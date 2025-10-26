/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.promedia.sentepos.dao;

import com.promedia.sentepos.db.Db;
import com.promedia.sentepos.model.Product;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author shaffic
 */
public final class ProductDAO {
    private ProductDAO(){}

    private static final String INSERT =
        "INSERT INTO products ("
      + " item_name, sku, commodity_code, is_service, measure_unit, unit_price, currency,"
      + " vat_category, vat_rate, barcode, brand, specification, package_unit, package_qty,"
      + " stock_qty, reorder_level, active"
      + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SELECT_BASE =
        "SELECT id, item_name, sku, commodity_code, is_service, measure_unit, unit_price, currency,"
      + " vat_category, vat_rate, barcode, brand, specification, package_unit, package_qty,"
      + " stock_qty, reorder_level, active, created_at FROM products ";

    private static final String UPDATE =
        "UPDATE products SET "
      + " item_name=?, sku=?, commodity_code=?, is_service=?, measure_unit=?, unit_price=?, currency=?,"
      + " vat_category=?, vat_rate=?, barcode=?, brand=?, specification=?, package_unit=?, package_qty=?,"
      + " stock_qty=?, reorder_level=?, active=? WHERE id=?";

    private static final String DELETE = "DELETE FROM products WHERE id=?";

    public static long insert(Product p) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            int i=1;
            ps.setString(i++, required(p.getItemName()));
            ps.setString(i++, nullable(p.getSku()));
            ps.setString(i++, nullable(p.getCommodityCode()));
            ps.setInt(i++, p.getIsService());
            ps.setString(i++, nullable(p.getMeasureUnit()));
            ps.setDouble(i++, p.getUnitPrice());
            ps.setString(i++, required(p.getCurrency()));
            ps.setString(i++, required(p.getVatCategory()));
            ps.setDouble(i++, p.getVatRate());
            ps.setString(i++, nullable(p.getBarcode()));
            ps.setString(i++, nullable(p.getBrand()));
            ps.setString(i++, nullable(p.getSpecification()));
            ps.setString(i++, nullable(p.getPackageUnit()));
            if (p.getPackageQty()==null) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, p.getPackageQty());
            if (p.getStockQty()==null)   ps.setNull(i++, Types.REAL);    else ps.setDouble(i++, p.getStockQty());
            if (p.getReorderLevel()==null) ps.setNull(i++, Types.REAL);  else ps.setDouble(i++, p.getReorderLevel());
            ps.setInt(i++, p.getActive());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next()? rs.getLong(1) : -1L;
            }
        }
    }

    public static List<ProductRow> listAll(boolean includeInactive) throws SQLException {
        String sql = SELECT_BASE + (includeInactive ? "ORDER BY item_name" : "WHERE active=1 ORDER BY item_name");
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

    public static int update(long id, Product p) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(UPDATE)) {
            int i=1;
            ps.setString(i++, required(p.getItemName()));
            ps.setString(i++, nullable(p.getSku()));
            ps.setString(i++, nullable(p.getCommodityCode()));
            ps.setInt(i++, p.getIsService());
            ps.setString(i++, nullable(p.getMeasureUnit()));
            ps.setDouble(i++, p.getUnitPrice());
            ps.setString(i++, required(p.getCurrency()));
            ps.setString(i++, required(p.getVatCategory()));
            ps.setDouble(i++, p.getVatRate());
            ps.setString(i++, nullable(p.getBarcode()));
            ps.setString(i++, nullable(p.getBrand()));
            ps.setString(i++, nullable(p.getSpecification()));
            ps.setString(i++, nullable(p.getPackageUnit()));
            if (p.getPackageQty()==null) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, p.getPackageQty());
            if (p.getStockQty()==null)   ps.setNull(i++, Types.REAL);    else ps.setDouble(i++, p.getStockQty());
            if (p.getReorderLevel()==null) ps.setNull(i++, Types.REAL);  else ps.setDouble(i++, p.getReorderLevel());
            ps.setInt(i++, p.getActive());
            ps.setLong(i++, id);
            return ps.executeUpdate();
        }
    }

    /** Soft delete = set active=0 */
    public static int setActive(long id, boolean active) throws SQLException {
        String sql = "UPDATE products SET active=? WHERE id=?";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setLong(2, id);
            return ps.executeUpdate();
        }
    }

    /** Hard delete — use cautiously (recommend using setActive=false instead) */
    public static int deleteById(long id) throws SQLException {
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }

    // --- helpers & mapper ---

    private static String required(String s) {
        if (s==null || s.trim().isEmpty())
            throw new IllegalArgumentException("Required field missing.");
        return s.trim();
    }
    private static String nullable(String s) {
        if (s==null) return null;
        String t = s.trim();
        return t.isEmpty()? null : t;
    }

    public static final class ProductRow {
        public final long id;
        public final String itemName, sku, commodityCode, measureUnit, currency, vatCategory,
                barcode, brand, specification, packageUnit, createdAt;
        public final int isService, active;
        public final Double unitPrice, vatRate, stockQty, reorderLevel;
        public final Integer packageQty;

        public ProductRow(long id, String itemName, String sku, String commodityCode, int isService,
                          String measureUnit, Double unitPrice, String currency, String vatCategory,
                          Double vatRate, String barcode, String brand, String specification,
                          String packageUnit, Integer packageQty, Double stockQty, Double reorderLevel,
                          int active, String createdAt) {
            this.id=id; this.itemName=itemName; this.sku=sku; this.commodityCode=commodityCode;
            this.isService=isService; this.measureUnit=measureUnit; this.unitPrice=unitPrice; this.currency=currency;
            this.vatCategory=vatCategory; this.vatRate=vatRate; this.barcode=barcode; this.brand=brand;
            this.specification=specification; this.packageUnit=packageUnit; this.packageQty=packageQty;
            this.stockQty=stockQty; this.reorderLevel=reorderLevel; this.active=active; this.createdAt=createdAt;
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
