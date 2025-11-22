package com.promedia.sentepos.efris;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.model.Business;
import com.promedia.sentepos.util.AppLog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EfrisClient {

    /** Local TCS / Offline Enabler URL (NOT URA cloud). */
    public static final String ENABLER_URL =
            "http://127.0.0.1:9880/efristcs/ws/tcsapp/getInformation";

    // Default fallbacks if Business record is incomplete
    private static final String DEFAULT_DEVICE_MAC = "FFFFFFFFFFFF";
    private static final String DEFAULT_LONGITUDE  = "0.0";
    private static final String DEFAULT_LATITUDE   = "0.0";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String nowTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ---------------------------------------------------------------------
    // Result object – used by PosService & CreditNoteService already
    // ---------------------------------------------------------------------
    public static final class Result {
        public boolean ok;
        public String rawRequest;
        public String rawResponse;
        public String error;

        // EFRIS fields (filled if ok=true)
        public String invoiceNumber;     // invoiceNo / FDN
        public String verificationCode;  // antifakeCode / verification code
        public String qrBase64;          // QR code (base64 string from summary.qrCode)
    }

    // ---------------------------------------------------------------------
    // PUBLIC API – signatures unchanged
    // ---------------------------------------------------------------------

    /**
     * Fiscalise a SALE invoice via OFFLINE TCS.
     *
     * @param innerInvoiceJson The inner T109 JSON (built by EfrisPayloadBuilder)
     * @param endpoint         IGNORED – kept only for compatibility
     * @param username         IGNORED – offline enabler signs itself
     * @param password         IGNORED – offline enabler signs itself
     * @param deviceNo         optional override for deviceNo (usually from Business)
     */
    public Result sendInvoiceJson(String innerInvoiceJson,
                                  String endpoint,
                                  String username,
                                  String password,
                                  String deviceNo) throws Exception {
        // SALE invoice upload = T109
        return sendToEnabler(innerInvoiceJson, "T109", "SALE", deviceNo);
    }

    /**
     * Fiscalise a CREDIT NOTE via OFFLINE TCS.
     * We still use T109 – the inner JSON differentiates credit note vs invoice.
     */
    public Result sendCreditNoteJson(String innerCreditNoteJson,
                                     String endpoint,
                                     String username,
                                     String password,
                                     String deviceNo) throws Exception {
        // Credit note upload = T110
        return sendToEnabler(innerCreditNoteJson, "T110", "CREDIT_NOTE", deviceNo);
    }

    /**
     * Cancel/void a CREDIT NOTE (or invoice) via OFFLINE TCS.
     * You can adjust interfaceCode if your spec says otherwise (e.g. T110),
     * but we keep T109 for now – inner JSON carries the cancel instruction.
     */
    public Result sendCreditNoteCancelJson(String innerCancelJson,
                                           String endpoint,
                                           String username,
                                           String password,
                                           String deviceNo) throws Exception {
        // Credit note cancellation = T111
        return sendToEnabler(innerCancelJson, "T111", "CREDIT_NOTE_CANCEL", deviceNo);
    }

    // ---------------------------------------------------------------------
    // Core OFFLINE TCS logic – wraps inner JSON & talks to local Enabler
    // ---------------------------------------------------------------------
    private Result sendToEnabler(String innerJson,
                                 String interfaceCode,
                                 String refPrefix,
                                 String deviceNoOverride) throws Exception {

        // Load business configuration (TIN, BRN, device, etc.)
        Business b = null;
        try {
            b = BusinessDAO.loadSingle();
        } catch (Exception ignore) {
        }

        String tin       = b != null && b.tin != null          ? b.tin          : "";
        String brn       = b != null && b.branchCode != null   ? b.branchCode   : "";
        String userName  = b != null && b.efrisUsername != null? b.efrisUsername: "admin";
        String deviceNo  = (deviceNoOverride != null && !deviceNoOverride.isBlank())
                ? deviceNoOverride
                : (b != null && b.efrisDeviceNo != null ? b.efrisDeviceNo : "TCS-UNKNOWN");

        String deviceMac = DEFAULT_DEVICE_MAC;
        String longitude = DEFAULT_LONGITUDE;
        String latitude  = DEFAULT_LATITUDE;

        // ----------------- Build OFFLINE envelope (like EfrisInvoiceSender) -----------------
        String contentB64 = Base64.getEncoder()
                .encodeToString(innerJson.getBytes(StandardCharsets.UTF_8));

        ObjectNode data = MAPPER.createObjectNode();
        data.put("content", contentB64);
        data.put("signature", ""); // offline: TCS signs itself

        ObjectNode dataDesc = MAPPER.createObjectNode();
        dataDesc.put("codeType", "0");    // 0 = JSON/plain
        dataDesc.put("encryptCode", "0"); // 0 = no encryption
        dataDesc.put("zipCode", "0");     // 0 = no compression
        data.set("dataDescription", dataDesc);

        ObjectNode global = MAPPER.createObjectNode();
        global.put("appId", "AP04");  // system-to-system
        global.put("version", "1.1.20191201");
        global.put("dataExchangeId", UUID.randomUUID().toString().replaceAll("-", ""));
        global.put("interfaceCode", interfaceCode);
        global.put("requestCode", "TP");
        global.put("requestTime", nowTime());
        global.put("responseCode", "TA");
        global.put("userName", userName);
        global.put("deviceMAC", deviceMac);
        global.put("deviceNo", deviceNo);
        global.put("tin", tin);
        global.put("brn", brn);
        global.put("taxpayerID", "1"); // OFFLINE = 1
        global.put("longitude", longitude);
        global.put("latitude", latitude);
        global.put("agentType", "0");  // 0 = not acting as agent

        ObjectNode extendField = MAPPER.createObjectNode();
        extendField.put("responseDateFormat", "dd/MM/yyyy");
        extendField.put("responseTimeFormat", "dd/MM/yyyy HH:mm:ss");
        global.set("extendField", extendField);

        ObjectNode root = MAPPER.createObjectNode();
        root.set("data", data);
        root.set("globalInfo", global);
        root.set("returnStateInfo", MAPPER.createObjectNode());

        String requestJson = MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(root);

        String ref = refPrefix + "-" + System.currentTimeMillis();

        // Log request into Logs/Payloads
        AppLog.blobInPayloads(ref, "request", requestJson);

        // ----------------- HTTP POST to LOCAL TCS ONLY -----------------
        HttpResult http = postJsonWithStatus(ENABLER_URL, requestJson);
        String body = http.body != null ? http.body : "";

        // Log raw response
        AppLog.blobInPayloads(ref, "response", prettyOrRaw(body));

        // ----------------- Parse result -----------------
                // ----------------- Parse result -----------------
        Result r = new Result();
        r.rawRequest  = requestJson;
        r.rawResponse = body;

        try {
            JsonNode resp = MAPPER.readTree(body);
            JsonNode rsi  = resp.get("returnStateInfo");
            String returnCode = (rsi != null && rsi.hasNonNull("returnCode"))
                    ? rsi.get("returnCode").asText("")
                    : "";
            String returnMsg  = (rsi != null && rsi.hasNonNull("returnMessage"))
                    ? rsi.get("returnMessage").asText("")
                    : "";

            r.ok = "00".equals(returnCode);
            if (!r.ok) {
                r.error = returnCode + " - " + returnMsg;
                AppLog.err("efris", ref, "Offline TCS error: " + r.error);
                return r;
            }

            // ---------- decode inner response content ----------
            String innerRespJson = null;
            JsonNode dataNode = resp.get("data");
            if (dataNode != null && dataNode.hasNonNull("content")) {
                String respContentB64 = dataNode.get("content").asText("");
                if (!respContentB64.isEmpty()) {
                    innerRespJson = new String(
                            Base64.getDecoder().decode(respContentB64),
                            StandardCharsets.UTF_8
                    );
                    AppLog.blobInPayloads(ref, "inner-response", prettyOrRaw(innerRespJson));
                }
            }

            if (innerRespJson != null && !innerRespJson.isBlank()) {
                JsonNode inv = MAPPER.readTree(innerRespJson);
                JsonNode basic   = inv.get("basicInformation");
                JsonNode summary = inv.get("summary");

                if (basic != null) {
                    if (basic.hasNonNull("invoiceNo")) {
                        r.invoiceNumber = basic.get("invoiceNo").asText("");
                    }
                    // some responses use fdn
                    if ((r.invoiceNumber == null || r.invoiceNumber.isBlank())
                            && basic.hasNonNull("fdn")) {
                        r.invoiceNumber = basic.get("fdn").asText("");
                    }
                    if (basic.hasNonNull("antifakeCode")) {
                        r.verificationCode = basic.get("antifakeCode").asText("");
                    }
                }

                if (summary != null) {
                    // look for any reasonable QR field name
                    r.qrBase64 = firstNonEmpty(summary,
                            "qrCode", "QRCode", "qrCodeStr", "qrCodeBase64");
                }
            }

            AppLog.ok("efris", ref, "Offline TCS OK, invoiceNo=" + r.invoiceNumber);

        } catch (Exception ex) {
            r.ok = false;
            r.error = "Parse error: " + ex.getMessage();
            AppLog.err("efris", ref, "Parse failed: " + ex.getMessage());
        }

        return r;
    }

    // ---------------------------------------------------------------------
    // HTTP + helpers (same idea as your EfrisInvoiceSender)
    // ---------------------------------------------------------------------

    private static class HttpResult {
        final int status;
        final String body;
        final Map<String, List<String>> headers;

        HttpResult(int status, String body, Map<String, List<String>> headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }
    }

    private static HttpResult postJsonWithStatus(String urlStr, String json) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "sente-pos-offline/1.0");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int rc = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        (rc >= 200 && rc < 300) ? conn.getInputStream() : conn.getErrorStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }

        return new HttpResult(rc, sb.toString(), conn.getHeaderFields());
    }

    private static String prettyOrRaw(String maybeJson) {
        try {
            ObjectMapper m = new ObjectMapper();
            return m.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(m.readTree(maybeJson));
        } catch (Exception ignore) {
            return maybeJson;
        }
    }
    
        /** Return first non-empty child from the given JSON node. */
    private static String firstNonEmpty(JsonNode node, String... names) {
        if (node == null) return null;
        for (String n : names) {
            if (node.hasNonNull(n)) {
                String v = node.get(n).asText("");
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }
}