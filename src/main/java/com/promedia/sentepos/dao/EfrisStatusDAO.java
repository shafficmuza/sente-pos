package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class EfrisStatusDAO {
    private EfrisStatusDAO(){}

    public static final class Row {
        public long saleId;
        public String receiptNo;
        public String status;           // PENDING | SENT | FAILED
        public String invoiceNumber;    // IRN/FDN
        public String verificationCode;
        public String qrBase64;
        public String errorMessage;
        public String createdAt;
        public String sentAt;
        public String requestJson;      // may be null
        public String responseJson;     // may be null
    }

    /** List recent EFRIS rows, joined with sales for receipt_no; optional search on receipt_no or invoice_number. */
    public static List<Row> listRecent(int limit, String search) throws SQLException {
        String base =
            "SELECT ei.sale_id, s.receipt_no, ei.status, ei.invoice_number, ei.verification_code, ei.qr_base64, " +
            "       ei.error_message, ei.created_at, ei.sent_at, ei.request_json, ei.response_json " +
            "FROM efris_invoices ei " +
            "LEFT JOIN sales s ON s.id = ei.sale_id ";
        String where = "";
        if (search != null && !search.trim().isEmpty()) {
            where = "WHERE (s.receipt_no LIKE ? OR ei.invoice_number LIKE ? OR CAST(ei.sale_id AS TEXT) LIKE ?) ";
        }
        String order = "ORDER BY ei.id DESC ";
        String lim   = "LIMIT ?";

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(base + where + order + lim)) {
            int i=1;
            if (!where.isEmpty()) {
                String q = "%" + search.trim() + "%";
                ps.setString(i++, q);
                ps.setString(i++, q);
                ps.setString(i++, q);
            }
            ps.setInt(i, limit);

            List<Row> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Row r = new Row();
                    r.saleId          = rs.getLong(1);
                    r.receiptNo       = rs.getString(2);
                    r.status          = rs.getString(3);
                    r.invoiceNumber   = rs.getString(4);
                    r.verificationCode= rs.getString(5);
                    r.qrBase64        = rs.getString(6);
                    r.errorMessage    = rs.getString(7);
                    r.createdAt       = rs.getString(8);
                    r.sentAt          = rs.getString(9);
                    r.requestJson     = rs.getString(10);
                    r.responseJson    = rs.getString(11);
                    out.add(r);
                }
            }
            return out;
        }
    }

    public static Row getBySaleId(long saleId) throws SQLException {
        String sql =
            "SELECT ei.sale_id, s.receipt_no, ei.status, ei.invoice_number, ei.verification_code, ei.qr_base64, " +
            "       ei.error_message, ei.created_at, ei.sent_at, ei.request_json, ei.response_json " +
            "FROM efris_invoices ei LEFT JOIN sales s ON s.id = ei.sale_id WHERE ei.sale_id=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Row r = new Row();
                r.saleId           = rs.getLong(1);
                r.receiptNo        = rs.getString(2);
                r.status           = rs.getString(3);
                r.invoiceNumber    = rs.getString(4);
                r.verificationCode = rs.getString(5);
                r.qrBase64         = rs.getString(6);
                r.errorMessage     = rs.getString(7);
                r.createdAt        = rs.getString(8);
                r.sentAt           = rs.getString(9);
                r.requestJson      = rs.getString(10);
                r.responseJson     = rs.getString(11);
                return r;
            }
        }
    }
}