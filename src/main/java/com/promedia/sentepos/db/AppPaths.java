/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.promedia.sentepos.db;

/**
 *
 * @author shaffic
 */
import java.nio.file.*;

public final class AppPaths {
    private AppPaths() {}

    public static Path dataDir() {
        // Prefer LOCALAPPDATA on Windows, fallback to user home
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = (localAppData != null && !localAppData.isBlank())
                ? Paths.get(localAppData)
                : Paths.get(System.getProperty("user.home"));

        Path dir = base.resolve("SentePOS");
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create data directory: " + dir, e);
        }
        return dir;
    }

    public static Path dbPath() {
        return dataDir().resolve("app.db");
    }
}