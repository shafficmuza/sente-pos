package com.promedia.sentepos.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AppLog {
    private AppLog(){}

    // Root = working dir / Logs
    private static final Path ROOT = Paths.get(System.getProperty("user.dir"), "Logs");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TS  = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    /** Append a one-line event to Logs/<category>-YYYY-MM-DD.log (creates dirs as needed). */
    public static synchronized void line(String category, String message) {
        try {
            ensureRoot();
            String day = LocalDate.now().format(DAY);
            Path file = ROOT.resolve(category + "-" + day + ".log");
            String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String line = String.format("%s  [%s]  %s%n", ts, category, message);
            Files.write(file, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    /**
     * Persist a full payload into Logs/YYYY-MM-DD/<category>/<ref>-<stage>-<ts>.json
     * (e.g., Logs/2025-11-01/efris/cn-42-request-20251101-044225-012.json)
     */
    public static synchronized Path blob(String category, String ref, String stage, String content) {
        try {
            ensureRoot();
            String day = LocalDate.now().format(DAY);
            Path dayDir = ROOT.resolve(day).resolve(category);
            Files.createDirectories(dayDir);

            String safeRef = safe(ref);
            String ts = LocalDateTime.now().format(TS);
            Path out = dayDir.resolve(safeRef + "-" + safe(stage) + "-" + ts + ".json");

            String masked = maskSecrets(content == null ? "" : content);
            Files.write(out, masked.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW);
            return out;
        } catch (IOException e) {
            line(category, "blob write failed for ref=" + ref + " stage=" + stage + " : " + e.getMessage());
            return null;
        }
    }

    /** Convenience for success/failure lines. */
    public static void ok(String category, String ref, String msg){
        line(category, "[OK] " + ref + " - " + msg);
    }
    public static void err(String category, String ref, String msg){
        line(category, "[ERR] " + ref + " - " + msg);
    }

    private static void ensureRoot() throws IOException {
        Files.createDirectories(ROOT);
    }
    private static String safe(String s){
        if (s == null) return "null";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** Very light redaction for common secret fields inside JSON or query strings. */
    private static String maskSecrets(String text){
        String t = text;
        // "password":"...","pwd":"...","token":"...","secret":"..."
        t = t.replaceAll("(?i)(\"password\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
        t = t.replaceAll("(?i)(\"pwd\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
        t = t.replaceAll("(?i)(\"token\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
        t = t.replaceAll("(?i)(\"secret\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
        // key=value forms: password=..., token=...
        t = t.replaceAll("(?i)(password=)([^&\\s]+)", "$1***");
        t = t.replaceAll("(?i)(token=)([^&\\s]+)", "$1***");
        return t;
    }
}