package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;

import java.sql.*;

public final class EfrisCreditNoteDAO {
    private EfrisCreditNoteDAO(){}

    /** DTO for efris_credit_notes. */
    public static final class Rec {
        public long   id;
        public long   credit_note_id;
        public String status;             // PENDING | SENT | FAILED | CANCELLED
        public String request_json;
        public String response_json;
        public String invoice_number;
        public String reference_number;   // NEW
        public String qr_base64;
        public String verification_code;
        public String error_message;
        public String created_at;
        public String sent_at;
    }

    // ---------- PENDING ----------

    public static void upsertPending(long creditNoteId, String requestJson) throws SQLException {
        try (Connection c = Db.get()) {
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO efris_credit_notes(" +
                    " credit_note_id,status,request_json,created_at" +
                    ") VALUES(?, 'PENDING', ?, datetime('now'))")) {

                ins.setLong(1, creditNoteId);
                ins.setString(2, requestJson);
                ins.executeUpdate();
            } catch (SQLException dup) {
                try (PreparedStatement upd = c.prepareStatement(
                        "UPDATE efris_credit_notes " +
                        "SET status='PENDING', request_json=?, response_json=NULL, " +
                        "    invoice_number=NULL, reference_number=NULL, " + // NEW
                        "    qr_base64=NULL, verification_code=NULL, " +
                        "    error_message=NULL, sent_at=NULL " +
                        "WHERE credit_note_id=?")) {

                    upd.setString(1, requestJson);
                    upd.setLong(2, creditNoteId);
                    upd.executeUpdate();
                }
            }
        }
    }

    // ---------- SENT ----------

    public static void markSent(long creditNoteId,
                                String responseJson,
                                String invoiceNumber,
                                String qrBase64,
                                String verificationCode,
                                String referenceNumber) throws SQLException {   // NEW param
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE efris_credit_notes " +
                     "SET status='SENT', response_json=?, invoice_number=?, " +
                     "    reference_number=?, qr_base64=?, verification_code=?, " +
                     "    error_message=NULL, sent_at=datetime('now') " +
                     "WHERE credit_note_id=?")) {

            ps.setString(1, responseJson);
            ps.setString(2, invoiceNumber);
            ps.setString(3, referenceNumber);
            ps.setString(4, qrBase64);
            ps.setString(5, verificationCode);
            ps.setLong(6, creditNoteId);
            ps.executeUpdate();
        }
    }

    // ---------- FAILED ----------

    public static void markFailed(long creditNoteId,
                                  String responseJson,
                                  String errorMessage) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE efris_credit_notes " +
                     "SET status='FAILED', response_json=?, error_message=?, " +
                     "    sent_at=datetime('now') " +
                     "WHERE credit_note_id=?")) {

            ps.setString(1, responseJson);
            ps.setString(2, errorMessage);
            ps.setLong(3, creditNoteId);
            ps.executeUpdate();
        }
    }

    // ---------- QUERY ----------

    public static Rec findByCreditNoteId(long creditNoteId) throws SQLException {
        final String sql =
            "SELECT id, credit_note_id, status, request_json, response_json," +
            "       invoice_number, reference_number, qr_base64, verification_code," +
            "       error_message, created_at, sent_at " +
            "FROM efris_credit_notes WHERE credit_note_id=?";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, creditNoteId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Rec r = new Rec();
                r.id                = rs.getLong(1);
                r.credit_note_id    = rs.getLong(2);
                r.status            = rs.getString(3);
                r.request_json      = rs.getString(4);
                r.response_json     = rs.getString(5);
                r.invoice_number    = rs.getString(6);
                r.reference_number  = rs.getString(7);
                r.qr_base64         = rs.getString(8);
                r.verification_code = rs.getString(9);
                r.error_message     = rs.getString(10);
                r.created_at        = rs.getString(11);
                r.sent_at           = rs.getString(12);
                return r;
            }
        }
    }
}