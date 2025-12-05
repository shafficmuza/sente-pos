package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.ProductDAO;
import com.promedia.sentepos.dao.UomDAO;
import com.promedia.sentepos.model.Product;
import com.promedia.sentepos.model.Uom;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EditProductDialog extends JDialog {

    private final long productId;
    private final Product original;

    private final JTextField txtName      = new JTextField(22);
    private final JTextField txtSku       = new JTextField(14);
    private final JTextField txtCommodity = new JTextField(14);
    private final JCheckBox  chkService   = new JCheckBox("Is Service?");
    // UOM combo populated from dictionary (code stored, code+name displayed)
    private final JComboBox<UomItem> cmbMeasure = new JComboBox<>();
    private final JTextField txtUnitPrice = new JTextField(8);
    private final JComboBox<String> cmbCurrency = new JComboBox<>(new String[]{"UGX"});
    private final JComboBox<String> cmbVatCat   = new JComboBox<>(new String[]{"STANDARD","ZERO","EXEMPT"});
    private final JTextField txtVatRate  = new JTextField(4);
    private final JTextField txtBarcode  = new JTextField(16);
    private final JTextField txtBrand    = new JTextField(14);
    private final JTextField txtSpec     = new JTextField(18);
    private final JComboBox<String> cmbPackUnit = new JComboBox<>(new String[]{"CTN","BOX","BAG","PKT"});
    private final JTextField txtPackQty  = new JTextField(6);
    private final JTextField txtStockQty = new JTextField(6);
    private final JTextField txtReorder  = new JTextField(6);
    private final JCheckBox  chkActive   = new JCheckBox("Active");

    public EditProductDialog(Frame owner, long productId, Product existing) {
        super(owner, "Edit Product", true);
        this.productId = productId;
        this.original  = existing;

        setResizable(false);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5,5,5,5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        int y = 0;
        addRow(form, gc, y++, "Item Name *", txtName);
        addRow(form, gc, y++, "SKU", txtSku);
        addRow(form, gc, y++, "Commodity Code", txtCommodity);
        addRow(form, gc, y++, "Is Service?", chkService);
        addRow(form, gc, y++, "Measure Unit *", cmbMeasure);
        addRow(form, gc, y++, "Unit Price (UGX) *", txtUnitPrice);
        addRow(form, gc, y++, "Currency *", cmbCurrency);
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

        JButton btnSave  = new JButton("Save");
        JButton btnClose = new JButton("Close");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(btnSave);
        actions.add(btnClose);

        btnSave.addActionListener(e -> onSave());
        btnClose.addActionListener(e -> dispose());

        getContentPane().setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        // Populate UOM combo from dictionary + defaults
        initUoms(original != null ? original.getMeasureUnit() : null);

        // Fill existing values
        fillFromOriginal();

        pack();
    }

    private static void addRow(JPanel form, GridBagConstraints gc, int y, String label, JComponent comp) {
        gc.gridx = 0; gc.gridy = y; gc.weightx = 0;
        form.add(new JLabel(label), gc);
        gc.gridx = 1; gc.weightx = 1;
        form.add(comp, gc);
    }

    private void fillFromOriginal() {
        if (original == null) return;

        txtName.setText(nvl(original.getItemName()));
        txtSku.setText(nvl(original.getSku()));
        txtCommodity.setText(nvl(original.getCommodityCode()));
        chkService.setSelected(original.getIsService() == 1);

        txtUnitPrice.setText(String.valueOf(original.getUnitPrice()));
        cmbCurrency.setSelectedItem(nvl(original.getCurrency(), "UGX"));
        cmbVatCat.setSelectedItem(nvl(original.getVatCategory(), "STANDARD"));
        txtVatRate.setText(String.valueOf(original.getVatRate()));

        txtBarcode.setText(nvl(original.getBarcode()));
        txtBrand.setText(nvl(original.getBrand()));
        txtSpec.setText(nvl(original.getSpecification()));

        if (original.getPackageUnit() != null) {
            cmbPackUnit.setSelectedItem(original.getPackageUnit());
        }
        if (original.getPackageQty() != null) {
            txtPackQty.setText(String.valueOf(original.getPackageQty()));
        }
        if (original.getStockQty() != null) {
            txtStockQty.setText(String.valueOf(original.getStockQty()));
        }
        if (original.getReorderLevel() != null) {
            txtReorder.setText(String.valueOf(original.getReorderLevel()));
        }

        chkActive.setSelected(original.getActive() == 1);
    }

    // ---------------- UOM init (uses your UomDAO + Uom model) ----------------

    private void initUoms(String currentCode) {
        List<UomItem> items = new ArrayList<>();

        try {
            List<Uom> rows = UomDAO.listAll();
            if (rows != null && !rows.isEmpty()) {
                for (Uom u : rows) {
                    String code = u.getCode() != null ? u.getCode().trim() : "";
                    String name = u.getName() != null ? u.getName().trim() : "";
                    if (!code.isEmpty()) {
                        String label = name.isEmpty()
                                ? code
                                : code + " - " + name;
                        items.add(new UomItem(code, label));
                    }
                }
            }
        } catch (SQLException ex) {
            // If dictionary fails, we'll fall back below
        }

        if (items.isEmpty()) {
            // Fallback defaults – URA/EFRIS friendly codes
            items.add(new UomItem("PCE", "PCE - Piece"));
            items.add(new UomItem("PCS", "PCS - Pieces"));
            items.add(new UomItem("KGM", "KGM - Kilogram"));
            items.add(new UomItem("LTR", "LTR - Litre"));
            items.add(new UomItem("MTR", "MTR - Meter"));
            items.add(new UomItem("BOX", "BOX - Box"));
            items.add(new UomItem("CTN", "CTN - Carton"));
            items.add(new UomItem("BAG", "BAG - Bag"));
            items.add(new UomItem("PKT", "PKT - Packet"));
        }

        cmbMeasure.removeAllItems();
        for (UomItem it : items) {
            cmbMeasure.addItem(it);
        }

        // Select existing measureUnit (code) if available
        if (currentCode != null && !currentCode.isBlank()) {
            boolean matched = false;
            for (int i = 0; i < cmbMeasure.getItemCount(); i++) {
                UomItem it = cmbMeasure.getItemAt(i);
                if (it.code.equalsIgnoreCase(currentCode)) {
                    cmbMeasure.setSelectedIndex(i);
                    matched = true;
                    break;
                }
            }
            // If code not present in dictionary, add it as a synthetic entry
            if (!matched) {
                UomItem custom = new UomItem(currentCode, currentCode + " (custom)");
                cmbMeasure.addItem(custom);
                cmbMeasure.setSelectedItem(custom);
            }
        } else {
            // no existing code → default to first item
            if (cmbMeasure.getItemCount() > 0) {
                cmbMeasure.setSelectedIndex(0);
            }
        }
    }

    // ---------------- Save ----------------

    private void onSave() {
        try {
            String name = must(txtName.getText(), "Item Name");
            double unitPrice = parseDouble(txtUnitPrice.getText(), "Unit Price");
            String vatCat = must((String)cmbVatCat.getSelectedItem(), "VAT Category");
            double vat = parseDouble(txtVatRate.getText(), "VAT Rate");

            UomItem uom = (UomItem) cmbMeasure.getSelectedItem();
            String measureCode = (uom != null ? uom.code : null);

            Product p = new Product(
                name,
                emptyToNull(txtSku.getText()),
                emptyToNull(txtCommodity.getText()),
                chkService.isSelected() ? 1 : 0,
                measureCode,
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

            int updated = ProductDAO.update(productId, p);
            if (updated > 0) {
                JOptionPane.showMessageDialog(this, "Product updated.");
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "No rows updated (maybe not found?).");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
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

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String nvl(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    /** Combo item: show "CODE - Name" but keep CODE separately. */
    private static class UomItem {
        final String code;
        final String label;

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