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
        
        
        // --- SALES HEAD ---
final String sales =
    "CREATE TABLE IF NOT EXISTS sales ("
  + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
  + " receipt_no TEXT UNIQUE,"
  + " date_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
  + " subtotal REAL NOT NULL,"
  + " vat_total REAL NOT NULL,"
  + " total REAL NOT NULL,"
  + " paid REAL NOT NULL DEFAULT 0,"
  + " change_due REAL NOT NULL DEFAULT 0,"
  + " note TEXT"
  + ");";

// --- SALES ITEMS ---
final String saleItems =
    "CREATE TABLE IF NOT EXISTS sale_items ("
  + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
  + " sale_id INTEGER NOT NULL,"
  + " product_id INTEGER NOT NULL,"
  + " item_name TEXT NOT NULL,"
  + " sku TEXT,"
  + " qty REAL NOT NULL,"
  + " unit_price REAL NOT NULL,"
  + " vat_rate REAL NOT NULL,"
  + " line_total REAL NOT NULL,"        // qty * unit_price
  + " vat_amount REAL NOT NULL,"        // line_total * vat_rate/100
  + " FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,"
  + " FOREIGN KEY (product_id) REFERENCES products(id)"
  + ");";

final String payments =
    "CREATE TABLE IF NOT EXISTS payments ("
  + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
  + " sale_id INTEGER NOT NULL,"
  + " method TEXT NOT NULL,"            // CASH | MOBILE | CARD
  + " amount REAL NOT NULL,"
  + " reference TEXT,"
  + " FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE"
  + ");";

final String stockMoves =
    "CREATE TABLE IF NOT EXISTS stock_moves ("
  + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
  + " product_id INTEGER NOT NULL,"
  + " date_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
  + " qty_delta REAL NOT NULL,"       // -- +ve for add, -ve for remove
  + " note TEXT,"
  + " FOREIGN KEY (product_id) REFERENCES products(id)"
  + ");";


        final String idxSku = "CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);";
        final String idxCommodity = "CREATE INDEX IF NOT EXISTS idx_products_commodity ON products(commodity_code);";
        


        try (Connection c = Db.get(); Statement st = c.createStatement()) {
            st.execute(products);
            st.execute(idxSku);
            st.execute(idxCommodity);
            st.execute(sales);
            st.execute(saleItems);
            st.execute(payments);
            st.execute(stockMoves);
        } catch (SQLException e) {
            throw new RuntimeException("DB migration failed", e);
        }
    }
}
