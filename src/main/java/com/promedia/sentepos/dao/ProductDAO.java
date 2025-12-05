package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;
import com.promedia.sentepos.model.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class ProductDAO {
    private ProductDAO() {}

    // ---------------------------------------------------------------------
    // SQL templates
    // ---------------------------------------------------------------------

    private static final String INSERT =
        "INSERT INTO products (" +
        " item_name, sku, commodity_code, is_service, measure_unit, unit_price, currency," +
        " vat_category, vat_rate, barcode, brand, specification, package_unit, package_qty," +
        " stock_qty, reorder_level, active" +
        ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SELECT_BASE =
        "SELECT id, item_name, sku, commodity_code, is_service, measure_unit, unit_price, currency," +
        " vat_category, vat_rate, barcode, brand, specification, package_unit, package_qty," +
        " stock_qty, reorder_level, active, created_at FROM products ";

    private static final String UPDATE =
        "UPDATE products SET " +
        " item_name=?, sku=?, commodity_code=?, is_service=?, measure_unit=?, unit_price=?, currency=?," +
        " vat_category=?, vat_rate=?, barcode=?, brand=?, specification=?, package_unit=?, package_qty=?," +
        " stock_qty=?, reorder_level=?, active=? WHERE id=?";

    private static final String DELETE =
        "DELETE FROM products WHERE id=?";

    // ---------------------------------------------------------------------
    // Insert
    // ---------------------------------------------------------------------

    public static long insert(Product p) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            int i = 1;
            ps.setString(i++, required(p.getItemName()));
            ps.setString(i++, nullable(p.getSku()));
            ps.setString(i++, nullable(p.getCommodityCode()));
            ps.setInt(i++, p.getIsService());
            ps.setString(i++, nullable(p.getMeasureUnit()));        // <-- UOM code from combo
            ps.setDouble(i++, p.getUnitPrice());
            ps.setString(i++, required(p.getCurrency()));
            ps.setString(i++, required(p.getVatCategory()));
            ps.setDouble(i++, p.getVatRate());
            ps.setString(i++, nullable(p.getBarcode()));
            ps.setString(i++, nullable(p.getBrand()));
            ps.setString(i++, nullable(p.getSpecification()));
            ps.setString(i++, nullable(p.getPackageUnit()));

            if (p.getPackageQty() == null)  ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, p.getPackageQty());
            if (p.getStockQty() == null)    ps.setNull(i++, Types.REAL);    else ps.setDouble(i++, p.getStockQty());
            if (p.getReorderLevel() == null) ps.setNull(i++, Types.REAL);   else ps.setDouble(i++, p.getReorderLevel());

            ps.setInt(i++, p.getActive());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Query APIs
    // ---------------------------------------------------------------------

    public static List<ProductRow> listAll(boolean includeInactive) throws SQLException {
        String sql = SELECT_BASE + (includeInactive
                ? "ORDER BY item_name"
                : "WHERE active=1 ORDER BY item_name");

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<ProductRow> out = new ArrayList<>();
            while (rs.next()) out.add(mapRow(rs));
            return out;
        }
    }

    public static ProductRow findById(long id) throws SQLException {
        String sql = SELECT_BASE + "WHERE id=?";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** Lookup by exact SKU or barcode (active products only). */
    public static ProductRow findBySkuOrBarcode(String code) throws SQLException {
        String sql = SELECT_BASE +
                     "WHERE (sku = ? OR barcode = ?) AND active = 1";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, code);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** Search active products by partial name or SKU. */
    public static List<ProductRow> searchByNameOrSku(String q) throws SQLException {
        String sql = SELECT_BASE +
                     "WHERE active = 1 AND (item_name LIKE ? OR sku LIKE ?) " +
                     "ORDER BY item_name";

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            String like = "%" + q + "%";
            ps.setString(1, like);
            ps.setString(2, like);

            try (ResultSet rs = ps.executeQuery()) {
                List<ProductRow> out = new ArrayList<>();
                while (rs.next()) out.add(mapRow(rs));
                return out;
            }
        }
    }

    /**
     * Find measure_unit (UOM code) by SKU from products table.
     * Returns null if SKU not found or measure_unit is NULL.
     */
    public static String findMeasureUnitBySku(String sku) throws SQLException {
        if (sku == null || sku.isBlank()) return null;

        final String sql = "SELECT measure_unit FROM products WHERE sku = ?";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, sku);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1); // may be null
            }
        }
    }

    // ---------------------------------------------------------------------
    // Update / delete
    // ---------------------------------------------------------------------

    public static int update(long id, Product p) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(UPDATE)) {

            int i = 1;
            ps.setString(i++, required(p.getItemName()));
            ps.setString(i++, nullable(p.getSku()));
            ps.setString(i++, nullable(p.getCommodityCode()));
            ps.setInt(i++, p.getIsService());
            ps.setString(i++, nullable(p.getMeasureUnit()));        // <-- UOM code
            ps.setDouble(i++, p.getUnitPrice());
            ps.setString(i++, required(p.getCurrency()));
            ps.setString(i++, required(p.getVatCategory()));
            ps.setDouble(i++, p.getVatRate());
            ps.setString(i++, nullable(p.getBarcode()));
            ps.setString(i++, nullable(p.getBrand()));
            ps.setString(i++, nullable(p.getSpecification()));
            ps.setString(i++, nullable(p.getPackageUnit()));

            if (p.getPackageQty() == null)  ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, p.getPackageQty());
            if (p.getStockQty() == null)    ps.setNull(i++, Types.REAL);    else ps.setDouble(i++, p.getStockQty());
            if (p.getReorderLevel() == null) ps.setNull(i++, Types.REAL);   else ps.setDouble(i++, p.getReorderLevel());

            ps.setInt(i++, p.getActive());
            ps.setLong(i++, id);

            return ps.executeUpdate();
        }
    }

    /** Soft delete = set active flag. */
    public static int setActive(long id, boolean active) throws SQLException {
        String sql = "UPDATE products SET active = ? WHERE id = ?";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, active ? 1 : 0);
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    /** Hard delete — better to use setActive(false) in production. */
    public static int deleteById(long id) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(DELETE)) {

            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------------
    // Helpers & row mapping
    // ---------------------------------------------------------------------

    private static String required(String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException("Required field missing.");
        }
        return s.trim();
    }

    private static String nullable(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static final class ProductRow {
        public final long id;
        public final String itemName;
        public final String sku;
        public final String commodityCode;
        public final int isService;
        public final String measureUnit;
        public final Double unitPrice;
        public final String currency;
        public final String vatCategory;
        public final Double vatRate;
        public final String barcode;
        public final String brand;
        public final String specification;
        public final String packageUnit;
        public final Integer packageQty;
        public final Double stockQty;
        public final Double reorderLevel;
        public final int active;
        public final String createdAt;

        public ProductRow(long id,
                          String itemName,
                          String sku,
                          String commodityCode,
                          int isService,
                          String measureUnit,
                          Double unitPrice,
                          String currency,
                          String vatCategory,
                          Double vatRate,
                          String barcode,
                          String brand,
                          String specification,
                          String packageUnit,
                          Integer packageQty,
                          Double stockQty,
                          Double reorderLevel,
                          int active,
                          String createdAt) {

            this.id = id;
            this.itemName = itemName;
            this.sku = sku;
            this.commodityCode = commodityCode;
            this.isService = isService;
            this.measureUnit = measureUnit;
            this.unitPrice = unitPrice;
            this.currency = currency;
            this.vatCategory = vatCategory;
            this.vatRate = vatRate;
            this.barcode = barcode;
            this.brand = brand;
            this.specification = specification;
            this.packageUnit = packageUnit;
            this.packageQty = packageQty;
            this.stockQty = stockQty;
            this.reorderLevel = reorderLevel;
            this.active = active;
            this.createdAt = createdAt;
        }
    }

    private static ProductRow mapRow(ResultSet rs) throws SQLException {
        return new ProductRow(
            rs.getLong("id"),
            rs.getString("item_name"),
            rs.getString("sku"),
            rs.getString("commodity_code"),
            rs.getInt("is_service"),
            rs.getString("measure_unit"),
            rs.getDouble("unit_price"),
            rs.getString("currency"),
            rs.getString("vat_category"),
            rs.getDouble("vat_rate"),
            rs.getString("barcode"),
            rs.getString("brand"),
            rs.getString("specification"),
            rs.getString("package_unit"),
            (Integer) rs.getObject("package_qty"),
            (Double) rs.getObject("stock_qty"),
            (Double) rs.getObject("reorder_level"),
            rs.getInt("active"),
            rs.getString("created_at")
        );
    }
}