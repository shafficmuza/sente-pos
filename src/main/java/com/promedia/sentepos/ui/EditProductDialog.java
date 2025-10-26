package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.ProductDAO;
import com.promedia.sentepos.model.Product;

import javax.swing.*;
import java.awt.*;

public class EditProductDialog extends JDialog {
    private final long productId;

    private final JTextField txtName = new JTextField(22);
    private final JTextField txtSku = new JTextField(14);
    private final JTextField txtCommodity = new JTextField(14);
    private final JCheckBox  chkService = new JCheckBox("Is Service?");
    private final JComboBox<String> cmbMeasure = new JComboBox<>(new String[]{"PCS","KGM","LTR","MTR","BOX","CTN","BAG","PKT"});
    private final JTextField txtUnitPrice = new JTextField(8);
    private final JComboBox<String> cmbCurrency = new JComboBox<>(new String[]{"UGX"});
    private final JComboBox<String> cmbVatCat = new JComboBox<>(new String[]{"STANDARD","ZERO","EXEMPT"});
    private final JTextField txtVatRate = new JTextField(4);
    private final JTextField txtBarcode = new JTextField(16);
    private final JTextField txtBrand = new JTextField(14);
    private final JTextField txtSpec = new JTextField(18);
    private final JComboBox<String> cmbPackUnit = new JComboBox<>(new String[]{"CTN","BOX","BAG","PKT"});
    private final JTextField txtPackQty = new JTextField(6);
    private final JTextField txtStockQty = new JTextField(6);
    private final JTextField txtReorder = new JTextField(6);
    private final JCheckBox chkActive = new JCheckBox("Active", true);

    public EditProductDialog(Frame owner, long id, Product p) {
        super(owner, "Edit Product #" + id, true);
        this.productId = id;
        setResizable(false);

        txtName.setText(s(p.getItemName()));
        txtSku.setText(s(p.getSku()));
        txtCommodity.setText(s(p.getCommodityCode()));
        chkService.setSelected(p.getIsService()==1);
        cmbMeasure.setSelectedItem(s(p.getMeasureUnit()));
        txtUnitPrice.setText(String.valueOf(p.getUnitPrice()));
        cmbCurrency.setSelectedItem(s(p.getCurrency()));
        cmbVatCat.setSelectedItem(s(p.getVatCategory()));
        txtVatRate.setText(String.valueOf(p.getVatRate()));
        txtBarcode.setText(s(p.getBarcode()));
        txtBrand.setText(s(p.getBrand()));
        txtSpec.setText(s(p.getSpecification()));
        cmbPackUnit.setSelectedItem(s(p.getPackageUnit()));
        if (p.getPackageQty()!=null) txtPackQty.setText(String.valueOf(p.getPackageQty()));
        if (p.getStockQty()!=null) txtStockQty.setText(String.valueOf(p.getStockQty()));
        if (p.getReorderLevel()!=null) txtReorder.setText(String.valueOf(p.getReorderLevel()));
        chkActive.setSelected(p.getActive()==1);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5,5,5,5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        int y=0;
        addRow(form, gc, y++, "Item Name *", txtName);
        addRow(form, gc, y++, "SKU", txtSku);
        addRow(form, gc, y++, "Commodity Code", txtCommodity);
        addRow(form, gc, y++, "Is Service?", chkService);
        addRow(form, gc, y++, "Measure Unit", cmbMeasure);
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
        JButton btnCancel = new JButton("Cancel");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(btnSave); actions.add(btnCancel);

        btnSave.addActionListener(e -> onSave());
        btnCancel.addActionListener(e -> dispose());

        getContentPane().setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
        pack();
    }

    private static void addRow(JPanel form, GridBagConstraints gc, int y, String label, JComponent comp){
        gc.gridx=0; gc.gridy=y; gc.weightx=0; form.add(new JLabel(label), gc);
        gc.gridx=1; gc.weightx=1; form.add(comp, gc);
    }
    private String s(String v){ return v==null? "" : v; }

    private void onSave() {
        try {
            String name = must(txtName.getText(), "Item Name");
            double unitPrice = parseDouble(txtUnitPrice.getText(), "Unit Price");
            String vatCat = must((String)cmbVatCat.getSelectedItem(), "VAT Category");
            double vat = parseDouble(txtVatRate.getText(), "VAT Rate");

            Product p = new Product(
                name,
                nullIfEmpty(txtSku.getText()),
                nullIfEmpty(txtCommodity.getText()),
                chkService.isSelected()? 1:0,
                (String)cmbMeasure.getSelectedItem(),
                unitPrice,
                (String)cmbCurrency.getSelectedItem(),
                vatCat,
                vat,
                nullIfEmpty(txtBarcode.getText()),
                nullIfEmpty(txtBrand.getText()),
                nullIfEmpty(txtSpec.getText()),
                (String)cmbPackUnit.getSelectedItem(),
                parseIntOrNull(txtPackQty.getText()),
                chkService.isSelected()? null : parseDoubleOrNull(txtStockQty.getText()),
                chkService.isSelected()? null : parseDoubleOrNull(txtReorder.getText()),
                chkActive.isSelected()? 1:0
            );

            int n = ProductDAO.update(productId, p);
            if (n>0) {
                JOptionPane.showMessageDialog(this, "Updated.");
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "No changes saved.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
        }
    }

    private static String must(String v, String label) {
        if (v==null || v.trim().isEmpty()) throw new IllegalArgumentException(label + " is required");
        return v.trim();
    }
    private static String nullIfEmpty(String v){ if (v==null) return null; String t=v.trim(); return t.isEmpty()? null:t; }
    private static Integer parseIntOrNull(String v){ if (v==null||v.trim().isEmpty()) return null; return Integer.parseInt(v.trim()); }
    private static Double parseDoubleOrNull(String v){ if (v==null||v.trim().isEmpty()) return null; return Double.parseDouble(v.trim()); }
    private static double parseDouble(String v, String label){
        try { return Double.parseDouble(v.trim()); } catch(Exception e){ throw new IllegalArgumentException(label+" must be a number"); }
    }
}
