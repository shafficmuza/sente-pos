package com.promedia.sentepos.efris;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class EfrisClient {

    public static class Result {
        public boolean ok;
        public String invoiceNumber; // IRN/invoice id
        public String qrBase64;      // base64 image
        public String rawResponse;   // JSON text
        public String error;
    }

    /**
     * Send invoice JSON to EFRIS.
     * TODO: Replace this stub with real HTTP POST + auth + signature per URA spec.
     */
    public Result sendInvoiceJson(String json, String endpointUrl, String username, String password, String deviceNo) {
        Result r = new Result();
        try {
            // TODO: build HTTP request with headers/signature as required by EFRIS
            // For now, simulate success:
            r.ok = true;
            r.invoiceNumber = "IRN-" + System.currentTimeMillis();
            // Simulate a QR by encoding the invoiceNumber (replace with server-provided QR)
            String qrText = "IRN:" + r.invoiceNumber;
            r.qrBase64 = Base64.getEncoder().encodeToString(qrText.getBytes(StandardCharsets.UTF_8));
            r.rawResponse = "{\"ok\":true,\"invoiceNumber\":\""+r.invoiceNumber+"\"}";
            return r;
        } catch (Exception e) {
            r.ok = false;
            r.error = e.getMessage();
            r.rawResponse = "{\"ok\":false,\"error\":\""+safe(e.getMessage())+"\"}";
            return r;
        }
    }

    private static String safe(String s){ return s==null? "" : s.replace("\"","'"); }
}