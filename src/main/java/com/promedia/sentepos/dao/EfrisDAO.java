package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;

import java.sql.*;

public final class EfrisDAO {
    private EfrisDAO(){}

    //** DTO that mirrors the efris_invoices table (snake_case field names kept). */
    public static final class Rec {
        public long   id;
        public long   sale_id;
        public String status;             // PENDING | SENT | FAILED
        public String request_json;
        public String response_json;
        public String invoice_number;
        public String qr_base64;
        public String verification_code;
        public String created_at;
        public String sent_at;
    }

    /** Upsert PENDING row before hitting EFRIS. */
    public static void upsertPending(long saleId, String requestJson) throws SQLException {
        try (Connection c = Db.get()) {
            // Try insert; if exists, update.
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO efris_invoices(sale_id,status,request_json,created_at) " +
                    "VALUES(?, 'PENDING', ?, datetime('now'))")) {
                ins.setLong(1, saleId);
                ins.setString(2, requestJson);
                ins.executeUpdate();
            } catch (SQLException dup) {
                try (PreparedStatement upd = c.prepareStatement(
                        "UPDATE efris_invoices SET status='PENDING', request_json=?, response_json=NULL, " +
                        "invoice_number=NULL, qr_base64=NULL, verification_code=NULL, error_message=NULL, sent_at=NULL " +
                        "WHERE sale_id=?")) {
                    upd.setString(1, requestJson);
                    upd.setLong(2, saleId);
                    upd.executeUpdate();
                }
            }
        }
    }

    /** Mark SENT (overload without QR/verification). */
    public static void markSent(long saleId, String responseJson, String invoiceNumber) throws SQLException {
        markSent(saleId, responseJson, invoiceNumber, null, null);
    }

    /** Mark SENT (full). */
    public static void markSent(long saleId, String responseJson, String invoiceNumber,
                                String qrBase64, String verificationCode) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE efris_invoices SET status='SENT', response_json=?, invoice_number=?, " +
                     "qr_base64=?, verification_code=?, error_message=NULL, sent_at=datetime('now') " +
                     "WHERE sale_id=?")) {
            ps.setString(1, responseJson);
            ps.setString(2, invoiceNumber);
            ps.setString(3, qrBase64);
            ps.setString(4, verificationCode);
            ps.setLong(5, saleId);
            ps.executeUpdate();
        }
    }

    /** Mark FAILED. */
    public static void markFailed(long saleId, String responseJson, String errorMessage) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE efris_invoices SET status='FAILED', response_json=?, error_message=? WHERE sale_id=?")) {
            ps.setString(1, responseJson);
            ps.setString(2, errorMessage);
            ps.setLong(3, saleId);
            ps.executeUpdate();
        }
    }

    /** Read by sale_id. Returns null if not found. */
    public static Rec findBySaleId(long saleId) throws SQLException {
        String sql = "SELECT id, sale_id, status, request_json, response_json, invoice_number, " +
                     "qr_base64, verification_code, created_at, sent_at " +
                     "FROM efris_invoices WHERE sale_id=?";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Rec r = new Rec();
                r.id                = rs.getLong(1);
                r.sale_id           = rs.getLong(2);
                r.status            = rs.getString(3);
                r.request_json      = rs.getString(4);
                r.response_json     = rs.getString(5);
                r.invoice_number    = rs.getString(6);
                r.qr_base64         = rs.getString(7);
                r.verification_code = rs.getString(8);
                r.created_at        = rs.getString(9);
                r.sent_at           = rs.getString(10);
                return r;
            }
        }
    }
}