package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;
import com.promedia.sentepos.model.Payment;
import com.promedia.sentepos.model.Sale;
import com.promedia.sentepos.model.SaleItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class SaleDAO {
    private SaleDAO(){}

    /* ===================== EXISTING API (unchanged) ===================== */

    public static long createEmptySale(Connection c, String receiptNo) throws SQLException {
        String sql = "INSERT INTO sales (receipt_no, subtotal, vat_total, total, paid, change_due, note) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, receiptNo);
            ps.setDouble(2, 0);
            ps.setDouble(3, 0);
            ps.setDouble(4, 0);
            ps.setDouble(5, 0);
            ps.setDouble(6, 0);
            ps.setString(7, null);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next()? rs.getLong(1) : -1L;
            }
        }
    }

    public static void addItems(Connection c, long saleId, List<SaleItem> items) throws SQLException {
        String sql = "INSERT INTO sale_items (sale_id, product_id, item_name, sku, qty, unit_price, vat_rate, line_total, vat_amount) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (SaleItem it : items) {
                ps.setLong(1, saleId);
                ps.setLong(2, it.productId);
                ps.setString(3, it.itemName);
                ps.setString(4, it.sku);
                ps.setDouble(5, it.qty);
                ps.setDouble(6, it.unitPrice);
                ps.setDouble(7, it.vatRate);
                ps.setDouble(8, it.lineTotal);
                ps.setDouble(9, it.vatAmount);
                ps.addBatch();

                // decrement stock for goods
                if (it.qty > 0) {
                    try (PreparedStatement ps2 = c.prepareStatement(
                            "UPDATE products SET stock_qty=COALESCE(stock_qty,0)-? WHERE id=? AND is_service=0")) {
                        ps2.setDouble(1, it.qty);
                        ps2.setLong(2, it.productId);
                        ps2.executeUpdate();
                    }
                }
            }
            ps.executeBatch();
        }
    }

    public static void addPayment(Connection c, long saleId, Payment p) throws SQLException {
        String sql = "INSERT INTO payments (sale_id, method, amount, reference) VALUES (?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, saleId);
            ps.setString(2, p.method.name());
            ps.setDouble(3, p.amount);
            ps.setString(4, p.reference);
            ps.executeUpdate();
        }
    }

    public static void finalizeTotals(Connection c, long saleId, double subtotal, double vatTotal, double total, double paid, double changeDue, String note) throws SQLException {
        String sql = "UPDATE sales SET subtotal=?, vat_total=?, total=?, paid=?, change_due=?, note=? WHERE id=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, subtotal);
            ps.setDouble(2, vatTotal);
            ps.setDouble(3, total);
            ps.setDouble(4, paid);
            ps.setDouble(5, changeDue);
            ps.setString(6, note);
            ps.setLong(7, saleId);
            ps.executeUpdate();
        }
    }

    public static Connection beginTx() throws SQLException {
        Connection c = Db.get();
        c.setAutoCommit(false);
        return c;
    }

    public static void commit(Connection c) {
        if (c != null) {
            try { c.commit(); } catch (Exception ignored) {}
            try { c.setAutoCommit(true); } catch (Exception ignored) {}
            try { c.close(); } catch (Exception ignored) {}
        }
    }

    public static void rollback(Connection c) {
        if (c != null) {
            try { c.rollback(); } catch (Exception ignored) {}
            try { c.setAutoCommit(true); } catch (Exception ignored) {}
            try { c.close(); } catch (Exception ignored) {}
        }
    }

    /* ===================== NEW HELPERS (for Sales page/preview/fiscalise) ===================== */

    /** Lightweight row for listing sales. */
    public static final class SaleHead {
        public long id;
        public String receipt_no;
        public String date_time;
        public double total;
        public double paid;
        public double change_due;
        public String note;
    }

    /** Aggregate bundle for preview/print/fiscalise (sale items + best payment). */
    public static final class Aggregate {
        public Sale sale;       // with items
        public Payment payment; // chosen payment or fallback CASH
    }

    /**
     * List recent sales, optionally filtering by receipt_no or note (contains).
     * @param limit  max rows (e.g., 200)
     * @param search nullable search string
     */
    public static List<SaleHead> listRecent(int limit, String search) throws SQLException {
        String base = "SELECT id, receipt_no, date_time, total, paid, change_due, note FROM sales ";
        String where = "";
        if (search != null && !search.trim().isEmpty()) {
            where = "WHERE (receipt_no LIKE ? OR note LIKE ?) ";
        }
        String order = "ORDER BY id DESC ";
        String lim = "LIMIT ?";

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(base + where + order + lim)) {

            int idx = 1;
            if (!where.isEmpty()) {
                String q = "%" + search.trim() + "%";
                ps.setString(idx++, q);
                ps.setString(idx++, q);
            }
            ps.setInt(idx, limit);

            try (ResultSet rs = ps.executeQuery()) {
                List<SaleHead> out = new ArrayList<>();
                while (rs.next()) {
                    SaleHead h = new SaleHead();
                    h.id         = rs.getLong(1);
                    h.receipt_no = rs.getString(2);
                    h.date_time  = rs.getString(3);
                    h.total      = rs.getDouble(4);
                    h.paid       = rs.getDouble(5);
                    h.change_due = rs.getDouble(6);
                    h.note       = rs.getString(7);
                    out.add(h);
                }
                return out;
            }
        }
    }

    /**
     * Load a sale aggregate: items + best payment for the given sale id.
     * - Items from sale_items (item_name, sku, qty, unit_price, vat_rate, line_total, vat_amount)
     * - Payment: latest payments row if present; fallback to CASH with amount = sales.total
     */
    // --- replace the start of loadAggregate(...) down to where items are read ---
public static Aggregate loadAggregate(long saleId) throws SQLException {
    Aggregate agg = new Aggregate();
    Sale sale = new Sale();                 // keep your Sale() as-is (items is final inside)
    List<SaleItem> tmpItems = new ArrayList<>();

    try (Connection c = Db.get()) {
        // Items
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT item_name, sku, qty, unit_price, vat_rate, line_total, vat_amount " +
                "FROM sale_items WHERE sale_id=?")) {
            ps.setLong(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SaleItem it = new SaleItem();
                    it.itemName  = rs.getString(1);
                    it.sku       = rs.getString(2);
                    it.qty       = rs.getDouble(3);
                    it.unitPrice = rs.getDouble(4);
                    it.vatRate   = rs.getDouble(5);
                    it.lineTotal = rs.getDouble(6);
                    it.vatAmount = rs.getDouble(7);
                    tmpItems.add(it);
                }
            }
        }

        sale.items.addAll(tmpItems);  // ✅ no reassignment

        // Head total (for fallback)
        double headTotal = 0.0;
        try (PreparedStatement ps = c.prepareStatement("SELECT total FROM sales WHERE id=?")) {
            ps.setLong(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) headTotal = rs.getDouble(1);
            }
        }

        // Best payment (latest)
        Payment payment = null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT method, amount, reference FROM payments WHERE sale_id=? ORDER BY id DESC LIMIT 1")) {
            ps.setLong(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    payment = new Payment();
                    String m = rs.getString(1);
                    try { payment.method = Payment.Method.valueOf(m); }
                    catch (Exception ignore) { payment.method = Payment.Method.CASH; }
                    payment.amount    = rs.getDouble(2);
                    payment.reference = rs.getString(3);
                }
            }
        }
        if (payment == null) {
            payment = new Payment();
            payment.method = Payment.Method.CASH;
            payment.amount = headTotal;
        }

        agg.sale = sale;
        agg.payment = payment;
        return agg;
    }
}
}