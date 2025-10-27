package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;
import com.promedia.sentepos.model.Payment;
import com.promedia.sentepos.model.Sale;
import com.promedia.sentepos.model.SaleItem;

import java.sql.*;
import java.util.List;

public final class SaleDAO {
    private SaleDAO(){}

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
                    try (PreparedStatement ps2 = c.prepareStatement("UPDATE products SET stock_qty=COALESCE(stock_qty,0)-? WHERE id=? AND is_service=0")) {
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
}