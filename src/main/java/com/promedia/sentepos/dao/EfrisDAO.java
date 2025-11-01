package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;
import java.sql.*;
import java.time.Instant;

public class EfrisDAO {
    
        //** One row from efris_invoices */
 public static final class Rec {
    public long saleId;
    public String status;
    public String requestJson;
    public String responseJson;
    public String invoiceNumber;
    public String qrBase64;
    public String errorMessage;
    public String createdAt;
    public String sentAt;
}
    
    //** Queue (or re-queue) a sale for fiscalisation as PENDING with the payload in request_json */
public static void upsertPending(long saleId, String requestJson) throws java.sql.SQLException {
    final String sql =
        "INSERT INTO efris_invoices (sale_id,status,request_json) " +
        "VALUES (?, 'PENDING', ?) " +
        "ON CONFLICT(sale_id) DO UPDATE SET " +
        "  status='PENDING', request_json=excluded.request_json, " +
        "  response_json=NULL, invoice_number=NULL, qr_base64=NULL, error_message=NULL, " +
        "  sent_at=NULL";
    try (var c = com.promedia.sentepos.db.Db.get(); var ps = c.prepareStatement(sql)) {
        ps.setLong(1, saleId);
        ps.setString(2, requestJson);
        ps.executeUpdate();
    }
}

   //** Mark SUCCESS after EFRIS returns invoice_number (+ optional QR) */
public static void markSuccess(long saleId, String invoiceNumber, String qrBase64, String responseJson)
        throws java.sql.SQLException {
    final String sql =
        "UPDATE efris_invoices " +
        "SET status='SENT', invoice_number=?, qr_base64=?, response_json=?, sent_at=datetime('now') " +
        "WHERE sale_id=?";
    try (var c = com.promedia.sentepos.db.Db.get(); var ps = c.prepareStatement(sql)) {
        ps.setString(1, invoiceNumber);
        ps.setString(2, qrBase64);
        ps.setString(3, responseJson);
        ps.setLong(4, saleId);
        ps.executeUpdate();
    }
}

    //** Mark FAILED and store response/error_message for troubleshooting */
public static void markFailed(long saleId, String responseJson, String errorMessage)
        throws java.sql.SQLException {
    final String sql =
        "UPDATE efris_invoices " +
        "SET status='FAILED', response_json=?, error_message=? " +
        "WHERE sale_id=?";
    try (var c = com.promedia.sentepos.db.Db.get(); var ps = c.prepareStatement(sql)) {
        ps.setString(1, responseJson);
        ps.setString(2, errorMessage);
        ps.setLong(3, saleId);
        ps.executeUpdate();
    }
}

    /** Read a single invoice row by sale_id (returns null if missing) */
public static Rec findBySaleId(long saleId) {
    final String sql =
        "SELECT sale_id, status, request_json, response_json, " +
        "       invoice_number, qr_base64, error_message, created_at, sent_at " +
        "FROM efris_invoices WHERE sale_id=?";
    try (var c = com.promedia.sentepos.db.Db.get(); var ps = c.prepareStatement(sql)) {
        ps.setLong(1, saleId);
        try (var rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            var r = new Rec();
            r.saleId        = rs.getLong("sale_id");
            r.status        = rs.getString("status");
            r.requestJson   = rs.getString("request_json");
            r.responseJson  = rs.getString("response_json");
            r.invoiceNumber = rs.getString("invoice_number");
            r.qrBase64      = rs.getString("qr_base64");
            r.errorMessage  = rs.getString("error_message");
            r.createdAt     = rs.getString("created_at");
            r.sentAt        = rs.getString("sent_at");
            return r;
        }
    } catch (java.sql.SQLException e) {
        throw new RuntimeException("EfrisDAO.findBySaleId failed", e);
    }
}

    public static class EfrisRecord {
        public String status;
        public String invoiceNumber;
        public String qrBase64;
    }
}