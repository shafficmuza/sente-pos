package com.promedia.sentepos.service;

import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.dao.CreditNoteDAO;
import com.promedia.sentepos.dao.CreditNoteDAO.Item;
import com.promedia.sentepos.dao.EfrisCreditNoteDAO;
import com.promedia.sentepos.dao.StockDAO;
import com.promedia.sentepos.efris.EfrisClient;
import com.promedia.sentepos.efris.EfrisPayloadBuilder;
import com.promedia.sentepos.model.Business;
import com.promedia.sentepos.util.AppLog;

import java.sql.SQLException;
import java.util.List;

public final class CreditNoteService {

    private CreditNoteService() {}

    // Local TCS Enabler (same as invoices)
    private static final String CN_ENDPOINT  =
            "http://127.0.0.1:9880/efristcs/ws/tcsapp/getInformation";
    private static final String CN_CANCEL_EP =
            "http://127.0.0.1:9880/efristcs/ws/tcsapp/getInformation";

    /**
     * Build + save a credit note for selected items (partial or full),
     * adjust stock (+qty back) AND immediately send to EFRIS.
     *
     * Returns the credit_note_id.
     */
    public static long issueCreditNote(long saleId,
                                       List<Item> items,
                                       String reason,
                                       String note) throws SQLException {

        double sub = items.stream().mapToDouble(i -> i.line_total).sum();
        double vat = items.stream().mapToDouble(i -> i.vat_amount).sum();
        double tot = sub + vat;

        long cnId = CreditNoteDAO.createHead(saleId, reason, sub, vat, tot, note);

        for (Item it : items) {
            it.credit_note_id = cnId;
            CreditNoteDAO.addItem(cnId, it);

            // stock comes back in
            StockDAO.adjust(it.product_id, +it.qty, "Return CN#" + cnId);
        }

        // Mark as PENDING locally (ready for fiscalisation)
        CreditNoteDAO.setStatus(cnId, "PENDING");

        // If you prefer to fiscalise later from a "Credit Notes" screen,
        // comment out this block and call fiscaliseCreditNote(cnId) from the UI instead.
        try {
            fiscaliseCreditNote(cnId);
        } catch (Exception ex) {
            // Keep CN in PENDING/FAILED; UI can show error and user can retry.
            AppLog.err("efris", "CN#" + cnId,
                    "Auto fiscalisation failed: " + ex.getMessage());
        }

        return cnId;
    }

