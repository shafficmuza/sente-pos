package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.CreditNoteDAO;
import com.promedia.sentepos.dao.CreditNoteDAO.ListRow;
import com.promedia.sentepos.service.CreditNoteService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Page to view all credit notes and (re)-fiscalise them, with search and EFRIS info.
 */
public class CreditNoteListPanel extends JPanel {

    private JTable table;
    private CreditNoteTableModel model;
    private JButton btnRefresh;
    private JButton btnFiscalise;
    private JButton btnPrint;
    private JButton btnSearch;
    private JButton btnClear;
    private JTextField txtSearch;

    public CreditNoteListPanel() {
        buildUI();
        reload();
    }

    // ---------------------- UI ----------------------

    private void buildUI() {
        setLayout(new BorderLayout(8, 8));

        // Top search bar
        JPanel north = new JPanel(new BorderLayout(4, 4));
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        searchPanel.add(new JLabel("Search:"));
        txtSearch = new JTextField(30);
        searchPanel.add(txtSearch);

        btnSearch = new JButton("Search");
        btnClear  = new JButton("Clear");
        searchPanel.add(btnSearch);
        searchPanel.add(btnClear);

        // search button
        btnSearch.addActionListener(e -> reload());
        // clear button
        btnClear.addActionListener(e -> {
            txtSearch.setText("");
            reload();
        });
        // Enter key in search field
        txtSearch.addActionListener(e -> reload());

        north.add(searchPanel, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        // Table + model
        model = new CreditNoteTableModel();
        table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);

        // double-click -> preview credit note
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    onPrintSelected();
                }
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        // Bottom buttons
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRefresh   = new JButton("Refresh");
        btnFiscalise = new JButton("Fiscalise / Re-send");
        btnPrint     = new JButton("Print");

        btnRefresh.addActionListener(e -> reload());
        btnFiscalise.addActionListener(e -> onFiscaliseSelected());
        btnPrint.addActionListener(e -> onPrintSelected());

        south.add(btnRefresh);
        south.add(btnFiscalise);
        south.add(btnPrint);

        add(south, BorderLayout.SOUTH);
    }

    // ---------------------- Data ----------------------

    private void reload() {
        String search = txtSearch != null ? txtSearch.getText().trim() : "";
        try {
            // Use the richer UI listing with EFRIS fields and total_qty
            List<ListRow> rows = CreditNoteDAO.listForUi(search.isEmpty() ? null : search);
            model.setRows(rows);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load credit notes: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private ListRow getSelectedRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return model.getRow(modelRow);
    }

    // ---------------------- Actions ----------------------

    /** (Re)-fiscalise the selected credit note. */
    private void onFiscaliseSelected() {
        ListRow r = getSelectedRow();
        if (r == null) {
            JOptionPane.showMessageDialog(this, "Select a credit note first.");
            return;
        }

        // Only allow PENDING or FAILED to be re-sent
        if ("SENT".equalsIgnoreCase(r.status) || "CANCELLED".equalsIgnoreCase(r.status)) {
            JOptionPane.showMessageDialog(this,
                    "This credit note is already " + r.status + " and cannot be re-fiscalised.");
            return;
        }

        int ans = JOptionPane.showConfirmDialog(this,
                "Fiscalise credit note #" + r.id + " now?",
                "Confirm",
                JOptionPane.YES_NO_OPTION);
        if (ans != JOptionPane.YES_OPTION) return;

        try {
            String cnNo = CreditNoteService.fiscaliseCreditNote(r.id);
            JOptionPane.showMessageDialog(this,
                    "Credit note fiscalised successfully.\nEFRIS CN Ref: " + cnNo,
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            reload();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Fiscalisation failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            // status may have changed to FAILED, so reload
            reload();
        }
    }

    /** Preview/print the selected credit note. */
    private void onPrintSelected() {
        ListRow r = getSelectedRow();
        if (r == null) {
            JOptionPane.showMessageDialog(this, "Select a credit note first.");
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        com.promedia.sentepos.print.CreditNotePrinter.previewFor(r.id, owner);
    }

    // ---------------------- Table model ----------------------

    private static final class CreditNoteTableModel extends AbstractTableModel {

        // Columns:
        //   ID, Sale ID, Date/Time, Reason, Total Qty, Subtotal, VAT, Total,
        //   Local Status, EFRIS Status,
        //   EFRIS Reference No,
        //   EFRIS Invoice No / FDN,
        //   Verification Code, Return Message
        private final String[] cols = {
                "ID",
                "Sale ID",
                "Date/Time",
                "Reason",
                "Total Qty",
                "Subtotal",
                "VAT",
                "Total",
                "Local Status",
                "EFRIS Status",
                "EFRIS Reference No",
                "EFRIS Invoice No / FDN",
                "Verification Code",
                "Return Message"
        };

        private List<ListRow> rows = new ArrayList<>();

        void setRows(List<ListRow> list) {
            this.rows = (list != null) ? list : new ArrayList<>();
            fireTableDataChanged();
        }

        ListRow getRow(int row) {
            return rows.get(row);
        }

        @Override public int getRowCount() {
            return rows.size();
        }

        @Override public int getColumnCount() {
            return cols.length;
        }

        @Override public String getColumnName(int column) {
            return cols[column];
        }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            ListRow r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0  -> r.id;
                case 1  -> r.sale_id;
                case 2  -> r.date_time;
                case 3  -> r.reason;
                case 4  -> r.total_qty;
                case 5  -> r.subtotal;
                case 6  -> r.vat_total;
                case 7  -> r.total;
                case 8  -> r.status;
                case 9  -> r.efris_status;
                case 10 -> r.reference_number;      // <- DB column reference_number
                case 11 -> r.efris_invoice_number;  // <- DB column invoice_number
                case 12 -> r.efris_verification;
                case 13 -> r.efris_error_message;
                default -> "";
            };
        }

        @Override public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0, 1 -> Long.class;
                case 4, 5, 6, 7 -> Double.class;
                default -> String.class;
            };
        }

        @Override public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    // ---------------------- Helpers to open as a dialog ----------------------

    /** Open the list in a modal dialog from any component. */
    public static void openInDialog(Component parent) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        openInDialog(owner);
    }

    /** Open the list in a modal dialog from a Window. */
    public static void openInDialog(Window owner) {
        JDialog dlg = (owner instanceof Frame)
                ? new JDialog((Frame) owner, "Credit Notes", true)
                : new JDialog((Frame) null, "Credit Notes", true);

        CreditNoteListPanel panel = new CreditNoteListPanel();
        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setSize(1100, 500);
        dlg.toFront();
        dlg.setVisible(true);
    }
}