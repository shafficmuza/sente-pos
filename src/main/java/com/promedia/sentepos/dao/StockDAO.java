package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class StockDAO {
    private StockDAO(){}

    /** Increment (or decrement if negative) product stock and record a movement. */
    public static void adjust(long productId, double qtyDelta, String note) throws SQLException {
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE products SET stock_qty = COALESCE(stock_qty,0) + ? WHERE id=? AND is_service=0")) {
                    ps.setDouble(1, qtyDelta);
                    ps.setLong(2, productId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO stock_moves (product_id, qty_delta, note) VALUES (?,?,?)")) {
                    ps.setLong(1, productId);
                    ps.setDouble(2, qtyDelta);
                    ps.setString(3, note);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                try { c.rollback(); } catch (Exception ignore) {}
                throw e;
            } finally {
                try { c.setAutoCommit(true); } catch (Exception ignore) {}
            }
        }
    }

    /** Set absolute stock (overwrites), recording delta in stock_moves. */
    public static void setAbsolute(long productId, double newQty, String note) throws SQLException {
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try {
                double current;
                try (var ps = c.prepareStatement("SELECT COALESCE(stock_qty,0) FROM products WHERE id=?")) {
                    ps.setLong(1, productId);
                    try (var rs = ps.executeQuery()) {
                        current = rs.next()? rs.getDouble(1) : 0.0;
                    }
                }
                double delta = newQty - current;

                try (var ps = c.prepareStatement("UPDATE products SET stock_qty=? WHERE id=? AND is_service=0")) {
                    ps.setDouble(1, newQty);
                    ps.setLong(2, productId);
                    ps.executeUpdate();
                }
                try (var ps = c.prepareStatement("INSERT INTO stock_moves (product_id, qty_delta, note) VALUES (?,?,?)")) {
                    ps.setLong(1, productId);
                    ps.setDouble(2, delta);
                    ps.setString(3, note);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                try { c.rollback(); } catch (Exception ignore) {}
                throw e;
            } finally {
                try { c.setAutoCommit(true); } catch (Exception ignore) {}
            }
        }
    }
}