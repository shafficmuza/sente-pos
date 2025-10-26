package com.promedia.sentepos.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Migrations {
    private Migrations(){}

    public static void ensure() {
        final String products =
            "CREATE TABLE IF NOT EXISTS products ("
          + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + " item_name      TEXT NOT NULL,"
          + " sku            TEXT UNIQUE,"
          + " commodity_code TEXT,"
          + " is_service     INTEGER NOT NULL DEFAULT 0,"
          + " measure_unit   TEXT,"
          + " unit_price     REAL NOT NULL DEFAULT 0,"
          + " currency       TEXT NOT NULL DEFAULT 'UGX',"
          + " vat_category   TEXT NOT NULL,"
          + " vat_rate       REAL NOT NULL,"
          + " barcode        TEXT,"
          + " brand          TEXT,"
          + " specification  TEXT,"
          + " package_unit   TEXT,"
          + " package_qty    INTEGER,"
          + " stock_qty      REAL DEFAULT 0,"
          + " reorder_level  REAL DEFAULT 0,"
          + " active         INTEGER NOT NULL DEFAULT 1,"
          + " created_at     TEXT DEFAULT CURRENT_TIMESTAMP"
          + ");";
        final String idxSku = "CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);";
        final String idxCommodity = "CREATE INDEX IF NOT EXISTS idx_products_commodity ON products(commodity_code);";

        try (Connection c = Db.get(); Statement st = c.createStatement()) {
            st.execute(products);
            st.execute(idxSku);
            st.execute(idxCommodity);
        } catch (SQLException e) {
            throw new RuntimeException("DB migration failed", e);
        }
    }
}
