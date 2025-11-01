package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.CreditNoteDAO;
import com.promedia.sentepos.dao.CreditNoteDAO.SaleLine;
import com.promedia.sentepos.service.PosService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CreditNoteDialog extends JDialog {
    private final long saleId;

    private JTable tbl;
    private LinesModel model;
    private JTextField txtReason, txtNote;
    private JLabel lblSubtotal, lblVat, lblTotal;
    private JButton btnIssue, btnCancel;

    public CreditNoteDialog(Frame parent, long saleId) {
        super(parent, "Issue Credit Note", true);
        this.saleId = saleId;
        buildUI();
        load();
    }

    private void buildUI() {
        model = new LinesModel();
        tbl = new JTable(model);
        tbl.setFillsViewportHeight(true);

        txtReason = new JTextField(28);
        txtNote   = new JTextField(28);

        lblSubtotal = new JLabel("Subtotal: UGX 0");
        lblVat      = new JLabel("VAT: UGX 0");
        lblTotal    = new JLabel("TOTAL: UGX 0");

        btnIssue = new JButton("Issue");
        btnCancel = new JButton("Cancel");

        btnIssue.addActionListener(e -> onIssue());
        btnCancel.addActionListener(e -> dispose());

        JPanel north = new JPanel(new BorderLayout());
        JPanel meta = new JPanel(new GridLayout(0,1,4,4));
        meta.add(new JLabel("Reason (required):"));
        meta.add(txtReason);
        meta.add(new JLabel("Note (optional):"));
        meta.add(txtNote);
        north.add(meta, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout());
        center.add(new JScrollPane(tbl), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        JPanel totals = new JPanel(new FlowLayout(FlowLayout.LEFT));
        totals.add(lblSubtotal); totals.add(Box.createHorizontalStrut(15));
        totals.add(lblVat);      totals.add(Box.createHorizontalStrut(15));
        totals.add(lblTotal);
        south.add(totals, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(btnIssue);
        actions.add(btnCancel);
        south.add(actions, BorderLayout.EAST);

        getContentPane().setLayout(new BorderLayout(8,8));
        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(center, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);

        setSize(780, 480);
        setLocationRelativeTo(getParent());
    }

    private void load() {
        try {
            List<SaleLine> lines = CreditNoteDAO.saleItemsForSale(saleId);
            model.setRows(lines);
            recomputeTotals();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Load failed: " + e.getMessage());
            dispose();
        }
    }

    private void recomputeTotals() {
        double sub=0, vat=0;
        for (SaleLine r : model.rows) {
            if (r.return_qty > 0) {
                double line = r.return_qty * r.unit_price;
                double v = line * (r.vat_rate/100.0);
                sub += line;
                vat += v;
            }
        }
        double total = sub + vat;
        lblSubtotal.setText(String.format("Subtotal: UGX %, .0f", sub).replace(" ,"," "));
        lblVat.setText(String.format("VAT: UGX %, .0f", vat).replace(" ,"," "));
        lblTotal.setText(String.format("TOTAL: UGX %, .0f", total).replace(" ,"," "));
    }

    private void onIssue() {
        String reason = txtReason.getText().trim();
        String note   = txtNote.getText().trim();
        if (reason.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Reason is required.");
            txtReason.requestFocus();
            return;
        }

        // Collect selected return lines
        List<CreditNoteDAO.ItemLine> items = new ArrayList<>();
        double sub=0, vat=0;
        for (SaleLine r : model.rows) {
            if (r.return_qty > 0) {
                if (r.return_qty > r.sold_qty) {
                    JOptionPane.showMessageDialog(this,
                            "Return qty cannot exceed sold qty for item: " + r.item_name);
                    return;
                }
                CreditNoteDAO.ItemLine li = new CreditNoteDAO.ItemLine();
                li.product_id = r.product_id;
                li.item_name  = r.item_name;
                li.sku        = r.sku;
                li.qty        = r.return_qty;
                li.unit_price = r.unit_price;
                li.vat_rate   = r.vat_rate;
                items.add(li);

                double line = r.return_qty * r.unit_price;
                double v = line * (r.vat_rate/100.0);
                sub += line; vat += v;
            }
        }
        if (items.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Set a return quantity on at least one line.");
            return;
        }
        double total = sub + vat;

        try {
            long creditNoteId = PosService.issueCreditNote(saleId, items, reason, note, sub, vat, total);
            JOptionPane.showMessageDialog(this, "Credit note issued: #" + creditNoteId);

            // Optional: preview/print now
            int ans = JOptionPane.showConfirmDialog(this, "Print credit note now?", "Print",
                    JOptionPane.YES_NO_OPTION);
            if (ans == JOptionPane.YES_OPTION) {
                com.promedia.sentepos.print.CreditNotePrinter.previewFor(creditNoteId, this);
            }

            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Issue failed: " + e.getMessage());
        }
    }

    private final class LinesModel extends AbstractTableModel {
        private final String[] cols = {"Item", "Sold Qty", "Return Qty", "Unit Price", "VAT %"};
        private List<SaleLine> rows = new ArrayList<>();
        void setRows(List<SaleLine> r) { rows = r; fireTableDataChanged(); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c){ return cols[c]; }
        @Override public boolean isCellEditable(int r, int c){ return c==2; }

        @Override public Object getValueAt(int r, int c) {
            SaleLine x = rows.get(r);
            return switch (c) {
                case 0 -> x.item_name + (x.sku!=null? " ["+x.sku+"]": "");
                case 1 -> x.sold_qty;
                case 2 -> x.return_qty;
                case 3 -> x.unit_price;
                case 4 -> x.vat_rate;
                default -> "";
            };
        }

        @Override public void setValueAt(Object aValue, int r, int c) {
            if (c == 2) {
                try {
                    double v = Double.parseDouble(String.valueOf(aValue));
                    if (v < 0) v = 0;
                    rows.get(r).return_qty = v;
                    recomputeTotals();
                    fireTableRowsUpdated(r, r);
                } catch (Exception ignore) {}
            }
        }

        @Override public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 1,2,3,4 -> Double.class;
                default -> String.class;
            };
        }
    }
    
    // --- paste inside CreditNoteDialog class ---

/** Open the credit-note dialog for a specific sale (convenience from any Component). */
public static void openForSale(java.awt.Component parent, long saleId) {
    java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(parent);
    openForSale(w, saleId);
}

/** Open the credit-note dialog for a specific sale (Window owner). */
public static void openForSale(java.awt.Window owner, long saleId) {
    java.awt.Frame f = (owner instanceof java.awt.Frame) ? (java.awt.Frame) owner : null;
    CreditNoteDialog dlg = new CreditNoteDialog(f, saleId);  // requires the (Frame,long) ctor (see below)
    dlg.setLocationRelativeTo(owner);
    dlg.setVisible(true);
}

}