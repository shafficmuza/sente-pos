package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.ProductDAO;
import com.promedia.sentepos.dao.UomDAO;
import com.promedia.sentepos.model.Product;
import com.promedia.sentepos.model.Uom;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

public class AddProductDialog extends JDialog {
    private final JTextField txtName = new JTextField(22);
    private final JTextField txtSku = new JTextField(14);
    private final JTextField txtCommodity = new JTextField(14);
    private final JCheckBox  chkService = new JCheckBox("Is Service?");

    // UOM combo – same behaviour as EditProduct (load from UomDAO, store code)
    private final JComboBox<UomItem> cmbMeasure = new JComboBox<>();

    private final JTextField txtUnitPrice = new JTextField("0", 8);
    private final JComboBox<String> cmbCurrency = new JComboBox<>(new String[]{"UGX"});
    private final JComboBox<String> cmbVatCat = new JComboBox<>(new String[]{"STANDARD","ZERO","EXEMPT"});
    private final JTextField txtVatRate = new JTextField("18", 4);
    private final JTextField txtBarcode = new JTextField(16);
    private final JTextField txtBrand = new JTextField(14);
    private final JTextField txtSpec = new JTextField(18);
    private final JComboBox<String> cmbPackUnit = new JComboBox<>(new String[]{"CTN","BOX","BAG","PKT"});
    private final JTextField txtPackQty = new JTextField(6);
    private final JTextField txtStockQty = new JTextField(6);
    private final JTextField txtReorder = new JTextField(6);
    private final JCheckBox  chkActive = new JCheckBox("Active", true);

    public AddProductDialog(Frame owner) {
        super(owner, "Add Product", true);
        setResizable(false);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5,5,5,5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        int y=0;
        addRow(form, gc, y++, "Item Name *", txtName);
        addRow(form, gc, y++, "SKU", txtSku);
        addRow(form, gc, y++, "Commodity Code", txtCommodity);
        addRow(form, gc, y++, "Is Service?", chkService);
        addRow(form, gc, y++, "Measure Unit *", cmbMeasure);
        addRow(form, gc, y++, "Unit Price (UGX) *", txtUnitPrice);
        addRow(form, gc, y++, "Currency", cmbCurrency);
        addRow(form, gc, y++, "VAT Category *", cmbVatCat);
        addRow(form, gc, y++, "VAT Rate *", txtVatRate);
        addRow(form, gc, y++, "Barcode", txtBarcode);
        addRow(form, gc, y++, "Brand", txtBrand);
        addRow(form, gc, y++, "Specification", txtSpec);
        addRow(form, gc, y++, "Package Unit", cmbPackUnit);
        addRow(form, gc, y++, "Package Qty", txtPackQty);
        addRow(form, gc, y++, "Stock Qty", txtStockQty);
        addRow(form, gc, y++, "Reorder Level", txtReorder);
        addRow(form, gc, y++, "Status", chkActive);

        JButton btnSave = new JButton("Save");
        JButton btnClose = new JButton("Close");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(btnSave);
        actions.add(btnClose);

        btnSave.addActionListener(e -> onSave());
        btnClose.addActionListener(e -> dispose());

        getContentPane().setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        // Load UOMs from dictionary (no preselect when adding)
        loadUoms(null);

        pack();
        setLocationRelativeTo(owner);
    }

