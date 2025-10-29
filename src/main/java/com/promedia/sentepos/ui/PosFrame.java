/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.promedia.sentepos.ui;
import com.promedia.sentepos.dao.ProductDAO.ProductRow;
import com.promedia.sentepos.model.Payment;
import com.promedia.sentepos.model.Sale;
import com.promedia.sentepos.model.SaleItem;
import com.promedia.sentepos.service.PosService;

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
        
            }
    /*
        private void txtScanActionPerformed(java.awt.event.ActionEvent evt) { doAdd(); }
        private void btnAddActionPerformed(java.awt.event.ActionEvent evt) { doAdd(); }
        private void btnRemoveActionPerformed(java.awt.event.ActionEvent evt) { doRemoveSelected(); }
        private void btnClearActionPerformed(java.awt.event.ActionEvent evt) { doClear(); }
        private void btnPayCashActionPerformed(java.awt.event.ActionEvent evt) { doPayCash(); }
        private void btnFinishActionPerformed(java.awt.event.ActionEvent evt) { doFinish(); }
        private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) { dispose(); }
*/
        
    private void refreshTotals() {
    double subtotal = cartModel.all().stream().mapToDouble(it -> it.lineTotal).sum();
    double vat = cartModel.all().stream().mapToDouble(it -> it.vatAmount).sum();
    double total = subtotal + vat;
    lblSubtotal.setText(String.format("UGX %, .0f", subtotal));
    lblVat.setText(String.format("UGX %, .0f", vat));
    lblTotal.setText(String.format("UGX %, .0f", total));
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
    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Invalid amount.");
    }
}

private void doFinish() {
    if (cartModel.all().isEmpty()) { JOptionPane.showMessageDialog(this, "Cart is empty."); return; }

    currentSale.items.clear();
    currentSale.items.addAll(cartModel.all());

    Payment p = new Payment();
    p.method = Payment.Method.CASH;
    p.amount = currentSale.paid > 0 ? currentSale.paid :
        currentSale.items.stream().mapToDouble(it -> it.lineTotal + it.vatAmount).sum();

    try {
        long saleId = PosService.saveSale(currentSale, p);
        JOptionPane.showMessageDialog(this, "Sale saved. ID = " + saleId);
        // new sale
        currentSale = new Sale();
        cartModel.clear();
        txtScan.requestFocus();
        refreshTotals();
    } catch (SQLException ex) {
        JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
    }
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
        btnFinish = new javax.swing.JButton();
        btnCancel = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        txtScan.setText("txtScan");
        txtScan.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtScanActionPerformed(evt);
            }
        });

        txtQty.setText("txtQty");

        btnAdd.setText("btnAdd");
        btnAdd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddActionPerformed(evt);
            }
        });

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

        lblSubtotal.setText("lblSubtotal");

        lblVat.setText("lblVat");

        lblTotal.setText("lblTotal");

        btnRemove.setText("btnRemove");
        btnRemove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRemoveActionPerformed(evt);
            }
        });

        btnClear.setText("btnClear");
        btnClear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnClearActionPerformed(evt);
            }
        });

        btnPayCash.setText("btnPayCash");
        btnPayCash.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPayCashActionPerformed(evt);
            }
        });

        btnFinish.setText("btnFinish");
        btnFinish.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnFinishActionPerformed(evt);
            }
        });

        btnCancel.setText("btnCancel");
        btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(76, Short.MAX_VALUE)
                .addComponent(lblSubtotal)
                .addGap(89, 89, 89)
                .addComponent(lblVat)
                .addGap(89, 89, 89)
                .addComponent(lblTotal)
                .addGap(199, 199, 199))
            .addGroup(layout.createSequentialGroup()
                .addGap(53, 53, 53)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(btnRemove)
                        .addGap(30, 30, 30)
                        .addComponent(btnClear))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(txtScan, javax.swing.GroupLayout.PREFERRED_SIZE, 141, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(49, 49, 49)
                        .addComponent(txtQty, javax.swing.GroupLayout.PREFERRED_SIZE, 127, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(btnAdd))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(layout.createSequentialGroup()
                            .addComponent(btnPayCash)
                            .addGap(81, 81, 81)
                            .addComponent(btnFinish)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnCancel))
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(56, 56, 56)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtScan, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtQty, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnAdd))
                .addGap(34, 34, 34)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblSubtotal)
                    .addComponent(lblVat)
                    .addComponent(lblTotal))
                .addGap(33, 33, 33)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 118, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnRemove)
                    .addComponent(btnClear))
                .addGap(93, 93, 93)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnPayCash)
                    .addComponent(btnFinish)
                    .addComponent(btnCancel))
                .addContainerGap(80, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void txtScanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtScanActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtScanActionPerformed

    private void btnAddActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnAddActionPerformed

    private void btnRemoveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRemoveActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnRemoveActionPerformed

    private void btnClearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnClearActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnClearActionPerformed

    private void btnPayCashActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPayCashActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnPayCashActionPerformed

    private void btnFinishActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnFinishActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnFinishActionPerformed

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnCancelActionPerformed

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
    private javax.swing.JButton btnFinish;
    private javax.swing.JButton btnPayCash;
    private javax.swing.JButton btnRemove;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblSubtotal;
    private javax.swing.JLabel lblTotal;
    private javax.swing.JLabel lblVat;
    private javax.swing.JTable tblCart;
    private javax.swing.JTextField txtQty;
    private javax.swing.JTextField txtScan;
    // End of variables declaration//GEN-END:variables
}
