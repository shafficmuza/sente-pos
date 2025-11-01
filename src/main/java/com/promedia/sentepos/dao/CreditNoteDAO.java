package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class CreditNoteDAO {
    private CreditNoteDAO(){}

    // ========================= Existing API (unchanged) =========================
    public static final class Head {
        public long id;
        public long sale_id;
        public String reason;
        public String date_time;
        public double subtotal, vat_total, total;
        public String status; // DRAFT/PENDING/SENT/FAILED/CANCELLED
        public String note;
    }

    public static final class Item {
        public long id;
        public long credit_note_id;
        public long product_id;
        public String item_name, sku;
        public double qty, unit_price, vat_rate, line_total, vat_amount;
    }

    /** Create a DRAFT credit note head (no date_time stamp yet). */
    public static long createHead(long sale_id, String reason, double subtotal, double vat_total, double total, String note)
            throws SQLException {
        final String sql =
            "INSERT INTO credit_notes(sale_id, reason, subtotal, vat_total, total, note, status) " +
            "VALUES(?,?,?,?,?,?,'DRAFT')";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, sale_id);
            ps.setString(2, reason);
            ps.setDouble(3, subtotal);
            ps.setDouble(4, vat_total);
            ps.setDouble(5, total);
            ps.setString(6, note);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public static void addItem(long credit_note_id, Item it) throws SQLException {
        final String sql =
            "INSERT INTO credit_note_items(" +
            "credit_note_id, product_id, item_name, sku, qty, unit_price, vat_rate, line_total, vat_amount" +
            ") VALUES(?,?,?,?,?,?,?,?,?)";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, credit_note_id);
            ps.setLong(2, it.product_id);
            ps.setString(3, it.item_name);
            ps.setString(4, it.sku);
            ps.setDouble(5, it.qty);
            ps.setDouble(6, it.unit_price);
            ps.setDouble(7, it.vat_rate);
            ps.setDouble(8, it.line_total);
            ps.setDouble(9, it.vat_amount);
            ps.executeUpdate();
        }
    }

    public static void setStatus(long credit_note_id, String status) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE credit_notes SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setLong(2, credit_note_id);
            ps.executeUpdate();
        }
    }

    public static Head findHead(long id) throws SQLException {
        final String sql =
            "SELECT id, sale_id, reason, date_time, subtotal, vat_total, total, status, note " +
            "FROM credit_notes WHERE id=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Head h = new Head();
                h.id        = rs.getLong(1);
                h.sale_id   = rs.getLong(2);
                h.reason    = rs.getString(3);
                h.date_time = rs.getString(4);
                h.subtotal  = rs.getDouble(5);
                h.vat_total = rs.getDouble(6);
                h.total     = rs.getDouble(7);
                h.status    = rs.getString(8);
                h.note      = rs.getString(9);
                return h;
            }
        }
    }

    public static List<Item> listItems(long credit_note_id) throws SQLException {
        final String sql =
            "SELECT id, credit_note_id, product_id, item_name, sku, qty, unit_price, vat_rate, line_total, vat_amount " +
            "FROM credit_note_items WHERE credit_note_id=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, credit_note_id);
            try (ResultSet rs = ps.executeQuery()) {
                List<Item> out = new ArrayList<>();
                while (rs.next()) {
                    Item i = new Item();
                    i.id              = rs.getLong(1);
                    i.credit_note_id  = rs.getLong(2);
                    i.product_id      = rs.getLong(3);
                    i.item_name       = rs.getString(4);
                    i.sku             = rs.getString(5);
                    i.qty             = rs.getDouble(6);
                    i.unit_price      = rs.getDouble(7);
                    i.vat_rate        = rs.getDouble(8);
                    i.line_total      = rs.getDouble(9);
                    i.vat_amount      = rs.getDouble(10);
                    out.add(i);
                }
                return out;
            }
        }
    }

    // ========================= Additions for dialog / flow =========================

    /** Row from original sale to drive "return_qty" input in the dialog. */
    public static final class SaleLine {
        public long product_id;
        public String item_name;
        public String sku;
        public double sold_qty;
        public double unit_price;
        public double vat_rate;
        // UI field (not persisted): how much is being returned
        public double return_qty;
    }

    /** Minimal line payload used when issuing the CN. */
    public static final class ItemLine {
        public long product_id;
        public String item_name;
        public String sku;
        public double qty;
        public double unit_price;
        public double vat_rate;
    }

    /** Load sold lines for a given sale_id (from sale_items). */
    public static List<SaleLine> saleItemsForSale(long sale_id) throws SQLException {
        final String sql = """
            SELECT product_id, item_name, sku, qty, unit_price, vat_rate
            FROM sale_items
            WHERE sale_id = ?
        """;
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, sale_id);
            try (ResultSet rs = ps.executeQuery()) {
                List<SaleLine> rows = new ArrayList<>();
                while (rs.next()) {
                    SaleLine x = new SaleLine();
                    x.product_id  = rs.getLong("product_id");
                    x.item_name   = rs.getString("item_name");
                    x.sku         = rs.getString("sku");
                    x.sold_qty    = rs.getDouble("qty");
                    x.unit_price  = rs.getDouble("unit_price");
                    x.vat_rate    = rs.getDouble("vat_rate");
                    x.return_qty  = 0.0;
                    rows.add(x);
                }
                return rows;
            }
        }
    }

    /** Update head monetary totals (useful if UI recalculates before issuing). */
    public static void updateTotals(long credit_note_id, double subtotal, double vat_total, double total)
            throws SQLException {
        final String sql =
            "UPDATE credit_notes SET subtotal=?, vat_total=?, total=? WHERE id=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, subtotal);
            ps.setDouble(2, vat_total);
            ps.setDouble(3, total);
            ps.setLong(4, credit_note_id);
            ps.executeUpdate();
        }
    }

    /**
     * When the cashier confirms issue:
     * - stamps date_time = now
     * - transitions status -> PENDING (ready for fiscalisation queue)
     */
    public static void finaliseToPending(long credit_note_id) throws SQLException {
        final String sql =
            "UPDATE credit_notes SET date_time = datetime('now'), status='PENDING' WHERE id=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, credit_note_id);
            ps.executeUpdate();
        }
    }
}