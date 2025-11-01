package com.promedia.sentepos.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Migrations {
    private Migrations(){}

    public static void ensure() {
        // --- PRODUCTS ---
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

        // --- PAYMENTS ---
        final String payments =
            "CREATE TABLE IF NOT EXISTS payments ("
          + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + " sale_id INTEGER NOT NULL,"
          + " method TEXT NOT NULL,"            // CASH | MOBILE | CARD
          + " amount REAL NOT NULL,"
          + " reference TEXT,"
          + " FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE"
          + ");";

        // --- STOCK MOVES ---
        final String stockMoves =
            "CREATE TABLE IF NOT EXISTS stock_moves ("
          + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + " product_id INTEGER NOT NULL,"
          + " date_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
          + " qty_delta REAL NOT NULL,"       // +ve add, -ve remove
          + " note TEXT,"
          + " FOREIGN KEY (product_id) REFERENCES products(id)"
          + ");";

        // --- BUSINESS (Company/Shop Setup) ---
        final String business =
            "CREATE TABLE IF NOT EXISTS business ("
          + " id               INTEGER PRIMARY KEY AUTOINCREMENT,"
          + " name             TEXT NOT NULL,"
          + " tin              TEXT,"
          + " branch_code      TEXT,"
          + " address_line     TEXT,"
          + " city             TEXT,"
          + " country          TEXT DEFAULT 'Uganda',"
          + " phone            TEXT,"
          + " email            TEXT,"
          + " currency         TEXT DEFAULT 'UGX',"
          + " vat_rate         REAL DEFAULT 18.0,"
          + " efris_device_no  TEXT,"
          + " efris_username   TEXT,"
          + " efris_password   TEXT,"          // TODO: encrypt/obfuscate before prod
          + " efris_branch_id  TEXT,"
          + " created_at       TEXT DEFAULT (datetime('now')),"
          + " updated_at       TEXT"
          + ");";

        
        final String trigBusiness =
            "CREATE TRIGGER trg_business_set_updated_at "
          + "AFTER UPDATE ON business "
          + "FOR EACH ROW "
          + "BEGIN "
          + "  UPDATE business SET updated_at = datetime('now') WHERE id = NEW.id;"
          + "END;";

        // Optional seed: create one row if empty (so UI can load/edit)
        final String seedBusiness =
            "INSERT INTO business("
          + "  name, tin, branch_code, address_line, city, country, phone, email, currency, vat_rate,"
          + "  efris_device_no, efris_username, efris_password, efris_branch_id, created_at, updated_at"
          + ") "
          + "SELECT "
          + "  'Your Business Name', NULL, NULL, NULL, NULL, 'Uganda', NULL, NULL, 'UGX', 18.0,"
          + "  NULL, NULL, NULL, NULL, datetime('now'), NULL "
          + "WHERE NOT EXISTS (SELECT 1 FROM business);";
        
        // --- EFRIS INVOICES (fiscalisation tracking) ---
        final String efrisInvoices =
            "CREATE TABLE IF NOT EXISTS efris_invoices ("
          + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + " sale_id INTEGER NOT NULL UNIQUE,"
          + " status TEXT NOT NULL DEFAULT 'PENDING',"      // -- PENDING | SENT | FAILED
          + " request_json TEXT,"                           //  -- payload we sent
          + " response_json TEXT,"                         //   -- raw response
          + " invoice_number TEXT,"                        //   -- e.g., IRN / invoiceNo from EFRIS
          + " qr_base64 TEXT,"                             //   -- base64 image if returned
          + " error_message TEXT,"
          + " created_at TEXT DEFAULT (datetime('now')),"
          + " sent_at TEXT,"
          + " FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE"
          + ");";
        
        // --- CREDIT NOTES (head) ---
        final String creditNotes =
            "CREATE TABLE IF NOT EXISTS credit_notes ("
          + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + " sale_id INTEGER NOT NULL,"                    /* reference original sale */
          + " reason TEXT,"
          + " date_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
          + " subtotal REAL NOT NULL,"
          + " vat_total REAL NOT NULL,"
          + " total REAL NOT NULL,"                         /* positive number; printer will display with minus sign */
          + " status TEXT NOT NULL DEFAULT 'DRAFT',"        /* DRAFT | PENDING | SENT | FAILED | CANCELLED */
          + " note TEXT,"
          + " created_at TEXT DEFAULT (datetime('now')),"
          + " FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE"
          + ");";

        final String creditNoteItems =
            "CREATE TABLE IF NOT EXISTS credit_note_items ("
          + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + " credit_note_id INTEGER NOT NULL,"
          + " product_id INTEGER NOT NULL,"
          + " item_name TEXT NOT NULL,"
          + " sku TEXT,"
          + " qty REAL NOT NULL,"                           /* quantity being returned */
          + " unit_price REAL NOT NULL,"
          + " vat_rate REAL NOT NULL,"
          + " line_total REAL NOT NULL,"                    /* qty * unit_price */
          + " vat_amount REAL NOT NULL,"                    /* line_total * (vat_rate/100) */
          + " FOREIGN KEY (credit_note_id) REFERENCES credit_notes(id) ON DELETE CASCADE,"
          + " FOREIGN KEY (product_id) REFERENCES products(id)"
          + ");";

        /* EFRIS tracking for credit notes (separate table to avoid altering efris_invoices) */
        final String efrisCreditNotes =
            "CREATE TABLE IF NOT EXISTS efris_credit_notes ("
          + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + " credit_note_id INTEGER NOT NULL UNIQUE,"
          + " status TEXT NOT NULL DEFAULT 'PENDING',"        /* PENDING | SENT | FAILED | CANCELLED */
          + " request_json TEXT,"
          + " response_json TEXT,"
          + " invoice_number TEXT,"                           /* EFRIS CN number */
          + " qr_base64 TEXT,"
          + " verification_code TEXT,"
          + " error_message TEXT,"
          + " created_at TEXT DEFAULT (datetime('now')),"
          + " sent_at TEXT,"
          + " FOREIGN KEY (credit_note_id) REFERENCES credit_notes(id) ON DELETE CASCADE"
          + ");";

        final String idxCnSale = "CREATE INDEX IF NOT EXISTS idx_credit_notes_sale ON credit_notes(sale_id);";
        final String idxCnStatus = "CREATE INDEX IF NOT EXISTS idx_efris_credit_notes_status ON efris_credit_notes(status);";
        
        final String idxBusinessTin   = "CREATE INDEX IF NOT EXISTS idx_business_tin ON business(tin);";
        final String idxBusinessName  = "CREATE INDEX IF NOT EXISTS idx_business_name ON business(name);";

        // Trigger to keep updated_at fresh
        final String dropTrigBusiness = "DROP TRIGGER IF EXISTS trg_business_set_updated_at;";
        final String idxEfrisStatus = "CREATE INDEX IF NOT EXISTS idx_efris_status ON efris_invoices(status);";


        try (Connection c = Db.get(); Statement st = c.createStatement()) {
            // Core tables
            st.execute(products);
            st.execute(idxSku);
            st.execute(idxCommodity);

            st.execute(sales);
            st.execute(saleItems);
            st.execute(payments);
            st.execute(stockMoves);

            // Business table + indexes + trigger + seed
            st.execute(business);
            st.execute(idxBusinessTin);
            st.execute(idxBusinessName);
            st.execute(dropTrigBusiness);
            st.execute(trigBusiness);
            st.execute(seedBusiness);
            st.execute(efrisInvoices);
            st.execute(idxEfrisStatus);
            st.execute(creditNotes);
            st.execute(creditNoteItems);
            st.execute(efrisCreditNotes);
            st.execute(idxCnSale);
            st.execute(idxCnStatus);
            
        } catch (SQLException e) {
            throw new RuntimeException("DB migration failed", e);
        }
    }
}