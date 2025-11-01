package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.SaleDAO;
import com.promedia.sentepos.dao.SaleDAO.SaleHead;
import com.promedia.sentepos.dao.SaleDAO.Aggregate;
import com.promedia.sentepos.print.ReceiptPrinter;
import com.promedia.sentepos.service.FiscalService;
// optional if you already have it
import com.promedia.sentepos.ui.CreditNoteDialog;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

public class SalesListDialog extends JDialog {

    private final JTextField txtSearch = new JTextField();
    private final JButton btnRefresh = new JButton("Refresh");
    private final JButton btnPreview = new JButton("Preview Receipt");
    private final JButton btnPrint   = new JButton("Print Receipt");
    private final JButton btnFiscal  = new JButton("Fiscalise");
    private final JButton btnCredit  = new JButton("Issue Credit Note");

    private final JTable table = new JTable();
    private final SalesModel model = new SalesModel();

    public SalesListDialog(Frame owner) {
        super(owner, "Sales", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Top bar
        JPanel north = new JPanel(new BorderLayout(8, 8));
        txtSearch.setToolTipText("Search by receipt no or note…  Press Enter to search");
        north.add(txtSearch, BorderLayout.CENTER);
        north.add(btnRefresh, BorderLayout.EAST);

        // Table
        table.setModel(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(table);

        // Bottom actions
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btnPreview);
        south.add(btnPrint);
        south.add(btnFiscal);
        south.add(btnCredit);

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);

        setSize(900, 520);

        // Events
        btnRefresh.addActionListener(e -> load());
        txtSearch.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) load();
            }
        });

        btnPreview.addActionListener(e -> doPreview(false));
        btnPrint.addActionListener(e -> doPreview(true));
        btnFiscal.addActionListener(e -> doFiscalise());
        btnCredit.addActionListener(e -> doCreditNote());

        // initial load
        load();
    }

    private void load() {
        try {
            String q = txtSearch.getText().trim();
            List<SaleHead> rows = SaleDAO.listRecent(200, q.isEmpty()? null : q);
            model.setRows(rows);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Load failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private SaleHead selected() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Select a sale first.");
            return null;
        }
        return model.getAt(table.convertRowIndexToModel(r));
    }

    private void doPreview(boolean directPrint) {
        SaleHead h = selected(); if (h == null) return;
        try {
            // Load sale aggregate (items + best payment)
            Aggregate agg = SaleDAO.loadAggregate(h.id);
            // Build receipt printer with Business & EFRIS aware fields
            ReceiptPrinter rp = ReceiptPrinter.usingBusinessFromDb(agg.sale, agg.payment, h.id);
            if (directPrint) {
                rp.print(null); // default printer
            } else {
                rp.preview(this);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Preview/Print failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doFiscalise() {
        SaleHead h = selected(); if (h == null) return;
        int ok = JOptionPane.showConfirmDialog(this, "Send sale #" + h.id + " to EFRIS now?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        try {
            Aggregate agg = SaleDAO.loadAggregate(h.id);
            String invoiceNo = FiscalService.fiscalise(h.id, agg.sale, agg.payment);
            JOptionPane.showMessageDialog(this, "Sent. Invoice No: " + invoiceNo);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Fiscalise failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doCreditNote() {
        SaleHead h = selected(); if (h == null) return;
        // Open your existing CreditNoteDialog (we built earlier)
        CreditNoteDialog.openForSale(this, h.id);
    }

    /* ===================== Table Model ===================== */
    private static final class SalesModel extends AbstractTableModel {
        private List<SaleHead> rows = java.util.Collections.emptyList();
        public void setRows(List<SaleHead> r){ this.rows = r; fireTableDataChanged(); }
        public SaleHead getAt(int i){ return rows.get(i); }

        private final String[] cols = {"ID","Receipt","Date/Time","Total","Paid","Change","Note"};

        @Override public int getRowCount(){ return rows.size(); }
        @Override public int getColumnCount(){ return cols.length; }
        @Override public String getColumnName(int c){ return cols[c]; }

        @Override public Object getValueAt(int r, int c) {
            SaleHead h = rows.get(r);
            return switch (c) {
                case 0 -> h.id;
                case 1 -> h.receipt_no;
                case 2 -> h.date_time;
                case 3 -> h.total;
                case 4 -> h.paid;
                case 5 -> h.change_due;
                case 6 -> h.note;
                default -> "";
            };
        }

        @Override public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0 -> Long.class;
                case 3,4,5 -> Double.class;
                default -> String.class;
            };
        }
    }
}