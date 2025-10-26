package com.promedia.sentepos.db;

import java.sql.*;

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
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA synchronous = NORMAL");
                st.execute("PRAGMA foreign_keys = ON");
            } catch (SQLException ignore) {}
            initialized = true;
        }
        return c;
    }
}
