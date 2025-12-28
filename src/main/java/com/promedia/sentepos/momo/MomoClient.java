package com.promedia.sentepos.momo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

public final class MomoClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;              // https://sandbox.momodeveloper.mtn.com/collection/v1_0
    private final String subscriptionKey;      // PRIMARY key
    private final String apiUserId;            // UUID
    private final String apiKey;               // API key generated for apiUserId
    private final String targetEnvironment;    // sandbox | production

    public MomoClient(String baseUrl, String subscriptionKey, String apiUserId, String apiKey, String targetEnvironment) {
        this.baseUrl = removeTrailingSlash(baseUrl);
        this.subscriptionKey = subscriptionKey;
        this.apiUserId = apiUserId;
        this.apiKey = apiKey;
        this.targetEnvironment = targetEnvironment != null ? targetEnvironment : "sandbox";
    }

    private static String removeTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** https://sandbox.momodeveloper.mtn.com/collection/token/ */
    public String getAccessToken() throws IOException {
        String tokenUrl = baseUrl.replace("/collection/v1_0", "") + "/collection/token/";

        String basic = apiUserId + ":" + apiKey;
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8));

        HttpURLConnection conn = open(tokenUrl, "POST");
        conn.setRequestProperty("Authorization", authHeader);
        conn.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(new byte[0]);
        }

        int rc = conn.getResponseCode();
        String resp = readBody(conn);

        if (resp != null && resp.toLowerCase(Locale.ROOT).contains("<html")
                && resp.toLowerCase(Locale.ROOT).contains("request rejected")) {
            throw new IOException("Request rejected by proxy/WAF (HTML response). Body: " + resp);
        }
        if (rc < 200 || rc >= 300) throw new IOException("Token failed: HTTP " + rc + " -> " + resp);

        JsonNode node = MAPPER.readTree(resp);
        String token = node.hasNonNull("access_token") ? node.get("access_token").asText("") : "";
        if (token.isBlank()) throw new IOException("Token missing 'access_token'. Body: " + resp);
        return token;
    }

    /** POST /collection/v1_0/requesttopay -> 202 */
    public String requestToPay(String accessToken,
                               String amount,
                               String currency,
                               String msisdn,
                               String externalId,
                               String payerMessage,
                               String payeeNote) throws IOException {

        String url = baseUrl + "/requesttopay";
        String referenceId = UUID.randomUUID().toString();

        HttpURLConnection conn = open(url, "POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey);
        conn.setRequestProperty("X-Target-Environment", targetEnvironment);
        conn.setRequestProperty("X-Reference-Id", referenceId);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        String json = "{"
                + "\"amount\":\"" + esc(amount) + "\","
                + "\"currency\":\"" + esc(currency) + "\","
                + "\"externalId\":\"" + esc(externalId) + "\","
                + "\"payer\":{\"partyIdType\":\"MSISDN\",\"partyId\":\"" + esc(msisdn) + "\"},"
                + "\"payerMessage\":\"" + esc(payerMessage) + "\","
                + "\"payeeNote\":\"" + esc(payeeNote) + "\""
                + "}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int rc = conn.getResponseCode();
        String body = readBody(conn);

        if (rc != 202) throw new IOException("RequestToPay failed: HTTP " + rc + " -> " + body);
        return referenceId;
    }

    /** GET /collection/v1_0/requesttopay/{referenceId} -> JSON */
    public JsonNode getTransactionStatus(String accessToken, String referenceId) throws IOException {
        String url = baseUrl + "/requesttopay/" + referenceId;
        HttpURLConnection conn = open(url, "GET");

        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey);
        conn.setRequestProperty("X-Target-Environment", targetEnvironment);
        conn.setRequestProperty("Accept", "application/json");

        int rc = conn.getResponseCode();
        String body = readBody(conn);
        if (rc < 200 || rc >= 300) throw new IOException("Status failed: HTTP " + rc + " -> " + body);

        return MAPPER.readTree(body);
    }

    // ---------- low level ----------
    private static HttpURLConnection open(String urlStr, String method) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection https) { /* no-op */ }
        conn.setRequestMethod(method);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "SentePOS-MoMoClient/1.0");
        return conn;
    }

    private static String readBody(HttpURLConnection conn) throws IOException {
        InputStream is = (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}