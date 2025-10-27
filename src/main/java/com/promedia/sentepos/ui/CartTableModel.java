package com.promedia.sentepos.ui;

import com.promedia.sentepos.model.SaleItem;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class CartTableModel extends AbstractTableModel {
    private final String[] cols = {"Item","Qty","Price","VAT%","Line","VAT"};
    private final List<SaleItem> rows = new ArrayList<>();

    public void clear() { rows.clear(); fireTableDataChanged(); }
    public void add(SaleItem it){ rows.add(it); fireTableRowsInserted(rows.size()-1, rows.size()-1); }
    public SaleItem getAt(int r){ return rows.get(r); }
    public List<SaleItem> all(){ return rows; }
    public void removeAt(int r){ rows.remove(r); fireTableDataChanged(); }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int c){ return cols[c]; }

    @Override public Object getValueAt(int r, int c) {
        SaleItem it = rows.get(r);
        return switch (c) {
            case 0 -> it.itemName;
            case 1 -> it.qty;
            case 2 -> it.unitPrice;
            case 3 -> it.vatRate;
            case 4 -> it.lineTotal;
            case 5 -> it.vatAmount;
            default -> "";
        };
    }

    @Override public Class<?> getColumnClass(int c) {
        return switch (c) {
            case 1,2,3,4,5 -> Double.class;
            default -> String.class;
        };
    }
}