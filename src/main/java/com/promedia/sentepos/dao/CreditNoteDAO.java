package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class CreditNoteDAO {
    private CreditNoteDAO(){}

    // ========================= Existing API (unchchanged core models) =========================
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

    /**
     * Row used for listing credit notes with EFRIS info + totals for UI.
     */
    public static final class ListRow {
        public long   id;                      // credit note id
        public long   sale_id;                 // original sale id
        public String reason;
        public String date_time;
        public double subtotal;
        public double vat_total;
        public double total;
        public double total_qty;               // aggregated from credit_note_items
        public String status;                  // local CN status

        // EFRIS tracking (from efris_credit_notes)
        public String efris_status;            // PENDING/SENT/FAILED/CANCELLED
        public String efris_invoice_number;    // FDN from URA (for CN: invoiceNo when approved)
        public String efris_verification;      // verification_code
        public String efris_error_message;     // last return/error message
        public String reference_number;        // EFRIS credit note referenceNo (DB column reference_number)
    }

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

    // ========================= Core write APIs =========================

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

    // ========================= Read APIs =========================

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

    /** Load sold lines for a given sale_id (from sale_items) to drive CN dialog. */
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

    // ========================= Listing helpers (old + new) =========================

    /** Simple list of all credit notes (no EFRIS join). */
    public static List<Head> listAll() throws SQLException {
        final String sql =
            "SELECT id, sale_id, reason, date_time, subtotal, vat_total, total, status, note " +
            "FROM credit_notes " +
            "ORDER BY id DESC";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Head> out = new ArrayList<>();
                while (rs.next()) {
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
                    out.add(h);
                }
                return out;
            }
        }
    }

    /** Old-style join: credit notes + EFRIS (no qty aggregation). Still safe to keep. */
    public static List<ListRow> listAllWithEfris() throws SQLException {
        final String sql = """
            SELECT
                cn.id,
                cn.sale_id,
                cn.reason,
                cn.date_time,
                cn.subtotal,
                cn.vat_total,
                cn.total,
                cn.status,
                ecn.status,
                ecn.invoice_number,
                ecn.verification_code,
                ecn.error_message,
                ecn.reference_number
            FROM credit_notes cn
            LEFT JOIN efris_credit_notes ecn
              ON ecn.credit_note_id = cn.id
            ORDER BY cn.id DESC
            """;

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<ListRow> out = new ArrayList<>();
            while (rs.next()) {
                ListRow r = new ListRow();
                int col = 1;
                r.id                   = rs.getLong(col++);
                r.sale_id              = rs.getLong(col++);
                r.reason               = rs.getString(col++);
                r.date_time            = rs.getString(col++);
                r.subtotal             = rs.getDouble(col++);
                r.vat_total            = rs.getDouble(col++);
                r.total                = rs.getDouble(col++);
                // old version didn't aggregate qty → default 0
                r.total_qty            = 0.0;
                r.status               = rs.getString(col++);
                r.efris_status         = rs.getString(col++);
                r.efris_invoice_number = rs.getString(col++);
                r.efris_verification   = rs.getString(col++);
                r.efris_error_message  = rs.getString(col++);
                r.reference_number     = rs.getString(col++);
                out.add(r);
            }
            return out;
        }
    }

    /**
     * Full-feature listing for the Credit Note List UI.
     * Includes:
     *  - All credit note fields
     *  - Total quantity from items
     *  - EFRIS tracking (status, invoice number, verification code, error, reference number)
     *  - Optional search (by reason, status, FDN, reference, CN id, sale id)
     */
    public static List<ListRow> listForUi(String search) throws SQLException {

        String base = """
            SELECT
                cn.id,
                cn.sale_id,
                cn.reason,
                cn.date_time,
                cn.subtotal,
                cn.vat_total,
                cn.total,
                IFNULL(SUM(cni.qty), 0) AS total_qty,
                cn.status,
                ecn.status AS efris_status,
                ecn.invoice_number,
                ecn.verification_code,
                ecn.error_message,
                ecn.reference_number
            FROM credit_notes cn
            LEFT JOIN credit_note_items cni
                ON cni.credit_note_id = cn.id
            LEFT JOIN efris_credit_notes ecn
                ON ecn.credit_note_id = cn.id
            """;

        boolean filtered = (search != null && !search.isBlank());
        if (filtered) {
            base += """
                WHERE
                    cn.reason LIKE ? OR
                    cn.status LIKE ? OR
                    ecn.invoice_number LIKE ? OR
                    ecn.reference_number LIKE ? OR
                    CAST(cn.id AS TEXT) LIKE ? OR
                    CAST(cn.sale_id AS TEXT) LIKE ?
                """;
        }

        base += """
            GROUP BY
                cn.id,
                cn.sale_id,
                cn.reason,
                cn.date_time,
                cn.subtotal,
                cn.vat_total,
                cn.total,
                cn.status,
                ecn.status,
                ecn.invoice_number,
                ecn.verification_code,
                ecn.error_message,
                ecn.reference_number
            ORDER BY cn.id DESC
            """;

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(base)) {

            if (filtered) {
                String q = "%" + search.trim() + "%";
                ps.setString(1, q);
                ps.setString(2, q);
                ps.setString(3, q);
                ps.setString(4, q);
                ps.setString(5, q);
                ps.setString(6, q);
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<ListRow> list = new ArrayList<>();
                while (rs.next()) {
                    ListRow r = new ListRow();
                    int col = 1;

                    r.id                   = rs.getLong(col++);
                    r.sale_id              = rs.getLong(col++);
                    r.reason               = rs.getString(col++);
                    r.date_time            = rs.getString(col++);
                    r.subtotal             = rs.getDouble(col++);
                    r.vat_total            = rs.getDouble(col++);
                    r.total                = rs.getDouble(col++);
                    r.total_qty            = rs.getDouble(col++);
                    r.status               = rs.getString(col++);

                    r.efris_status         = rs.getString(col++);
                    r.efris_invoice_number = rs.getString(col++);
                    r.efris_verification   = rs.getString(col++);
                    r.efris_error_message  = rs.getString(col++);
                    r.reference_number     = rs.getString(col++);

                    list.add(r);
                }
                return list;
            }
        }
    }
}