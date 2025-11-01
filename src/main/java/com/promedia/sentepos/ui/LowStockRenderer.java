package com.promedia.sentepos.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class LowStockRenderer extends DefaultTableCellRenderer {
    private final ProductTableModel model;

    public LowStockRenderer(ProductTableModel model) {
        this.model = model;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        // Reset colors first
        if (!isSelected) {
            c.setForeground(Color.BLACK);
            c.setBackground(Color.WHITE);
        }

        int modelRow = table.convertRowIndexToModel(row);
        var p = model.getAt(modelRow);

        // Only highlight for goods (not services), when both stock & reorder exist
        if (p != null && p.isService == 0 && p.stockQty != null && p.reorderLevel != null) {
            boolean low = p.stockQty <= p.reorderLevel;
            if (low) {
                if (!isSelected) {
                    c.setBackground(new Color(255, 235, 238)); // light red
                    c.setForeground(new Color(183, 28, 28));   // dark red text
                }
            }
        }

        // Render "—" for null numeric values in Stock/Reorder columns
        if ((column == 9 || column == 10) && value == null) {
            setText("—");
        }

        return c;
    }
}