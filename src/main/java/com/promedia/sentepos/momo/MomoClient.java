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
import java.util.zip.GZIPInputStream;

public final class MomoClient {

    // ================= CONFIG (sandbox defaults) =================
    // Base host (NO /collection/v1_0 here)
    private static final String MOMO_HOST = "https://sandbox.momodeveloper.mtn.com";

    // Product base path
    private static final String COLLECTION_BASE_URL = MOMO_HOST + "/collection/v1_0";

    // Subscription key (Primary)
    private static final String SUBSCRIPTION_KEY = "9dc12346168343468b0ef45536ba2953";

    // API user + key (yours that worked)
    private static final String API_USER_ID = "b4a4c5a8-0d57-4b18-b056-5e6f1c3da7c9";
    private static final String API_KEY     = "39c0b5ea4916450d84f715c95ddc27b6";

    // sandbox / production
    private static final String TARGET_ENVIRONMENT = "sandbox";

    // If you create API user via code, set your public callback here
    private static final String PROVIDER_CALLBACK_URL = "https://prosystemsug.com/sentepos/momo_callback.php";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String collectionBaseUrl;
    private final String subscriptionKey;
    private final String apiUserId;
    private final String apiKey;
    private final String targetEnvironment;

    public MomoClient(String collectionBaseUrl,
                      String subscriptionKey,
                      String apiUserId,
                      String apiKey,
                      String targetEnvironment) {
        this.collectionBaseUrl = removeTrailingSlash(collectionBaseUrl);
        this.subscriptionKey = subscriptionKey;
        this.apiUserId = apiUserId;
        this.apiKey = apiKey;
        this.targetEnvironment = (targetEnvironment == null || targetEnvironment.isBlank())
                ? "sandbox" : targetEnvironment;
    }

    private static String removeTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // ============================================================
    // 0) CREATE API USER (optional, only when provisioning)
    // POST {host}/v1_0/apiuser  with X-Reference-Id = new UUID
    // ============================================================
    public String createApiUser(String callbackUrl) throws IOException {
        String url = MOMO_HOST + "/v1_0/apiuser";
        String newUserId = UUID.randomUUID().toString();

        HttpURLConnection conn = open(url, "POST");
        conn.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey);
        conn.setRequestProperty("X-Reference-Id", newUserId);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");

        conn.setDoOutput(true);

        String body = "{"
                + "\"providerCallbackHost\":\"" + escape(callbackUrl) + "\""
                + "}";

        writeBody(conn, body);

        int rc = conn.getResponseCode();
        String resp = readBody(conn);

