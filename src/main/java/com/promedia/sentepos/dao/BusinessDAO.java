package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;
import com.promedia.sentepos.model.Business;

import java.sql.*;
import java.time.Instant;

public class BusinessDAO {

    private static Business map(ResultSet rs) throws SQLException {
        Business b = new Business();
        b.id = rs.getLong("id");
        if (rs.wasNull()) b.id = null;
        b.name = rs.getString("name");
        b.tin = rs.getString("tin");
        b.branchCode = rs.getString("branch_code");
        b.addressLine = rs.getString("address_line");
        b.city = rs.getString("city");
        b.country = rs.getString("country");
        b.phone = rs.getString("phone");
        b.email = rs.getString("email");
        b.currency = rs.getString("currency");
        double vr = rs.getDouble("vat_rate");
        b.vatRate = rs.wasNull() ? null : vr;
        b.efrisDeviceNo = rs.getString("efris_device_no");
        b.efrisUsername = rs.getString("efris_username");
        b.efrisPassword = rs.getString("efris_password");
        b.efrisBranchId = rs.getString("efris_branch_id");
        return b;
    }

    /** Load the single business row (if any). */
    public static Business loadSingle() throws SQLException {
        String sql = "SELECT * FROM business ORDER BY id LIMIT 1";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return map(rs);
            return null;
        }
    }

    /** Insert new business row. Returns generated id. */
    public static long insert(Business b) throws SQLException {
        String sql = """
            INSERT INTO business(name,tin,branch_code,address_line,city,country,phone,email,
                                 currency,vat_rate,efris_device_no,efris_username,efris_password,efris_branch_id,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i=1;
            ps.setString(i++, b.name);
            ps.setString(i++, b.tin);
            ps.setString(i++, b.branchCode);
            ps.setString(i++, b.addressLine);
            ps.setString(i++, b.city);
            ps.setString(i++, b.country);
            ps.setString(i++, b.phone);
            ps.setString(i++, b.email);
            ps.setString(i++, b.currency != null ? b.currency : "UGX");
            if (b.vatRate == null) ps.setNull(i++, Types.REAL); else ps.setDouble(i++, b.vatRate);
            ps.setString(i++, b.efrisDeviceNo);
            ps.setString(i++, b.efrisUsername);
            ps.setString(i++, b.efrisPassword);
            ps.setString(i++, b.efrisBranchId);
            ps.setString(i++, Instant.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            return 0L;
        }
    }

    /** Update existing by id. */
    public static void update(Business b) throws SQLException {
        if (b.id == null) throw new IllegalArgumentException("Business.id is null");
        String sql = """
            UPDATE business SET
              name=?, tin=?, branch_code=?, address_line=?, city=?, country=?, phone=?, email=?,
              currency=?, vat_rate=?, efris_device_no=?, efris_username=?, efris_password=?, efris_branch_id=?,
              updated_at=?
            WHERE id=?
            """;
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i=1;
            ps.setString(i++, b.name);
            ps.setString(i++, b.tin);
            ps.setString(i++, b.branchCode);
            ps.setString(i++, b.addressLine);
            ps.setString(i++, b.city);
            ps.setString(i++, b.country);
            ps.setString(i++, b.phone);
            ps.setString(i++, b.email);
            ps.setString(i++, b.currency);
            if (b.vatRate == null) ps.setNull(i++, Types.REAL); else ps.setDouble(i++, b.vatRate);
            ps.setString(i++, b.efrisDeviceNo);
            ps.setString(i++, b.efrisUsername);
            ps.setString(i++, b.efrisPassword);
            ps.setString(i++, b.efrisBranchId);
            ps.setString(i++, Instant.now().toString());
            ps.setLong(i++, b.id);
            ps.executeUpdate();
        }
    }

    /** (Optional) Delete the single row. Rarely used. */
    public static void deleteById(long id) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM business WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }
}