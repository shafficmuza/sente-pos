package com.promedia.sentepos.service;

import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.dao.EfrisDAO;
import com.promedia.sentepos.efris.EfrisClient;
import com.promedia.sentepos.efris.EfrisPayloadBuilder;
import com.promedia.sentepos.model.Payment;
import com.promedia.sentepos.model.Sale;

public class FiscalService {

    private static final String DEFAULT_ENDPOINT =
            "http://127.0.0.1:9880/efristcs/ws/tcsapp/getInformation";

    /** Build payload → save PENDING → send → mark SENT/FAILED. Returns invoice number on success. */
    public static String fiscalise(long saleId, Sale sale, Payment payment) throws Exception {
        // 1) Build inner T109 (e-Invoice/e-Receipt) JSON
        String payload = EfrisPayloadBuilder.buildInvoicePayload(saleId, sale, payment);

        // 2) Save PENDING row
        EfrisDAO.upsertPending(saleId, payload);

        // 3) Load business config
        var b = BusinessDAO.loadSingle();
        if (b == null) {
            throw new IllegalStateException("Business not configured.");
        }

        String user   = b.efrisUsername;
        String pass   = b.efrisPassword;
        String device = b.efrisDeviceNo;
        String endpoint = DEFAULT_ENDPOINT;   // local TCS

        // 4) Send via offline enabler
        EfrisClient client = new EfrisClient();
        EfrisClient.Result r = client.sendInvoiceJson(payload, endpoint, user, pass, device);

        // 5) Persist result
        if (r.ok) {
            // Store full response + verification + QR
           EfrisDAO.markSent(
                    saleId,
                    r.rawResponse,          // full JSON
                    r.invoiceId,            // NEW
                    r.invoiceNumber,
                    r.qrBase64,
                    r.verificationCode
            );
            return r.invoiceNumber;
        } else {
            // FAILED with full response + error text
            EfrisDAO.markFailed(
                    saleId,
                    r.rawResponse,       // response_json
                    r.error              // error_message
            );
            throw new RuntimeException("Fiscalisation failed: " + r.error);
        }
    }
}