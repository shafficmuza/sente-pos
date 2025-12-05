package com.promedia.sentepos.license;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class LicenseManager {

    private static final String SECRET_KEY = "PROMEDIA-SYSTEMS-KEY-2025";

    // Hidden file location
    private static final String TRIAL_FILE =
            System.getProperty("os.name").toLowerCase().contains("win")
                    ? "C:\\\\ProgramData\\\\.pms_trial.dat"
                    : "/usr/local/share/.pms_trial.dat";

    // Default trial length in minutes for FIRST install
    private static final int DEFAULT_TRIAL_MINUTES = 720;   // change as needed

    // How many minutes of backward jump we tolerate before crying tamper
    private static final int BACKWARD_TOLERANCE_MIN = 2;

    // ***** ADMIN RESET KEYS (Minutes) *****
    public static final String RESET_6_HR_KEY   = "ADMIN-RESET-6H";    // 360 min
    public static final String RESET_10_MIN_KEY = "ADMIN-RESET-10M";
    public static final String RESET_30_MIN_KEY = "ADMIN-RESET-30M";
    public static final String RESET_60_MIN_KEY = "ADMIN-RESET-60M";

    // ***** USER FULL ACTIVATION KEY *****
    public static final String ACTIVATION_KEY = "PROMEDIA-2025-ACTIVE";

    /**
     * File format (semicolon separated):
     * startedOn;durationMinutes;lastRun;hash;activated
     *
     * hash = SHA256(startedOn + ";" + durationMinutes + ";" + lastRun + SECRET_KEY)
     */

    // Create hidden file automatically on first run
    public static void initializeTrial() {
        try {
            File file = new File(TRIAL_FILE);

            if (!file.exists()) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startedOn = now;
                LocalDateTime lastRun = now;
                int durationMinutes = DEFAULT_TRIAL_MINUTES;

                String hash = sha256(startedOn.toString() + ";" + durationMinutes + ";" + lastRun.toString() + SECRET_KEY);
                String content = startedOn.toString() + ";" +
                                 durationMinutes + ";" +
                                 lastRun.toString() + ";" +
                                 hash + ";" +
                                 "0";  // activated = 0
                writeFile(content);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Check if trial is active (and also detect time tampering)
    public static boolean isTrialActive() {
        try {
            File file = new File(TRIAL_FILE);
            if (!file.exists()) return false;

            String[] parts = readFile().split(";");
            if (parts.length < 5) return false; // invalid / old file

            String startedOnStr   = parts[0];
            int durationMinutes   = Integer.parseInt(parts[1]);
            String lastRunStr     = parts[2];
            String storedHash     = parts[3];
            int activated         = Integer.parseInt(parts[4]);

            LocalDateTime startedOn = LocalDateTime.parse(startedOnStr);
            LocalDateTime lastRun   = LocalDateTime.parse(lastRunStr);
            LocalDateTime now       = LocalDateTime.now();

            // FULL ACTIVATED
            if (activated == 1) return true;

            // Verify hash (integrity)
            String expectedHash = sha256(startedOnStr + ";" + durationMinutes + ";" + lastRunStr + SECRET_KEY);
            if (!storedHash.equals(expectedHash)) {
                // Tampering with file content
                return false;
            }

            // Detect clock rollback: if 'now' is significantly before lastRun
            long minutesBackward = ChronoUnit.MINUTES.between(now, lastRun); // positive if lastRun > now
            if (minutesBackward > BACKWARD_TOLERANCE_MIN) {
                // User moved the clock back too much -> treat as tampering
                return false;
            }

            // Calculate total minutes used since trial started
            long minutesUsed = ChronoUnit.MINUTES.between(startedOn, now);

            boolean stillValid = minutesUsed <= durationMinutes;

            // If still valid, update lastRun to now and re-hash (to move time forward)
            if (stillValid) {
                LocalDateTime newLastRun = now;
                String newHash = sha256(startedOn.toString() + ";" + durationMinutes + ";" + newLastRun.toString() + SECRET_KEY);
                String updatedContent = startedOn.toString() + ";" +
                                        durationMinutes + ";" +
                                        newLastRun.toString() + ";" +
                                        newHash + ";" +
                                        activated;
                writeFile(updatedContent);
            }

            return stillValid;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    

    // ***** APPLY ADMIN KEYS *****
    public static boolean applyAdminKey(String key) {
        try {
            if (key.equals(RESET_6_HR_KEY)) {
                resetTrialMinutes(360); // 6 hours
                return true;
            }
            if (key.equals(RESET_10_MIN_KEY)) {
                resetTrialMinutes(10);
                return true;
            }
            if (key.equals(RESET_30_MIN_KEY)) {
                resetTrialMinutes(30);
                return true;
            }
            if (key.equals(RESET_60_MIN_KEY)) {
                resetTrialMinutes(60);
                return true;
            }
            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Reset trial: new start time = now, fresh duration, lastRun = now
    private static void resetTrialMinutes(int minutes) throws Exception {
        LocalDateTime now           = LocalDateTime.now();
        LocalDateTime startedOn     = now;
        LocalDateTime lastRun       = now;
        int durationMinutes         = minutes;

        String hash = sha256(startedOn.toString() + ";" + durationMinutes + ";" + lastRun.toString() + SECRET_KEY);
        String content = startedOn.toString() + ";" +
                         durationMinutes + ";" +
                         lastRun.toString() + ";" +
                         hash + ";" +
                         "0"; // not activated
        writeFile(content);
    }

    // ***** NORMAL ACTIVATION *****
    public static boolean activateSoftware(String key) {
        if (!key.equals(ACTIVATION_KEY))
            return false;

        try {
            String[] parts = readFile().split(";");
            if (parts.length < 5) return false;

            String startedOnStr   = parts[0];
            String durationStr    = parts[1];
            String lastRunStr     = parts[2];
            String hash           = parts[3];

            String updated = startedOnStr + ";" +
                             durationStr + ";" +
                             lastRunStr + ";" +
                             hash + ";" +
                             "1";  // 1 = activated
            writeFile(updated);
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    // ***** UTILITY FUNCTIONS *****
    private static String sha256(String base) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(base.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static void writeFile(String content) throws IOException {
        Files.write(Paths.get(TRIAL_FILE), content.getBytes());
    }

    private static String readFile() throws IOException {
        return new String(Files.readAllBytes(Paths.get(TRIAL_FILE)));
    }
}