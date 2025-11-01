package com.promedia.sentepos.service;

import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.dao.CreditNoteDAO;
import com.promedia.sentepos.dao.CreditNoteDAO.Item;
import com.promedia.sentepos.dao.StockDAO;
import com.promedia.sentepos.efris.EfrisClient;
import com.promedia.sentepos.efris.EfrisPayloadBuilder;
import com.promedia.sentepos.model.Business;

import java.sql.SQLException;
import java.util.List;

public final class CreditNoteService {
    private CreditNoteService(){}

    private static final String CN_ENDPOINT    = "https://efris.example/submitCreditNote";   // TODO: real endpoint
    private static final String CN_CANCEL_EP   = "https://efris.example/cancelCreditNote";   // TODO: real endpoint

    /** Build + save a credit note for selected items (partial or full), adjust stock (+qty back). */
    public static long issueCreditNote(long saleId, List<Item> items, String reason, String note) throws SQLException {
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
        CreditNoteDAO.setStatus(cnId, "PENDING");
        return cnId;
    }

    /** Send the credit note to EFRIS and mark status. Returns invoice number (if any). */
    public static String fiscaliseCreditNote(long creditNoteId) throws Exception {
        var head = CreditNoteDAO.findHead(creditNoteId);
        if (head == null) throw new IllegalStateException("Credit note not found.");

        var items = CreditNoteDAO.listItems(creditNoteId);
        Business b = BusinessDAO.loadSingle();
        if (b == null) throw new IllegalStateException("Business not configured.");

        // Build payload (you provide implementation in EfrisPayloadBuilder)
        String payload = EfrisPayloadBuilder.buildCreditNotePayload(creditNoteId, head, items);

        String endpoint = CN_ENDPOINT;
        String user   = b.efrisUsername;   // ✅ camelCase
        String pass   = b.efrisPassword;   // ✅ camelCase
        String device = b.efrisDeviceNo;   // ✅ camelCase

        // Reuse invoice sender for CN to avoid missing overloads
        EfrisClient client = new EfrisClient();
        EfrisClient.Result r = client.sendInvoiceJson(payload, endpoint, user, pass, device);

        if (r.ok) {
            CreditNoteDAO.setStatus(creditNoteId, "SENT");
            // If you later want to persist response/qr/verification: add methods in EfrisDAO and call them here.
            return r.invoiceNumber; // may be null depending on EFRIS response
        } else {
            CreditNoteDAO.setStatus(creditNoteId, "FAILED");
            throw new RuntimeException("Credit note fiscalisation failed: " + r.error);
        }
    }

    /** Cancel/void a credit note on EFRIS (and locally). */
    public static void cancelCreditNote(long creditNoteId, String reason) throws Exception {
        var head = CreditNoteDAO.findHead(creditNoteId);
        if (head == null) throw new IllegalStateException("Credit note not found.");
        if (!"SENT".equalsIgnoreCase(head.status) && !"PENDING".equalsIgnoreCase(head.status)) {
            throw new IllegalStateException("Only PENDING/SENT credit notes can be cancelled.");
        }

        Business b = BusinessDAO.loadSingle();
        if (b == null) throw new IllegalStateException("Business not configured.");

        String payload = EfrisPayloadBuilder.buildCreditNoteCancelPayload(creditNoteId, head, reason);

        String endpoint = CN_CANCEL_EP;
        String user   = b.efrisUsername;   // ✅
        String pass   = b.efrisPassword;   // ✅
        String device = b.efrisDeviceNo;   // ✅

        EfrisClient client = new EfrisClient();
        // Reuse same sender to avoid missing overloads; change to sendCreditNoteCancelJson if you implement it
        EfrisClient.Result r = client.sendInvoiceJson(payload, endpoint, user, pass, device);

        if (r.ok) {
            CreditNoteDAO.setStatus(creditNoteId, "CANCELLED");
            // If you reversed stock on issue and business rules require undoing that on cancel,
            // uncomment the following:
            // var items = CreditNoteDAO.listItems(creditNoteId);
            // for (var it : items) StockDAO.adjust(it.product_id, -it.qty, "CN Cancel #" + creditNoteId);
        } else {
            throw new RuntimeException("Cancel failed: " + r.error);
        }
    }
}