    private static void addRow(JPanel form, GridBagConstraints gc, int y, String label, JComponent comp){
        gc.gridx = 0;
        gc.gridy = y;
        gc.weightx = 0;
        form.add(new JLabel(label), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        form.add(comp, gc);
    }

    // ---------------- UOM logic (aligned with EditProduct) ----------------

    private void loadUoms(String preselectCode) {
        cmbMeasure.removeAllItems();
        boolean any = false;

        try {
            List<Uom> list = UomDAO.listAll();
            if (list != null && !list.isEmpty()) {
                for (Uom u : list) {
                    String code = trimOrNull(u.getCode());
                    if (code == null || code.isEmpty()) continue;

                    String name = trimOrNull(u.getName());
                    String desc = trimOrNull(u.getDescription());

                    String label;
                    if (name != null && !name.isEmpty()) {
                        label = code + " - " + name;
                    } else if (desc != null && !desc.isEmpty()) {
                        label = code + " - " + desc;
                    } else {
                        label = code;
                    }

                    UomItem item = new UomItem(code, label);
                    cmbMeasure.addItem(item);
                    any = true;
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load Unit of Measure list from EFRIS dictionary.\n" +
                    "Using basic defaults for now.\nError: " + ex.getMessage(),
                    "UOM Warning",
                    JOptionPane.WARNING_MESSAGE);
        }

        // If nothing from DB, fall back to sensible defaults (codes only)
        if (!any) {
            UomItem[] defaults = new UomItem[] {
                    new UomItem("PCE", "PCE - Piece"),
                    new UomItem("KGM", "KGM - Kilogram"),
                    new UomItem("LTR", "LTR - Litre"),
                    new UomItem("MTR", "MTR - Metre"),
                    new UomItem("BAG", "BAG - Bag"),
                    new UomItem("BOX", "BOX - Box"),
                    new UomItem("CTN", "CTN - Carton"),
                    new UomItem("PKT", "PKT - Packet")
            };
            for (UomItem it : defaults) {
                cmbMeasure.addItem(it);
            }
        }

        // Default selection
        if (cmbMeasure.getItemCount() > 0) {
            // try to default to PCE if it exists, else first
            for (int i = 0; i < cmbMeasure.getItemCount(); i++) {
                UomItem it = cmbMeasure.getItemAt(i);
                if ("PCE".equalsIgnoreCase(it.code) || "PCS".equalsIgnoreCase(it.code)) {
                    cmbMeasure.setSelectedIndex(i);
                    return;
                }
            }
            cmbMeasure.setSelectedIndex(0);
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ---------------- Save ----------------

    private void onSave() {
        try {
            String name = must(txtName.getText(), "Item Name");
            double unitPrice = parseDouble(txtUnitPrice.getText(), "Unit Price");
            String vatCat = must((String)cmbVatCat.getSelectedItem(), "VAT Category");
            double vat = parseDouble(txtVatRate.getText(), "VAT Rate");

            UomItem uomItem = (UomItem) cmbMeasure.getSelectedItem();
            String uomCode = (uomItem != null) ? uomItem.code : null;
            if (uomCode == null || uomCode.trim().isEmpty()) {
                throw new IllegalArgumentException("Measure Unit is required");
            }

            Product p = new Product(
                    name,
                    emptyToNull(txtSku.getText()),
                    emptyToNull(txtCommodity.getText()),
                    chkService.isSelected() ? 1 : 0,
                    uomCode, // store EFRIS UOM code only
                    unitPrice,
                    (String)cmbCurrency.getSelectedItem(),
                    vatCat,
                    vat,
                    emptyToNull(txtBarcode.getText()),
                    emptyToNull(txtBrand.getText()),
                    emptyToNull(txtSpec.getText()),
                    (String)cmbPackUnit.getSelectedItem(),
                    parseIntOrNull(txtPackQty.getText()),
                    chkService.isSelected()? null : parseDoubleOrNull(txtStockQty.getText()),
                    chkService.isSelected()? null : parseDoubleOrNull(txtReorder.getText()),
                    chkActive.isSelected()? 1 : 0
            );

            long id = ProductDAO.insert(p);
            JOptionPane.showMessageDialog(this, "Saved! Product ID = " + id);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Save failed: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------- Helpers ----------------

    private static String must(String v, String label) {
        if (v == null || v.trim().isEmpty())
            throw new IllegalArgumentException(label + " is required");
        return v.trim();
    }

    private static String emptyToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty()? null : t;
    }

    private static Double parseDoubleOrNull(String v) {
        if (v == null || v.trim().isEmpty()) return null;
        return Double.parseDouble(v.trim());
    }

    private static Integer parseIntOrNull(String v) {
        if (v == null || v.trim().isEmpty()) return null;
        return Integer.parseInt(v.trim());
    }

    private static double parseDouble(String v, String label) {
        try { return Double.parseDouble(v.trim()); }
        catch (Exception e) { throw new IllegalArgumentException(label + " must be a number"); }
    }

    /** Combo-box item: holds code, shows label */
    private static class UomItem {
        final String code;   // EFRIS code, e.g. PCE
        final String label;  // Display text, e.g. "PCE - Piece"

        UomItem(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}