package com.promedia.sentepos.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple application logger and payload dumper.
 *
 * Root folders (relative to working dir):
 *   Logs/
 *     <category>-YYYY-MM-DD.log
 *     YYYY-MM-DD/<category>/<ref>-<stage>-<ts>.json
 *     Payloads/YYYY-MM-DD/<ref>-<stage>-<ts>.json
 */// ...package + imports stay the same...

public final class AppLog {
    private AppLog(){}

    // Root = working dir / Logs
    private static final Path ROOT = Paths.get(System.getProperty("user.dir"), "Logs");
    private static final Path PAYLOADS_ROOT = ROOT.resolve("Payloads");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TS  = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    /* ================= NEW: startup init used by SentePOS.java ================= */
    /** Ensure Logs/ and Logs/Payloads/ exist; log where we’re writing. */
    public static synchronized void init() {
        try {
            ensureRoot();
            Files.createDirectories(PAYLOADS_ROOT);
            line("app", "AppLog initialized. ROOT=" + ROOT.toAbsolutePath());
        } catch (Exception ignored) {
            // last resort, avoid crashing the app on logging init failure
        }
    }

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

    /** Persist a full payload into Logs/YYYY-MM-DD/<category>/<ref>-<stage>-<ts>.json */
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
            Files.write(out, masked.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            return out;
        } catch (IOException e) {
            line(category, "blob write failed for ref=" + ref + " stage=" + stage + " : " + e.getMessage());
            return null;
        }
    }

    /** Make sure Logs/Payloads exists. Safe to call many times. */
    public static synchronized void ensurePayloadsFolder() {
        try {
            ensureRoot();
            Files.createDirectories(PAYLOADS_ROOT);
        } catch (IOException ignored) {}
    }

    /** Write to Logs/Payloads/YYYY-MM-DD/<ref>-<stage>-<ts>.json */
    public static synchronized Path blobInPayloads(String ref, String stage, String content) {
        try {
            ensurePayloadsFolder();
            String day = LocalDate.now().format(DAY);
            Path dayDir = PAYLOADS_ROOT.resolve(day);
            Files.createDirectories(dayDir);

            String safeRef = safe(ref);
            String ts = LocalDateTime.now().format(TS);
            Path out = dayDir.resolve(safeRef + "-" + safe(stage) + "-" + ts + ".json");

            String masked = maskSecrets(content == null ? "" : content);
            Files.write(out, masked.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            return out;
        } catch (IOException e) {
            line("payloads", "blobInPayloads failed for ref=" + ref + " stage=" + stage + " : " + e.getMessage());
            return null;
        }
    }
    
     /** Convenience for success/failure line logging. */
    public static void ok(String category, String ref, String msg) {
        line(category, "[OK] " + ref + " - " + msg);
    }

    public static void err(String category, String ref, String msg) {
        line(category, "[ERR] " + ref + " - " + msg);
    }

    // ---- internals (unchanged) ----
    private static void ensureRoot() throws IOException { Files.createDirectories(ROOT); }
    private static String safe(String s){ if (s == null) return "null"; return s.replaceAll("[^a-zA-Z0-9._-]", "_"); }
    private static String maskSecrets(String text){
        String t = text;
        t = t.replaceAll("(?i)(\"password\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
        t = t.replaceAll("(?i)(\"pwd\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
        t = t.replaceAll("(?i)(\"token\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
        t = t.replaceAll("(?i)(\"secret\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3");
        t = t.replaceAll("(?i)(password=)([^&\\s]+)", "$1***");
        t = t.replaceAll("(?i)(token=)([^&\\s]+)", "$1***");
        return t;
    }
}