package com.promedia.sentepos.license;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class JwtLite {
    private JwtLite(){}

    public static final class Claims {
        public final String sub;   // deviceId
        public final long exp;     // epoch seconds
        public final String plan;  // optional
        public Claims(String sub, long exp, String plan) {
            this.sub = sub; this.exp = exp; this.plan = plan;
        }
    }

    public static PublicKey readRsaPublicKey(String pem) throws Exception {
        String clean = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(clean);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    /** Verify RS256 JWT and return minimal claims. */
    public static Claims verifyRs256(String jwt, PublicKey publicKey) throws Exception {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid JWT parts");

        String headerB64 = parts[0];
        String payloadB64 = parts[1];
        String sigB64 = parts[2];

        byte[] signingInput = (headerB64 + "." + payloadB64).getBytes(StandardCharsets.UTF_8);
        byte[] signature = base64UrlDecode(sigB64);

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(signingInput);

        if (!verifier.verify(signature)) {
            throw new SecurityException("Invalid license signature");
        }

        String payloadJson = new String(base64UrlDecode(payloadB64), StandardCharsets.UTF_8);

        // Minimal parsing (no JSON libs): extract "sub", "exp", "plan"
        String sub = extractJsonString(payloadJson, "sub");
        long exp = extractJsonLong(payloadJson, "exp");
        String plan = extractJsonString(payloadJson, "plan");

        if (sub == null || sub.isBlank()) throw new IllegalArgumentException("Missing sub");
        if (exp <= 0) throw new IllegalArgumentException("Missing/invalid exp");

        return new Claims(sub, exp, plan);
    }

    private static byte[] base64UrlDecode(String v) {
        String s = v.replace('-', '+').replace('_', '/');
        switch (s.length() % 4) {
            case 2 -> s += "==";
            case 3 -> s += "=";
        }
        return Base64.getDecoder().decode(s);
    }

    private static String extractJsonString(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + k.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static long extractJsonLong(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return -1;
        int colon = json.indexOf(':', i + k.length());
        if (colon < 0) return -1;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)))) end++;
        if (end <= start) return -1;
        return Long.parseLong(json.substring(start, end));
    }
}