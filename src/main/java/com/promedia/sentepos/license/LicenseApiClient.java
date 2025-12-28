package com.promedia.sentepos.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class LicenseApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String base;

    public LicenseApiClient(String serverBase) {
        this.base = serverBase.endsWith("/") ? serverBase.substring(0, serverBase.length() - 1) : serverBase;
    }

    public static final class CreatePaymentResponse {
        public boolean ok;
        public String error;
        public String referenceId;
        public String momoBody; // when momo_request_failed
        public Integer http;    // momo http code
    }

    public static final class StatusResponse {
        public boolean ok;
        public String error;
        public String status;       // PENDING / SUCCESSFUL / FAILED / REJECTED
        public String licenseToken; // when SUCCESSFUL
        public long expiresAt;      // epoch seconds
    }

    /** JSON POST /license_create_payment.php */
    public CreatePaymentResponse createPayment(String deviceId, String machineId, String msisdn, String plan, String externalId) throws IOException {
        String url = base + "/license_create_payment.php";

        String json = "{"
                + "\"deviceId\":\"" + esc(deviceId) + "\","
                + "\"machineId\":\"" + esc(machineId) + "\","
                + "\"msisdn\":\"" + esc(msisdn) + "\","
                + "\"plan\":\"" + esc(plan) + "\","
                + "\"externalId\":\"" + esc(externalId) + "\""
                + "}";

        String resp = postJson(url, json);
        JsonNode root = parse(resp);

        CreatePaymentResponse r = new CreatePaymentResponse();
        r.ok = root.path("ok").asBoolean(false);
        r.error = text(root, "error");
        r.referenceId = text(root, "referenceId");
        r.momoBody = text(root, "momoBody");
        if (root.hasNonNull("http")) r.http = root.get("http").asInt();

        return r;
    }

    /** JSON POST /license_check_status.php */
    public StatusResponse checkStatus(String referenceId, String deviceId) throws IOException {
        String url = base + "/license_check_status.php";

        String json = "{"
                + "\"referenceId\":\"" + esc(referenceId) + "\","
                + "\"deviceId\":\"" + esc(deviceId) + "\""
                + "}";

        String resp = postJson(url, json);
        JsonNode root = parse(resp);

        StatusResponse r = new StatusResponse();
        r.ok = root.path("ok").asBoolean(false);
        r.error = text(root, "error");
        r.status = text(root, "status");
        r.licenseToken = text(root, "licenseToken");
        r.expiresAt = root.path("expiresAt").asLong(0);

        return r;
    }

    // ---------------- low-level HTTP ----------------

    private static String postJson(String urlStr, String json) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int rc = conn.getResponseCode();
        String body = readBody(conn);
        if (rc < 200 || rc >= 300) throw new IOException("HTTP " + rc + " -> " + body);
        return body;
    }

    private static String readBody(HttpURLConnection conn) throws IOException {
        InputStream is = (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static JsonNode parse(String body) throws IOException {
        try {
            return MAPPER.readTree(body == null ? "" : body);
        } catch (Exception e) {
            throw new IOException("Bad JSON from server: " + body);
        }
    }

    private static String text(JsonNode n, String k) {
        if (n == null) return null;
        JsonNode v = n.get(k);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}