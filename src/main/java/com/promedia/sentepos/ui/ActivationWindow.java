package com.promedia.sentepos.ui;

import com.promedia.sentepos.license.LicenseManager;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

public final class ActivationWindow extends JDialog {

    private final JTextField txtPhone = new JTextField(16);
    private final JComboBox<String> cmbPlan = new JComboBox<>(new String[]{"MONTHLY", "YEARLY"});
    private final JTextArea log = new JTextArea(12, 46);

    private final JButton btnPay = new JButton("Pay & Activate");
    private final JButton btnClose = new JButton("Close");

    private volatile boolean running = false;

    public ActivationWindow() { this((Window) null); }

    public ActivationWindow(Window owner) {
        super(owner, "Activate SentePOS", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setLineWrap(true);
        log.setWrapStyleWord(true);

        txtPhone.setToolTipText("Digits only. Example: 256775200442");

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        int y = 0;
        addRow(form, gc, y++, "Phone (MSISDN)", txtPhone);
        addRow(form, gc, y++, "Plan", cmbPlan);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(btnPay);
        actions.add(btnClose);

        btnPay.addActionListener(e -> startActivation());
        btnClose.addActionListener(e -> { if (!running) dispose(); });
        txtPhone.addActionListener(e -> startActivation()); // Enter triggers pay

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(form, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(log), BorderLayout.CENTER);
        getContentPane().add(actions, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
        setAlwaysOnTop(true);
    }

    private static void addRow(JPanel p, GridBagConstraints gc, int y, String label, JComponent comp) {
        gc.gridx = 0; gc.gridy = y; gc.weightx = 0; p.add(new JLabel(label), gc);
        gc.gridx = 1; gc.weightx = 1; p.add(comp, gc);
    }

    private void startActivation() {
    if (running) return;

    final String phone = normalizeMsisdn(txtPhone.getText());
    if (phone.isBlank()) {
        JOptionPane.showMessageDialog(this, "Enter phone number (digits only). Example: 256775200442");
        return;
    }

    String plan = String.valueOf(cmbPlan.getSelectedItem()).trim().toUpperCase(Locale.ROOT);
    if (!"MONTHLY".equals(plan) && !"YEARLY".equals(plan)) plan = "MONTHLY";
    final String finalPlan = plan; // ✅ effectively final for lambdas

    running = true;
    setUiBusy(true);

    append("Starting activation...");
    append("Plan: " + finalPlan);
    append("DeviceId: " + LicenseManager.getOrCreateDeviceId());
    append("MachineId: " + LicenseManager.getMachineId());
    append("Requesting payment prompt on phone...");

    Thread worker = new Thread(() -> {
        try {
            LicenseManager.payAndActivate(finalPlan, phone);
            append("✅ Activation OK. License saved.");

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Activation successful!");
                dispose();
            });
        } catch (Exception ex) {
            append("❌ Activation failed: " + ex.getMessage());
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, "Activation failed: " + ex.getMessage())
            );
        } finally {
            running = false;
            SwingUtilities.invokeLater(() -> setUiBusy(false));
        }
    }, "license-activation");

    worker.setDaemon(true);
    worker.start();
}

    private void setUiBusy(boolean busy) {
        btnPay.setEnabled(!busy);
        btnClose.setEnabled(!busy);
        txtPhone.setEnabled(!busy);
        cmbPlan.setEnabled(!busy);
    }

    private static String normalizeMsisdn(String in) {
        if (in == null) return "";
        String s = in.trim().replaceAll("\\s+", "");
        if (s.startsWith("+")) s = s.substring(1);
        return s.replaceAll("[^0-9]", "");
    }

    private static String normalizePlan(String in) {
        String p = (in == null) ? "MONTHLY" : in.trim().toUpperCase(Locale.ROOT);
        return ("YEARLY".equals(p) || "MONTHLY".equals(p)) ? p : "MONTHLY";
    }

    private void append(String s) {
        SwingUtilities.invokeLater(() -> {
            log.append(s + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }
}