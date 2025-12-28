package com.promedia.sentepos.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class LicenseManager {

    private LicenseManager() {}

    public static final String SERVER_BASE = "https://sentepos.com/sentepos";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SECRET_KEY = "SENTE-POS-LICENSE-2025"; // local integrity only
    private static final Path DATA_DIR = Paths.get(System.getProperty("user.home"), ".sentepos");
    private static final Path DEVICE_FILE = DATA_DIR.resolve("device.id");
    private static final Path LICENSE_FILE = DATA_DIR.resolve("license.dat");

    // ---- Device Id ----
    public static String getOrCreateDeviceId() {
        try {
            Files.createDirectories(DATA_DIR);
            if (Files.exists(DEVICE_FILE)) {
                String id = Files.readString(DEVICE_FILE, StandardCharsets.UTF_8).trim();
                if (!id.isBlank()) return id;
            }
            String id = UUID.randomUUID().toString();
            Files.writeString(DEVICE_FILE, id, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return id;
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    public static String getMachineId() {
        String raw = System.getProperty("os.name") + "|" +
                System.getProperty("os.arch") + "|" +
                System.getProperty("user.name") + "|" +
                getOrCreateDeviceId();
        return sha256(raw);
    }

    // ---- License Check ----
    public static boolean isLicensed() {            // <-- for SentePOS.java compatibility
        return isLicensedNow();
    }

    public static boolean isLicensedNow() {
        try {
            if (!Files.exists(LICENSE_FILE)) return false;

            String content = Files.readString(LICENSE_FILE, StandardCharsets.UTF_8);
            JsonNode root = MAPPER.readTree(content);

            String token = text(root, "licenseToken");
            long expiresAt = root.path("expiresAt").asLong(0);
            String savedHash = text(root, "hash");

            if (token == null || token.isBlank()) return false;
            if (expiresAt <= 0) return false;

            String calcHash = sha256(token + ";" + expiresAt + ";" + SECRET_KEY);
            if (!calcHash.equals(savedHash)) return false;

            long now = Instant.now().getEpochSecond();
            return now < expiresAt;
        } catch (Exception e) {
            return false;
        }
    }

    public static void saveLicenseToken(String licenseToken, long expiresAtEpochSeconds) throws IOException {
        Files.createDirectories(DATA_DIR);
        String hash = sha256(licenseToken + ";" + expiresAtEpochSeconds + ";" + SECRET_KEY);

        String json = "{\n" +
                "  \"licenseToken\": \"" + esc(licenseToken) + "\",\n" +
                "  \"expiresAt\": " + expiresAtEpochSeconds + ",\n" +
                "  \"hash\": \"" + hash + "\"\n" +
                "}\n";

        Files.writeString(LICENSE_FILE, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    // ---- Main Flow ----
   // in LicenseManager.java

public static String payAndActivate(String plan, String msisdn) throws Exception {
    final String deviceId = getOrCreateDeviceId();
    final String machineId = getMachineId();

    String p = (plan == null ? "MONTHLY" : plan.trim().toUpperCase(Locale.ROOT));
    if (!p.equals("MONTHLY") && !p.equals("YEARLY")) p = "MONTHLY";

    String phone = (msisdn == null) ? "" : msisdn.replaceAll("\\D+", "");
    if (phone.isBlank()) throw new IllegalArgumentException("Phone/MSISDN is required.");

    String externalId = "SENTE_" + p + "_" + System.currentTimeMillis();

    LicenseApiClient api = new LicenseApiClient(SERVER_BASE);

    // ✅ FIX: call with 5 args (matches LicenseApiClient)
    LicenseApiClient.CreatePaymentResponse created =
            api.createPayment(deviceId, machineId, phone, p, externalId);

    if (!created.ok) {
        String extra = (created.momoBody != null && !created.momoBody.isBlank())
                ? (" | momoBody: " + created.momoBody)
                : "";
        throw new IOException("License issue failed: " + created.error + extra);
    }
    if (created.referenceId == null || created.referenceId.isBlank()) {
        throw new IOException("Server did not return referenceId.");
    }

    // poll server for status
    for (int i = 0; i < 60; i++) {
        Thread.sleep(3000);

        LicenseApiClient.StatusResponse st = api.checkStatus(created.referenceId, deviceId);
        String status = (st.status == null) ? "" : st.status.trim();

        if ("SUCCESSFUL".equalsIgnoreCase(status)) {
            if (st.licenseToken == null || st.licenseToken.isBlank())
                throw new IOException("SUCCESSFUL but server did not return licenseToken.");
            if (st.expiresAt <= 0)
                throw new IOException("SUCCESSFUL but server did not return expiresAt.");

            saveLicenseToken(st.licenseToken, st.expiresAt);
            return st.licenseToken;
        }

        if ("FAILED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
            throw new IOException("Payment " + status + ". Try again.");
        }
    }

    throw new IOException("Timed out waiting for payment confirmation.");
}
    // ---- Helpers ----
    private static String text(JsonNode n, String name) {
        if (n == null) return null;
        JsonNode v = n.get(name);
        return (v != null && !v.isNull()) ? v.asText(null) : null;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}