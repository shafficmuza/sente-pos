package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.ProductDAO;
import com.promedia.sentepos.dao.ProductDAO.ProductRow;
import com.promedia.sentepos.model.Product;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

public class ProductsListFrame extends JFrame {
    private final JCheckBox chkShowInactive = new JCheckBox("Show inactive");
    private final JTextField txtSearch = new JTextField(18);
    private final JButton btnRefresh = new JButton("Refresh");
    private final JButton btnAdd = new JButton("Add");
    private final JButton btnEdit = new JButton("Edit");
    private final JButton btnDeactivate = new JButton("Deactivate");
    private final JButton btnActivate = new JButton("Activate");
    // NEW: Adjust Stock button
    private final JButton btnAdjust = new JButton("Adjust Stock");

    private final JTable table = new JTable();
    private final ProductTableModel model = new ProductTableModel();

    public ProductsListFrame() {
        super("SentePOS — Products");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 520);
        setLocationByPlatform(true);

        // table + model
        table.setModel(model);
        table.setAutoCreateRowSorter(true);
        table.setDefaultRenderer(Object.class, new LowStockRenderer(model));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // sensible column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(50);   // ID
        table.getColumnModel().getColumn(1).setPreferredWidth(220);  // Name
        table.getColumnModel().getColumn(2).setPreferredWidth(110);  // SKU
        table.getColumnModel().getColumn(9).setPreferredWidth(80);   // Stock
        table.getColumnModel().getColumn(10).setPreferredWidth(80);  // Reorder

        // top bar
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Search (Name/SKU):"));
        top.add(txtSearch);
        top.add(chkShowInactive);
        top.add(btnRefresh);

        // right-side buttons
        JPanel right = new JPanel(new GridLayout(0, 1, 6, 6));
        right.add(btnAdd);
        right.add(btnEdit);
        right.add(btnAdjust);      // NEW
        right.add(btnActivate);
        right.add(btnDeactivate);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

        // listeners
        btnRefresh.addActionListener(e -> refresh());
        chkShowInactive.addActionListener(e -> refresh());
        txtSearch.addActionListener(e -> refresh());
        btnAdd.addActionListener(e -> onAdd());
        btnEdit.addActionListener(e -> onEdit());
        btnDeactivate.addActionListener(e -> onToggle(false));
        btnActivate.addActionListener(e -> onToggle(true));
        btnAdjust.addActionListener(e -> onAdjust()); // NEW

        refresh();
    }

    private void refresh() {
        try {
            List<ProductRow> data = ProductDAO.listAll(chkShowInactive.isSelected());
            String q = txtSearch.getText().trim().toLowerCase();
            if (!q.isEmpty()) {
                data = data.stream().filter(r ->
                        (r.itemName != null && r.itemName.toLowerCase().contains(q)) ||
                        (r.sku != null && r.sku.toLowerCase().contains(q))
                ).toList();
            }
            model.setData(data);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Load failed: " + ex.getMessage());
        }
    }

    private Long selectedIdOrNull() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        int modelRow = table.convertRowIndexToModel(row);
        return model.getAt(modelRow).id;
    }

    private void onAdd() {
        AddProductDialog dlg = new AddProductDialog(this);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        refresh();
    }

    private void onEdit() {
        Long id = selectedIdOrNull();
        if (id == null) { JOptionPane.showMessageDialog(this, "Select a product first."); return; }
        try {
            ProductRow r = ProductDAO.findById(id);
            if (r == null) { JOptionPane.showMessageDialog(this, "Not found."); return; }

            Product p = new Product(
                    r.itemName, r.sku, r.commodityCode, r.isService,
                    r.measureUnit, r.unitPrice != null ? r.unitPrice : 0, r.currency,
                    r.vatCategory, r.vatRate != null ? r.vatRate : 0,
                    r.barcode, r.brand, r.specification,
                    r.packageUnit, r.packageQty, r.stockQty, r.reorderLevel, r.active
            );

            EditProductDialog dlg = new EditProductDialog(this, id, p);
            dlg.setLocationRelativeTo(this);
            dlg.setVisible(true);
            refresh();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Load failed: " + ex.getMessage());
        }
    }

    private void onToggle(boolean activate) {
        Long id = selectedIdOrNull();
        if (id == null) { JOptionPane.showMessageDialog(this, "Select a product first."); return; }
        try {
            ProductDAO.setActive(id, activate);
            refresh();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Update failed: " + ex.getMessage());
        }
    }

    // NEW: Adjust Stock handler
    private void onAdjust() {
    Long id = selectedIdOrNull();
    if (id == null) { JOptionPane.showMessageDialog(this, "Select a product first."); return; }
    try {
        var row = ProductDAO.findById(id);
        AdjustStockDialog dlg = new AdjustStockDialog(this, true);

        String prefill = (row != null && row.sku != null && !row.sku.isBlank())
                ? row.sku
                : (row != null ? row.itemName : "");
        dlg.setInitialQuery(prefill);   // prefill first
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);           // then show
        refresh();
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Failed: " + ex.getMessage());
    }
}
}