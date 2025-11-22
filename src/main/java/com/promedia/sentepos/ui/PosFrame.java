/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.promedia.sentepos.ui;
import com.promedia.sentepos.dao.ProductDAO.ProductRow;
import com.promedia.sentepos.model.Payment;
import com.promedia.sentepos.model.Sale;
import com.promedia.sentepos.model.SaleItem;
import com.promedia.sentepos.print.ReceiptPrinter;
import com.promedia.sentepos.service.PosService;
import com.promedia.sentepos.service.FiscalService;
import com.promedia.sentepos.dao.EfrisDAO;
import java.awt.Color;

import javax.swing.*;
import java.sql.SQLException;



/**
 *
 * @author shaffic
 */
public class PosFrame extends javax.swing.JFrame {
    
    private CartTableModel cartModel = new CartTableModel();
    private Sale currentSale = new Sale();
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(PosFrame.class.getName());

    /**
     * Creates new form PosFrame
     */
    public PosFrame() {
        initComponents();
   
        tblCart.setModel(cartModel);
        txtQty.setText("1");
        refreshTotals();
        btnRemove.setOpaque(true);
        btnRemove.setBackground(Color.red);
        
            }
    
        
    private void refreshTotals() {
    double subtotal = cartModel.all().stream().mapToDouble(it -> it.lineTotal).sum();
    double vat = cartModel.all().stream().mapToDouble(it -> it.vatAmount).sum();
    double total = subtotal + vat;
    lblSubtotal.setText(String.format("Amount UGX:  %, .0f", subtotal));
    lblVat.setText(String.format("Vat UGX:  %, .0f", vat));
    lblTotal.setText(String.format("Total UGX:  %, .0f", total));
    
}

private double parseQty() {
    try {
        double q = Double.parseDouble(txtQty.getText().trim());
        if (q <= 0) throw new IllegalArgumentException();
        return q;
    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Quantity must be > 0");
        txtQty.requestFocus();
        throw new RuntimeException("invalid qty");
    }
}

private void doAdd() {
    String code = txtScan.getText().trim();
    if (code.isEmpty()) return;
    double qty = parseQty();
    try {
        ProductRow p = PosService.lookupProduct(code);
        if (p == null) {
            JOptionPane.showMessageDialog(this, "Product not found: " + code);
            return;
        }
        SaleItem it = PosService.makeItemFromProduct(p, qty);
        cartModel.add(it);
        txtScan.setText("");
        txtScan.requestFocus();
        refreshTotals();
    } catch (SQLException ex) {
        JOptionPane.showMessageDialog(this, "Lookup failed: " + ex.getMessage());
    }
}

private void doRemoveSelected() {
    int row = tblCart.getSelectedRow();
    if (row < 0) { JOptionPane.showMessageDialog(this, "Select a row to remove."); return; }
    int modelRow = tblCart.convertRowIndexToModel(row);
    cartModel.removeAt(modelRow);
    refreshTotals();
}

private void doClear() {
    cartModel.clear();
    refreshTotals();
}

private void doPayCash() {
    double total = cartModel.all().stream().mapToDouble(it -> it.lineTotal + it.vatAmount).sum();
    String s = JOptionPane.showInputDialog(this, "Cash received (UGX):", String.format("%.0f", total));
    if (s == null) return;
    try {
        double paid = Double.parseDouble(s.trim());
        if (paid < total) {
            JOptionPane.showMessageDialog(this, "Amount is less than total.");
            return;
        }
        currentSale.paid = paid; // store temporarily
        JOptionPane.showMessageDialog(this, String.format("Change: UGX %, .0f", (paid - total)));
        // Finish the transaction
        doFinish();
    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Invalid amount.");
    }
}

private void doFinish() {
    if (cartModel.all().isEmpty()) {
        JOptionPane.showMessageDialog(this, "Cart is empty.");
        return;
    }

    // 1) Build sale from cart
    currentSale.items.clear();
    currentSale.items.addAll(cartModel.all());

    Payment p = new Payment();
    p.method = Payment.Method.CASH; // or selected method
    p.amount = (currentSale.paid > 0)
            ? currentSale.paid
            : currentSale.items.stream().mapToDouble(it -> it.lineTotal + it.vatAmount).sum();

    // 2) Save ONCE
    final long saleId;
    try {
        saleId = PosService.saveSale(currentSale, p);
    } catch (SQLException ex) {
        JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
        return;
    }

    // 3) Fiscalise (don’t block the sale if it fails)
    try {
        String irn = FiscalService.fiscalise(saleId, currentSale, p);
        if (irn != null && !irn.isBlank()) {
            System.out.println("Fiscalised IRN: " + irn);
        }
    } catch (Exception fx) {
        JOptionPane.showMessageDialog(this,
            "Sale saved, but fiscalisation did not complete.\n" +
            "Reason: " + fx.getMessage() + "\nYou can retry from Sales List.");
    }

    // 4) Print PREVIEW (always show preview AFTER fiscalisation so QR/IRN can appear)
    try {
        ReceiptPrinter rp = ReceiptPrinter.usingBusinessFromDb(currentSale, p, saleId);
        rp.preview(this); // modal dialog; user can zoom/inspect

        // 5) Optional: print after preview
        if (chkPrint.isSelected()) {
            try {
                rp.print(null); // or rp.print("EPSON")
            } catch (java.awt.print.PrinterException pe) {
                JOptionPane.showMessageDialog(this, "Print failed: " + pe.getMessage());
            }
        }
    } catch (Exception anyPreviewIssue) {
        // Preview should rarely fail, but don’t break the cashier flow
        JOptionPane.showMessageDialog(this, "Preview unavailable: " + anyPreviewIssue.getMessage());
    }

    // 6) Notify + reset for next sale
    JOptionPane.showMessageDialog(this, "Sale saved. ID = " + saleId);
    currentSale = new Sale();
    cartModel.clear();
    txtScan.requestFocus();
    refreshTotals();
}

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        txtScan = new javax.swing.JTextField();
        txtQty = new javax.swing.JTextField();
        btnAdd = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        tblCart = new javax.swing.JTable();
        lblSubtotal = new javax.swing.JLabel();
        lblVat = new javax.swing.JLabel();
        lblTotal = new javax.swing.JLabel();
        btnRemove = new javax.swing.JButton();
        btnClear = new javax.swing.JButton();
        btnPayCash = new javax.swing.JButton();
        btnCancel = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        chkPrint = new javax.swing.JCheckBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("POS Screen");
        setBackground(new java.awt.Color(0, 153, 255));