        // Expected: 201 Created, empty body
        if (rc != 201 && rc != 202) {
            throw new IOException("CreateApiUser failed: HTTP " + rc + " -> " + resp);
        }
        return newUserId;
    }

    // ============================================================
    // 0b) CREATE API KEY for a given API USER
    // POST {host}/v1_0/apiuser/{apiUserId}/apikey
    // ============================================================
    public String createApiKey(String apiUserId) throws IOException {
        String url = MOMO_HOST + "/v1_0/apiuser/" + apiUserId + "/apikey";

        HttpURLConnection conn = open(url, "POST");
        conn.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        writeBody(conn, ""); // empty

        int rc = conn.getResponseCode();
        String resp = readBody(conn);

        if (rc < 200 || rc >= 300) {
            throw new IOException("CreateApiKey failed: HTTP " + rc + " -> " + resp);
        }

        JsonNode node = parseJson(resp);
        String key = node != null && node.hasNonNull("apiKey") ? node.get("apiKey").asText() : null;
        if (key == null || key.isBlank()) {
            throw new IOException("CreateApiKey: missing apiKey field. Body: " + resp);
        }
        return key;
    }

    // ============================================================
    // 1) ACCESS TOKEN
    // POST {host}/collection/token/
    // Authorization: Basic base64(apiUserId:apiKey)
    // ============================================================
    public String getAccessToken() throws IOException {
        String tokenUrl = MOMO_HOST + "/collection/token/";

        String basic = apiUserId + ":" + apiKey;
        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString(basic.getBytes(StandardCharsets.UTF_8));

        HttpURLConnection conn = open(tokenUrl, "POST");
        conn.setRequestProperty("Authorization", authHeader);
        conn.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");

        conn.setDoOutput(true);
        writeBody(conn, ""); // empty ok

        int rc = conn.getResponseCode();
        String contentType = conn.getHeaderField("Content-Type");
        String resp = readBody(conn);

        System.out.println("Token URL       : " + tokenUrl);
        System.out.println("HTTP " + rc + " Content-Type: " + contentType);
        System.out.println("Raw response    : " + resp);

        if (looksLikeHtmlBlocked(resp)) {
            throw new IOException("Request rejected by a firewall/proxy (HTML). Body: " + resp);
        }
        if (rc < 200 || rc >= 300) {
            throw new IOException("Token failed: HTTP " + rc + " -> " + resp);
        }

        JsonNode node = parseJson(resp);
        String token = node != null && node.hasNonNull("access_token") ? node.get("access_token").asText() : null;
        if (token == null || token.isBlank()) {
            throw new IOException("Token missing in response (access_token). Body: " + resp);
        }
        return token;
    }

    // ============================================================
    // 2) REQUEST TO PAY
    // POST /collection/v1_0/requesttopay   -> 202 Accepted
    // X-Reference-Id = our referenceId
    // ============================================================
    public String requestToPay(String accessToken,
                               String amount,
                               String currency,
                               String msisdn,
                               String externalId,
                               String payerMessage,
                               String payeeNote) throws IOException {

        String url = collectionBaseUrl + "/requesttopay";
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
                + "\"amount\":\"" + escape(amount) + "\","
                + "\"currency\":\"" + escape(currency) + "\","
                + "\"externalId\":\"" + escape(externalId) + "\","
                + "\"payer\":{"
                +   "\"partyIdType\":\"MSISDN\","
                +   "\"partyId\":\"" + escape(msisdn) + "\""
                + "},"
                + "\"payerMessage\":\"" + escape(payerMessage) + "\","
                + "\"payeeNote\":\"" + escape(payeeNote) + "\""
                + "}";

        writeBody(conn, json);

        int rc = conn.getResponseCode();
        String respBody = readBody(conn);

        System.out.println("RequestToPay URL: " + url);
        System.out.println("X-Reference-Id  : " + referenceId);
        System.out.println("HTTP " + rc + " -> " + respBody);

        // Expected: 202, body often empty
        if (rc != 202) {
            throw new IOException("RequestToPay failed: HTTP " + rc + " -> " + respBody);
        }
        return referenceId;
    }

    // ============================================================
    // 3) GET TRANSACTION STATUS
    // GET /collection/v1_0/requesttopay/{referenceId}
    // ============================================================
    public JsonNode getTransactionStatus(String accessToken, String referenceId) throws IOException {
        String url = collectionBaseUrl + "/requesttopay/" + referenceId;

        HttpURLConnection conn = open(url, "GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey);
        conn.setRequestProperty("X-Target-Environment", targetEnvironment);
        conn.setRequestProperty("Accept", "application/json");

        int rc = conn.getResponseCode();
        String body = readBody(conn);

        System.out.println("Status URL   : " + url);
        System.out.println("HTTP " + rc + " -> " + body);

        if (looksLikeHtmlBlocked(body)) {
            throw new IOException("Request rejected by firewall/proxy (HTML). Body: " + body);
        }
        if (rc < 200 || rc >= 300) {
            throw new IOException("Status failed: HTTP " + rc + " -> " + body);
        }
        return parseJson(body);
    }

    // ============================================================
    // DEMO MAIN
    // ============================================================
    public static void main(String[] args) {
        MomoClient client = new MomoClient(
                COLLECTION_BASE_URL,
                SUBSCRIPTION_KEY,
                API_USER_ID,
                API_KEY,
                TARGET_ENVIRONMENT
        );

        try {
            // 1) token
            String token = client.getAccessToken();
            System.out.println("Access token: " + token);

            // 2) request to pay (uncomment and use a real sandbox MSISDN format)
            // NOTE: MSISDN should be digits only, e.g. 2567XXXXXXXX
            /*
            String refId = client.requestToPay(
                    token,
                    "1000",
                    "UGX",
                    "2567XXXXXXX",
                    "SENTE_LICENSE_0001",
                    "Pay SentePOS License",
                    "SentePOS License"
            );
            System.out.println("RequestToPay submitted. ReferenceId=" + refId);

            // 3) poll status
            for (int i = 0; i < 20; i++) { // ~20 polls
                Thread.sleep(3000);
                JsonNode st = client.getTransactionStatus(token, refId);
                String status = st != null && st.hasNonNull("status") ? st.get("status").asText() : "";
                System.out.println("Status=" + status + " -> " + st);

                if ("SUCCESSFUL".equalsIgnoreCase(status)
                        || "FAILED".equalsIgnoreCase(status)
                        || "REJECTED".equalsIgnoreCase(status)) {
                    break;
                }
            }
            */
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= LOW-LEVEL HELPERS =================

    private static HttpURLConnection open(String urlStr, String method) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection https) {
            // SSL defaults ok
        }
        conn.setRequestMethod(method);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        conn.setRequestProperty("User-Agent", "SentePOS-MoMoClient/1.0");
        conn.setRequestProperty("Accept", "application/json");
        // IMPORTANT: avoid gzip “binary-looking” responses unless we decode
        conn.setRequestProperty("Accept-Encoding", "gzip");

        return conn;
    }

    private static void writeBody(HttpURLConnection conn, String body) throws IOException {
        if (body == null) body = "";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readBody(HttpURLConnection conn) throws IOException {
        int rc = conn.getResponseCode();
        InputStream is = (rc >= 200 && rc < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";

        // handle gzip
        String enc = conn.getHeaderField("Content-Encoding");
        if (enc != null && enc.toLowerCase(Locale.ROOT).contains("gzip")) {
            is = new GZIPInputStream(is);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static boolean looksLikeHtmlBlocked(String resp) {
        if (resp == null) return false;
        String t = resp.toLowerCase(Locale.ROOT);
        return t.contains("<html") && t.contains("request rejected");
    }

    private static JsonNode parseJson(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}