/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.promedia.sentepos.db;
import java.sql.*;
/**
 *
 * @author shaffic
 */
public final class Db {
    private static final String URL = "jdbc:sqlite:app.db";
    private static volatile boolean initialized = false;

    static {
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC not found. Add sqlite-jdbc to classpath.", e);
        }
    }

    private Db(){}

    public static Connection get() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        if (!initialized) {
            // Apply one-time PRAGMAs (best-effort)
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode = WAL");      // better concurrent reads + crash-safety
                st.execute("PRAGMA synchronous = NORMAL");     // good balance of safety/perf
                st.execute("PRAGMA foreign_keys = ON");        // enforce FK
            } catch (SQLException ignore) {}
            initialized = true;
        }
        return c;
    }
}
