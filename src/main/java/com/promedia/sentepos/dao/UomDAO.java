package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;
import com.promedia.sentepos.model.Uom;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class UomDAO {

    private UomDAO() {}

    /** Make sure the table exists (SQLite syntax – adjust if needed). */
    public static void ensureTable() throws SQLException {
        final String sql = """
            CREATE TABLE IF NOT EXISTS efris_uom (
                code        TEXT PRIMARY KEY,
                name        TEXT,
                description TEXT
            )
            """;
        try (Connection c = Db.get(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /** Insert or update a single UOM row. */
    public static void upsert(Uom u) throws SQLException {
        ensureTable();

        final String sql = """
            INSERT INTO efris_uom(code, name, description)
            VALUES(?,?,?)
            ON CONFLICT(code) DO UPDATE SET
                name        = excluded.name,
                description = excluded.description
            """;

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, u.getCode());
            ps.setString(2, u.getName());
            ps.setString(3, u.getDescription());
            ps.executeUpdate();
        }
    }

    /** Bulk upsert; convenient when syncing from T115. */
    public static void upsertAll(List<Uom> list) throws SQLException {
        if (list == null || list.isEmpty()) return;
        ensureTable();
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            final String sql = """
                INSERT INTO efris_uom(code, name, description)
                VALUES(?,?,?)
                ON CONFLICT(code) DO UPDATE SET
                    name        = excluded.name,
                    description = excluded.description
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (Uom u : list) {
                    ps.setString(1, u.getCode());
                    ps.setString(2, u.getName());
                    ps.setString(3, u.getDescription());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        }
    }

    /** List all UOMs (for your combo-box on the product screen). */
    public static List<Uom> listAll() throws SQLException {
        ensureTable();
        final String sql = "SELECT code, name, description FROM efris_uom ORDER BY name";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Uom> out = new ArrayList<>();
            while (rs.next()) {
                Uom u = new Uom();
                u.setCode(rs.getString(1));
                u.setName(rs.getString(2));
                u.setDescription(rs.getString(3));
                out.add(u);
            }
            return out;
        }
    }

    /** Get a single UOM by code (e.g. "PCE"). */
    public static Uom findByCode(String code) throws SQLException {
        ensureTable();
        final String sql = "SELECT code, name, description FROM efris_uom WHERE code=?";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Uom u = new Uom();
                u.setCode(rs.getString(1));
                u.setName(rs.getString(2));
                u.setDescription(rs.getString(3));
                return u;
            }
        }
    }
}