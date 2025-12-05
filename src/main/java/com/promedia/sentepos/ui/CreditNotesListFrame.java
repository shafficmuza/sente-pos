package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.CreditNoteDAO;
import com.promedia.sentepos.dao.CreditNoteDAO.ListRow;
import com.promedia.sentepos.service.CreditNoteService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists all credit notes with EFRIS info, allows searching
 * and manual fiscalisation (re-send) of a selected credit note.
 */
public class CreditNotesListFrame extends JFrame {

    private JTextField txtSearch;
    private JButton btnSearch;
    private JButton btnClear;
    private JTable table;
    private NotesModel model;

    private List<ListRow> allRows = new ArrayList<>();

    public CreditNotesListFrame() {
        super("Credit Notes");
        buildUI();
        loadData();
    }

    private void buildUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // --- North: search bar ---
        JPanel north = new JPanel(new BorderLayout(5, 5));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search:"));
        txtSearch = new JTextField(25);
        searchPanel.add(txtSearch);

        btnSearch = new JButton("Search");
        btnClear  = new JButton("Clear");

        searchPanel.add(btnSearch);
        searchPanel.add(btnClear);

        north.add(searchPanel, BorderLayout.CENTER);

        btnSearch.addActionListener(e -> applyFilter());
        btnClear.addActionListener(e -> {
            txtSearch.setText("");
            applyFilter();
        });

        // --- Center: table ---
        model = new NotesModel();
        table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true); // allow sorting

        // Wider columns for messages
        table.getColumnModel().getColumn(0).setPreferredWidth(30);   // CN No
        table.getColumnModel().getColumn(1).setPreferredWidth(30);   // Sale ID
        table.getColumnModel().getColumn(2).setPreferredWidth(150);  // Reason
        table.getColumnModel().getColumn(3).setPreferredWidth(140);  // Date
        table.getColumnModel().getColumn(4).setPreferredWidth(80);   // Status
        table.getColumnModel().getColumn(5).setPreferredWidth(90);   // EFRIS Status
        table.getColumnModel().getColumn(6).setPreferredWidth(120);  // FDN
        table.getColumnModel().getColumn(7).setPreferredWidth(140);  // Verification
        table.getColumnModel().getColumn(8).setPreferredWidth(250);  // Return message

        JScrollPane scroll = new JScrollPane(table);

        // --- South: actions ---
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnFiscalise = new JButton("Fiscalise / Re-Send");
        JButton btnClose     = new JButton("Close");

        south.add(btnFiscalise);
        south.add(btnClose);

        btnClose.addActionListener(e -> dispose());
        btnFiscalise.addActionListener(e -> onFiscalise());

        // --- Layout ---
        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);

        setSize(1100, 500);
        setLocationRelativeTo(null);
    }

    private void loadData() {
        try {
            allRows = CreditNoteDAO.listAllWithEfris();
            model.setRows(allRows);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load credit notes: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Apply client-side filtering by CN #, sale #, status, FDN, or reason. */
    private void applyFilter() {
        String q = txtSearch.getText();
        if (q == null) q = "";
        q = q.trim().toLowerCase();

        if (q.isEmpty()) {
            model.setRows(allRows);
            return;
        }

        List<ListRow> filtered = new ArrayList<>();
        for (ListRow r : allRows) {
            String cnNo    = ("CN-" + r.id).toLowerCase();
            String saleId  = String.valueOf(r.sale_id);
            String reason  = r.reason != null ? r.reason.toLowerCase() : "";
            String status  = r.status != null ? r.status.toLowerCase() : "";
            String eStatus = r.efris_status != null ? r.efris_status.toLowerCase() : "";
            String fdn     = r.efris_invoice_number != null ? r.efris_invoice_number.toLowerCase() : "";
            String msg     = r.efris_error_message != null ? r.efris_error_message.toLowerCase() : "";

            if (cnNo.contains(q)
                    || saleId.contains(q)
                    || reason.contains(q)
                    || status.contains(q)
                    || eStatus.contains(q)
                    || fdn.contains(q)
                    || msg.contains(q)) {
                filtered.add(r);
            }
        }
        model.setRows(filtered);
    }

    private void onFiscalise() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a credit note first.");
            return;
        }
        // Because of RowSorter, convert view index to model index
        row = table.convertRowIndexToModel(row);
        ListRow r = model.getRow(row);

        int ans = JOptionPane.showConfirmDialog(this,
                "Fiscalise / re-send credit note CN-" + r.id + " now?",
                "Confirm",
                JOptionPane.YES_NO_OPTION);
        if (ans != JOptionPane.YES_OPTION) return;

        try {
            // Call existing service
            String fdn = CreditNoteService.fiscaliseCreditNote(r.id);
            JOptionPane.showMessageDialog(this,
                    "Fiscalisation sent.\nFDN: " + fdn,
                    "OK",
                    JOptionPane.INFORMATION_MESSAGE);

            // Reload list (status + FDN + verification code may have changed)
            loadData();
            applyFilter(); // keep current filter if any

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Fiscalisation failed: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // ----------------------------------------------------
    // Table model
    // ----------------------------------------------------
    private static final class NotesModel extends AbstractTableModel {
        private final String[] cols = {
                "CN No",          // CN-<id>
                "Sale ID",
                "Reason",
                "Date/Time",
                "Status",
                "EFRIS Status",
                "FDN / Invoice No",
                "Verification Code",
                "Return Message"
        };

        private List<ListRow> rows = new ArrayList<>();

        public void setRows(List<ListRow> r) {
            rows = (r != null) ? r : new ArrayList<>();
            fireTableDataChanged();
        }

        public ListRow getRow(int idx) {
            return rows.get(idx);
        }

        @Override public int getRowCount()  { return rows.size(); }
        @Override public int getColumnCount(){ return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override public Object getValueAt(int r, int c) {
            ListRow x = rows.get(r);
            return switch (c) {
                case 0 -> "CN-" + x.id;                  // local CN number
                case 1 -> x.sale_id;
                case 2 -> x.reason;
                case 3 -> x.date_time;
                case 4 -> x.status;
                case 5 -> x.efris_status;
                case 6 -> x.efris_invoice_number;
                case 7 -> x.efris_verification;
                case 8 -> x.efris_error_message;
                default -> "";
            };
        }

        @Override public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0, 1 -> Long.class;
                default   -> String.class;
            };
        }

        @Override public boolean isCellEditable(int r, int c) {
            return false;
        }
    }

    // Convenience launcher
    public static void open() {
        SwingUtilities.invokeLater(() -> {
            CreditNotesListFrame f = new CreditNotesListFrame();
            f.setVisible(true);
        });
    }
}