/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.ProductDAO;
import com.promedia.sentepos.dao.ProductDAO.ProductRow;
import com.promedia.sentepos.dao.StockDAO;

import javax.swing.*;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Adjust stock dialog (Design-view friendly).
 */
public class AdjustStockDialog extends javax.swing.JDialog {

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(AdjustStockDialog.class.getName());

    // --- state ---
    private Long selectedProductId = null;
    private Double currentStock = 0.0;

    public AdjustStockDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
     initComponents();

        setTitle("SentePOS — Adjust Stock");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(parent);

        // Defaults / placeholders
        txtQty.setText("1");
        txtNote.setText("");
        lblName.setText("No product selected");
        lblCurrent.setText("—");
        btnFind.setText("Find");
        btnSave.setText("Save");
        btnCancel.setText("Cancel");
        cmbMode.setModel(new DefaultComboBoxModel<>(new String[]{"Add","Remove","Set"}));

        // Events
        btnFind.addActionListener(e -> onFind());
        txtScan.addActionListener(e -> onFind());  // Enter in scan box
        btnSave.addActionListener(e -> onSave());
        btnCancel.addActionListener(e -> dispose());
    }

    // Prefill the scan field with SKU or Name and (optionally) auto-search. */
  public void setInitialQuery(String q) {
    final String val = (q == null) ? "" : q;
    SwingUtilities.invokeLater(() -> {
        txtScan.setText(val);
        txtScan.selectAll();
        txtScan.requestFocusInWindow();
        if (!val.isBlank()) onFind();  // auto-select product & fill labels
    });
}

    // ---------------------- Logic ----------------------

    private void onFind() {
        String q = txtScan.getText().trim();
        if (q.isEmpty()) return;

        try {
            ProductRow p = ProductDAO.findBySkuOrBarcode(q);
            if (p != null) { setProduct(p); return; }

            List<ProductRow> list = ProductDAO.searchByNameOrSku(q);
            if (list.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No product found.");
                return;
            }
            if (list.size() == 1) { setProduct(list.get(0)); return; }

            ProductRow choice = chooseProduct(list);
            if (choice != null) setProduct(choice);

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Search failed: " + e.getMessage());
        }
    }

    private void setProduct(ProductRow p) {
        selectedProductId = p.id;
        currentStock = p.stockQty != null ? p.stockQty : 0.0;
        lblName.setText(p.itemName + (p.sku != null ? " (" + p.sku + ")" : ""));
        lblCurrent.setText(String.format("%,.2f", currentStock));
        txtQty.requestFocus();
    }

    private ProductRow chooseProduct(List<ProductRow> list) {
        String[] names = list.stream()
                .map(r -> r.itemName + (r.sku != null ? " [" + r.sku + "]" : ""))
                .toArray(String[]::new);

        String sel = (String) JOptionPane.showInputDialog(
                this, "Select product:", "Matches",
                JOptionPane.QUESTION_MESSAGE, null, names, names[0]
        );
        if (sel == null) return null;
        int idx = Arrays.asList(names).indexOf(sel);
        return list.get(idx);
    }

    private void onSave() {
        if (selectedProductId == null) {
            JOptionPane.showMessageDialog(this, "Find/select a product first.");
            return;
        }
        String mode = (String) cmbMode.getSelectedItem();
        double qty;
        try {
            qty = Double.parseDouble(txtQty.getText().trim());
            if (qty <= 0) throw new IllegalArgumentException();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Quantity must be > 0");
            txtQty.requestFocus();
            return;
        }
        String note = txtNote.getText().trim();

        try {
            switch (mode) {
                case "Add" -> StockDAO.adjust(selectedProductId, +qty, note);
                case "Remove" -> StockDAO.adjust(selectedProductId, -qty, note);
                case "Set" -> StockDAO.setAbsolute(selectedProductId, qty, note);
                default -> throw new IllegalStateException("Unknown mode: " + mode);
            }
            JOptionPane.showMessageDialog(this, "Stock updated.");
            dispose();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Update failed: " + e.getMessage());
        }
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // </editor-fold>
    
@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        txtScan = new javax.swing.JTextField();
        txtQty = new javax.swing.JTextField();
        txtNote = new javax.swing.JTextField();
        btnFind = new javax.swing.JButton();
        lblName = new javax.swing.JLabel();
        lblCurrent = new javax.swing.JLabel();
        btnSave = new javax.swing.JButton();
        btnCancel = new javax.swing.JButton();
        cmbMode = new javax.swing.JComboBox<>();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        txtScan.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtScanActionPerformed(evt);
            }
        });

        txtNote.setText("txtNote");

        btnFind.setText("btnFind");

        lblCurrent.setText("lblCurrent");

        btnSave.setText("btnSave");

        btnCancel.setText("btnCancel");

        cmbMode.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Add", "Remove", "Set" }));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(44, 44, 44)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtNote, javax.swing.GroupLayout.PREFERRED_SIZE, 301, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(txtScan, javax.swing.GroupLayout.DEFAULT_SIZE, 301, Short.MAX_VALUE)
                            .addComponent(lblName, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(txtQty, javax.swing.GroupLayout.PREFERRED_SIZE, 148, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(34, 34, 34)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblCurrent, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(btnFind, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cmbMode, javax.swing.GroupLayout.PREFERRED_SIZE, 148, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(btnSave, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(48, 48, 48)
                        .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(138, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(68, 68, 68)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtScan, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnFind, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(lblName, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(14, 14, 14)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblCurrent, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtQty, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 38, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtNote, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cmbMode, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(74, 74, 74)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnSave, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(66, 66, 66))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void txtScanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtScanActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtScanActionPerformed

  

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnFind;
    private javax.swing.JButton btnSave;
    private javax.swing.JComboBox<String> cmbMode;
    private javax.swing.JLabel lblCurrent;
    private javax.swing.JLabel lblName;
    private javax.swing.JTextField txtNote;
    private javax.swing.JTextField txtQty;
    private javax.swing.JTextField txtScan;
    // End of variables declaration//GEN-END:variables

}