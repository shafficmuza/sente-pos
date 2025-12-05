package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class UnitOfMeasureDAO {
    private UnitOfMeasureDAO() {}

    public static final class Row {
        public long   id;
        public String code;    // EFRIS code (10, KG, PP, etc.)
        public String name;    // Friendly name ("Piece", "Kilogram", etc.)
        public int    active;
    }

    /** List all active units ordered by name. */
    public static List<Row> listActive() throws SQLException {
        final String sql =
            "SELECT id, code, name, active " +
            "FROM measure_units " +
            "WHERE active = 1 " +
            "ORDER BY name ASC";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Row> out = new ArrayList<>();
            while (rs.next()) {
                Row r = new Row();
                r.id     = rs.getLong(1);
                r.code   = rs.getString(2);
                r.name   = rs.getString(3);
                r.active = rs.getInt(4);
                out.add(r);
            }
            return out;
        }
    }

    /** Delete all UOM rows (used before re-sync from EFRIS). */
    public static void deleteAll() throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM measure_units")) {
            ps.executeUpdate();
        }
    }

    /** Bulk insert units (no upsert; we usually do deleteAll + insert). */
    public static void insertAll(List<Row> rows) throws SQLException {
        if (rows == null || rows.isEmpty()) return;
        final String sql =
            "INSERT INTO measure_units(code, name, active) " +
            "VALUES(?,?,1)";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            for (Row r : rows) {
                ps.setString(1, r.code);
                ps.setString(2, r.name);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}