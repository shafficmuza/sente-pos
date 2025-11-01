package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.ProductDAO.ProductRow;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ProductTableModel extends AbstractTableModel {
    // Added "Stock" and "Reorder"
    private final String[] cols = {
        "ID","Name","SKU","Unit","Price","VAT Cat","VAT %","Service","Active","Stock","Reorder"
    };
    private final List<ProductRow> rows = new ArrayList<>();

    public void setData(List<ProductRow> data) {
        rows.clear();
        rows.addAll(data);
        fireTableDataChanged();
    }

    public ProductRow getAt(int row) { return rows.get(row); }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int col){ return cols[col]; }

    @Override
    public Object getValueAt(int r, int c) {
        ProductRow p = rows.get(r);
        return switch (c) {
            case 0 -> p.id;
            case 1 -> p.itemName;
            case 2 -> p.sku;
            case 3 -> p.measureUnit;
            case 4 -> p.unitPrice;
            case 5 -> p.vatCategory;
            case 6 -> p.vatRate;
            case 7 -> p.isService==1 ? "Yes" : "No";
            case 8 -> p.active==1 ? "Yes" : "No";
            case 9 -> p.stockQty;       // may be null for services
            case 10 -> p.reorderLevel;  // may be null
            default -> "";
        };
    }

    @Override
    public Class<?> getColumnClass(int c) {
        return switch (c) {
            case 0 -> Long.class;
            case 4,6,9,10 -> Double.class;
            default -> String.class;
        };
    }
}