    /**
     * Send the credit note to EFRIS and update its status.
     * Returns the EFRIS credit note number (for CN this is the referenceNo).
     */
    public static String fiscaliseCreditNote(long creditNoteId) throws Exception {
        var head = CreditNoteDAO.findHead(creditNoteId);
        if (head == null) {
            throw new IllegalStateException("Credit note not found.");
        }

        var items = CreditNoteDAO.listItems(creditNoteId);
        Business b = BusinessDAO.loadSingle();
        if (b == null) {
            throw new IllegalStateException("Business not configured.");
        }

        // Build inner payload for credit note (T110)
        String innerPayload =
                EfrisPayloadBuilder.buildCreditNotePayload(creditNoteId, head, items);

        String endpoint = CN_ENDPOINT;
        String user     = b.efrisUsername;
        String pass     = b.efrisPassword;
        String device   = b.efrisDeviceNo;

        String ref = "CN-" + creditNoteId;

        AppLog.line("efris", ref + " building payload");
        // Log inner CN payload into the same Payloads folder (like invoices)
        AppLog.blobInPayloads(ref, "inner-request", innerPayload);

        // Save/refresh local efris_credit_notes row as PENDING
        try {
            EfrisCreditNoteDAO.upsertPending(creditNoteId, innerPayload);
        } catch (Exception daoEx) {
            // Don't block fiscalisation on this; just log it
            AppLog.err("efris", ref,
                    "Failed to upsert PENDING CN record: " + daoEx.getMessage());
        }

        EfrisClient client = new EfrisClient();
        EfrisClient.Result r =
                client.sendCreditNoteJson(innerPayload, endpoint, user, pass, device);

        // Raw outer response already logged by EfrisClient; we add a short marker.
        AppLog.blobInPayloads(ref, "outer-response", r.rawResponse);

        if (r.ok) {
            // For CREDIT NOTES:
            //  - referenceNumber  = CN number returned by EFRIS
            //  - invoiceNumber    = only filled if/when URA returns invoiceNo for the CN
            String refNo = r.referenceNumber != null ? r.referenceNumber.trim() : "";
            String invNo = r.invoiceNumber   != null ? r.invoiceNumber.trim()   : "";

            // Proper log naming for CN
            AppLog.ok(
                "efris",
                ref,
                "CREDIT NOTE SENT: REF=" +
                    (refNo.isEmpty() ? "<none>" : refNo) +
                    (invNo.isEmpty() ? "" : (" | INV=" + invNo))
            );

            // Business credit_note status
            CreditNoteDAO.setStatus(creditNoteId, "SENT");

            // Persist EFRIS CN info (invoiceNo + referenceNo + QR + verification)
            try {
                EfrisCreditNoteDAO.markSent(
                        creditNoteId,
                        r.rawResponse,
                        invNo,          // invoice_number column
                        r.qrBase64,
                        r.verificationCode,
                        refNo           // reference_number column
                );
            } catch (Exception daoEx) {
                AppLog.err("efris", ref,
                        "Failed to update efris_credit_notes as SENT: " + daoEx.getMessage());
            }

            // Return the CN number (prefer referenceNo; fall back to invoiceNo only if present)
            return !refNo.isEmpty() ? refNo : invNo;
        } else {
            AppLog.err("efris", ref, "FAILED " + r.error);

            // Business credit_note status
            CreditNoteDAO.setStatus(creditNoteId, "FAILED");

            // Persist failed response in efris_credit_notes
            try {
                EfrisCreditNoteDAO.markFailed(
                        creditNoteId,
                        r.rawResponse,
                        r.error
                );
            } catch (Exception daoEx) {
                AppLog.err("efris", ref,
                        "Failed to update efris_credit_notes as FAILED: " + daoEx.getMessage());
            }

            throw new RuntimeException("Credit note fiscalisation failed: " + r.error);
        }
    }

    /**
     * Cancel/void a credit note on EFRIS (and locally).
     */
    public static void cancelCreditNote(long creditNoteId, String reason) throws Exception {
        var head = CreditNoteDAO.findHead(creditNoteId);
        if (head == null) {
            throw new IllegalStateException("Credit note not found.");
        }
        if (!"SENT".equalsIgnoreCase(head.status)
                && !"PENDING".equalsIgnoreCase(head.status)) {
            throw new IllegalStateException(
                    "Only PENDING/SENT credit notes can be cancelled.");
        }

        Business b = BusinessDAO.loadSingle();
        if (b == null) {
            throw new IllegalStateException("Business not configured.");
        }

        String innerPayload =
                EfrisPayloadBuilder.buildCreditNoteCancelPayload(creditNoteId, head, reason);

        String endpoint = CN_CANCEL_EP;
        String user     = b.efrisUsername;
        String pass     = b.efrisPassword;
        String device   = b.efrisDeviceNo;

        String ref = "CN-CANCEL-" + creditNoteId;
        AppLog.blobInPayloads(ref, "inner-request", innerPayload);

        EfrisClient client = new EfrisClient();
        // Use T111 for cancellation
        EfrisClient.Result r =
                client.sendCreditNoteCancelJson(innerPayload, endpoint, user, pass, device);

        AppLog.blobInPayloads(ref, "outer-response", r.rawResponse);

        if (r.ok) {
            CreditNoteDAO.setStatus(creditNoteId, "CANCELLED");
        } else {
            throw new RuntimeException("Cancel failed: " + r.error);
        }
    }
}