        txtScan.setFont(new java.awt.Font("sansserif", 0, 18)); // NOI18N
        txtScan.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtScanActionPerformed(evt);
            }
        });

        txtQty.setFont(new java.awt.Font("sansserif", 0, 18)); // NOI18N
        txtQty.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtQty.setText("0");
        txtQty.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtQtyActionPerformed(evt);
            }
        });

        btnAdd.setFont(new java.awt.Font("sansserif", 0, 18)); // NOI18N
        btnAdd.setText("Add");
        btnAdd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddActionPerformed(evt);
            }
        });

        tblCart.setFont(new java.awt.Font("sansserif", 0, 16)); // NOI18N
        tblCart.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(tblCart);

        lblSubtotal.setFont(new java.awt.Font("Helvetica Neue", 0, 18)); // NOI18N
        lblSubtotal.setText("lblSubtotal");

        lblVat.setFont(new java.awt.Font("Helvetica Neue", 0, 18)); // NOI18N
        lblVat.setText("lblVat");

        lblTotal.setFont(new java.awt.Font("Helvetica Neue", 0, 18)); // NOI18N
        lblTotal.setText("lblTotal");

        btnRemove.setBackground(new java.awt.Color(255, 51, 51));
        btnRemove.setFont(new java.awt.Font("Helvetica Neue", 1, 18)); // NOI18N
        btnRemove.setForeground(new java.awt.Color(0, 0, 0));
        btnRemove.setText("Remove");
        btnRemove.setOpaque(true);
        btnRemove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRemoveActionPerformed(evt);
            }
        });

        btnClear.setBackground(new java.awt.Color(255, 204, 0));
        btnClear.setFont(new java.awt.Font("Helvetica Neue", 1, 18)); // NOI18N
        btnClear.setForeground(new java.awt.Color(0, 0, 0));
        btnClear.setText("Clear");
        btnClear.setOpaque(true);
        btnClear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnClearActionPerformed(evt);
            }
        });

        btnPayCash.setFont(new java.awt.Font("Helvetica Neue", 1, 18)); // NOI18N
        btnPayCash.setForeground(new java.awt.Color(51, 153, 0));
        btnPayCash.setText("Pay Cash");
        btnPayCash.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPayCashActionPerformed(evt);
            }
        });

        btnCancel.setBackground(new java.awt.Color(102, 102, 102));
        btnCancel.setFont(new java.awt.Font("sansserif", 0, 18)); // NOI18N
        btnCancel.setForeground(new java.awt.Color(0, 0, 0));
        btnCancel.setText("Cancel");
        btnCancel.setOpaque(true);
        btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Helvetica Neue", 0, 24)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(51, 153, 0));
        jLabel1.setText("SentePOS");

        chkPrint.setFont(new java.awt.Font("Helvetica Neue", 0, 16)); // NOI18N
        chkPrint.setSelected(true);
        chkPrint.setText("Print Receipt");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 237, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(53, 53, 53)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(lblSubtotal)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(lblVat)
                                .addGap(89, 89, 89)
                                .addComponent(lblTotal)
                                .addGap(23, 23, 23))
                            .addComponent(jScrollPane1)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(182, 182, 182)
                                .addComponent(btnRemove, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(33, 33, 33)
                                .addComponent(btnClear, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 28, Short.MAX_VALUE)
                                .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(txtScan)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(txtQty, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(btnAdd, javax.swing.GroupLayout.PREFERRED_SIZE, 96, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(chkPrint, javax.swing.GroupLayout.PREFERRED_SIZE, 213, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnPayCash, javax.swing.GroupLayout.PREFERRED_SIZE, 191, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(txtScan, javax.swing.GroupLayout.PREFERRED_SIZE, 42, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtQty, javax.swing.GroupLayout.PREFERRED_SIZE, 42, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(btnAdd, javax.swing.GroupLayout.PREFERRED_SIZE, 42, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(34, 34, 34)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lblSubtotal)
                            .addComponent(lblVat)
                            .addComponent(lblTotal))
                        .addGap(30, 30, 30)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 337, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(168, 168, 168)
                        .addComponent(btnPayCash, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(26, 26, 26)
                        .addComponent(chkPrint)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 28, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnRemove, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnClear, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(41, 41, 41))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void txtScanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtScanActionPerformed
        // TODO add your handling code here:
        doAdd();
    }//GEN-LAST:event_txtScanActionPerformed

    private void btnAddActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddActionPerformed
        // TODO add your handling code here:
        doAdd();
    }//GEN-LAST:event_btnAddActionPerformed

    private void btnRemoveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRemoveActionPerformed
        // TODO add your handling code here:
        doRemoveSelected();
        
    }//GEN-LAST:event_btnRemoveActionPerformed

    private void btnClearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnClearActionPerformed
        // TODO add your handling code here:
        doClear();
    }//GEN-LAST:event_btnClearActionPerformed

    private void btnPayCashActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPayCashActionPerformed
        // TODO add your handling code here:
        doPayCash();
    }//GEN-LAST:event_btnPayCashActionPerformed

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        // TODO add your handling code here:
        parseQty();
    }//GEN-LAST:event_btnCancelActionPerformed

    private void txtQtyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtQtyActionPerformed
        // TODO add your handling code here:
        doAdd();
    }//GEN-LAST:event_txtQtyActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new PosFrame().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAdd;
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnClear;
    private javax.swing.JButton btnPayCash;
    private javax.swing.JButton btnRemove;
    private javax.swing.JCheckBox chkPrint;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblSubtotal;
    private javax.swing.JLabel lblTotal;
    private javax.swing.JLabel lblVat;
    private javax.swing.JTable tblCart;
    private javax.swing.JTextField txtQty;
    private javax.swing.JTextField txtScan;
    // End of variables declaration//GEN-END:variables
}
