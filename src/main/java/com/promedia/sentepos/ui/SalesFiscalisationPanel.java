package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.EfrisStatusDAO;
import com.promedia.sentepos.service.PosService; // retry method call (optional; if you don't have it, comment out)

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SalesFiscalisationPanel extends JPanel {
    private final DefaultTableModel model;
    private final JTable table;
    private final JTextField search;
    private final JButton btnViewDbRequest, btnViewDbResponse, btnOpenFileRequest, btnOpenFileResponse, btnRetry;

    public SalesFiscalisationPanel() {
        setLayout(new BorderLayout(8,8));
        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        model = new DefaultTableModel(new String[]{
            "Sale ID","Receipt","Status","Invoice(FDN)","Verification","Created","Sent","Error"
        }, 0) {
            @Override public boolean isCellEditable(int r,int c){ return false; }
        };

        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        search = new JTextField();
        JButton btnSearch = new JButton("Search");
        btnSearch.addActionListener(e -> refresh());

        JPanel top = new JPanel(new BorderLayout(6,6));
        top.add(new JLabel("Filter (receipt / FDN / saleId): "), BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);
        top.add(btnSearch, BorderLayout.EAST);

        btnViewDbRequest   = new JButton("View DB Request");
        btnViewDbResponse  = new JButton("View DB Response");
        btnOpenFileRequest = new JButton("Open File Request");
        btnOpenFileResponse= new JButton("Open File Response");
        btnRetry           = new JButton("Retry Fiscalise");

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(btnViewDbRequest);
        actions.add(btnViewDbResponse);
        actions.add(btnOpenFileRequest);
        actions.add(btnOpenFileResponse);
        actions.add(btnRetry);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        // Wire buttons
        btnViewDbRequest.addActionListener(e -> openDbPayload(true));
        btnViewDbResponse.addActionListener(e -> openDbPayload(false));
        btnOpenFileRequest.addActionListener(e -> openFilePayload(true));
        btnOpenFileResponse.addActionListener(e -> openFilePayload(false));
        btnRetry.addActionListener(e -> retry());

        refresh();
    }

    private void refresh() {
        model.setRowCount(0);
        String q = search.getText().trim();
        try {
            List<EfrisStatusDAO.Row> rows = EfrisStatusDAO.listRecent(500, q.isEmpty()?null:q);
            for (var r : rows) {
                model.addRow(new Object[]{
                    r.saleId, nvl(r.receiptNo,""), nvl(r.status,""),
                    nvl(r.invoiceNumber,""), nvl(r.verificationCode,""),
                    nvl(r.createdAt,""), nvl(r.sentAt,""), nvl(r.errorMessage,"")
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Load failed: " + ex.getMessage());
        }
    }

    private void openDbPayload(boolean request) {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a row first"); return; }
        long saleId = Long.parseLong(model.getValueAt(row, 0).toString());
        try {
            var r = EfrisStatusDAO.getBySaleId(saleId);
            String content = request ? r.requestJson : r.responseJson;
            if (content == null || content.isBlank()) {
                JOptionPane.showMessageDialog(this, "No " + (request?"request":"response") + " payload stored in DB.");
                return;
            }
            PayloadPreviewDialog.show(
                SwingUtilities.getWindowAncestor(this),
                (request?"DB Request":"DB Response") + " - Sale #" + saleId,
                content
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Open failed: " + ex.getMessage());
        }
    }

    /** Looks under Logs/Payloads for files like sale-<id>-request*.json or sale-<id>-response*.json */
    private void openFilePayload(boolean request) {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a row first"); return; }
        long saleId = Long.parseLong(model.getValueAt(row, 0).toString());
        String prefix = "sale-" + saleId + "-" + (request ? "request" : "response");
        Path dir = Paths.get(System.getProperty("user.dir"), "Logs", "Payloads");
        if (!Files.exists(dir)) {
            JOptionPane.showMessageDialog(this, "Payloads dir not found: " + dir);
            return;
        }
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> candidates = s
                .filter(p -> p.getFileName().toString().startsWith(prefix) && p.getFileName().toString().endsWith(".json"))
                .sorted((a,b)->{
                    try { return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)); }
                    catch (IOException e){ return 0; }
                })
                .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No file payload found for " + prefix);
                return;
            }
            Path file = candidates.get(0);
            String content = Files.readString(file, StandardCharsets.UTF_8);
            PayloadPreviewDialog.show(
                SwingUtilities.getWindowAncestor(this),
                "File " + (request?"Request":"Response") + " - " + file.getFileName(),
                content
            );
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Read failed: " + ex.getMessage());
        }
    }

    private void retry() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Select a row first"); return; }
        long saleId = Long.parseLong(model.getValueAt(row, 0).toString());
        int ok = JOptionPane.showConfirmDialog(this,
                "Retry fiscalise for sale #" + saleId + "?", "Confirm",
                JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        try {
            // If you already implemented it:
            PosService.retryFiscalise(saleId); // comment/remove if you don’t have this yet
            JOptionPane.showMessageDialog(this, "Queued/retired fiscalisation for sale #" + saleId);
            refresh();
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, "Retry failed: " + t.getMessage());
        }
    }

    private static String nvl(String s, String d){ return s==null? d : s; }
}