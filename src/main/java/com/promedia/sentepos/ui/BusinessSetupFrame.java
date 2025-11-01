package com.promedia.sentepos.ui;

import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.model.Business;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

public class BusinessSetupFrame extends JFrame {

    // Fields
    private JTextField txtName = new JTextField(28);
    private JTextField txtTIN = new JTextField(18);
    private JTextField txtBranch = new JTextField(12);
    private JTextField txtAddress = new JTextField(28);
    private JTextField txtCity = new JTextField(18);
    private JTextField txtCountry = new JTextField(18);
    private JTextField txtPhone = new JTextField(18);
    private JTextField txtEmail = new JTextField(22);
    private JTextField txtCurrency = new JTextField(8);
    private JTextField txtVatRate = new JTextField(6);

    private JTextField txtEfrisDevice = new JTextField(18);
    private JTextField txtEfrisUser = new JTextField(18);
    private JPasswordField txtEfrisPass = new JPasswordField(18);
    private JTextField txtEfrisBranch = new JTextField(18);

    private JButton btnNew = new JButton("New");
    private JButton btnSave = new JButton("Save");
    private JButton btnReload = new JButton("Reload");

    private Business current;

    public BusinessSetupFrame() {
        super("SentePOS — Business Setup");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(720, 520);
        setLocationByPlatform(true);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6,6,6,6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        int r=0;
        addRow(form, gc, r++, "Business Name *", txtName);
        addRow(form, gc, r++, "TIN",             txtTIN);
        addRow(form, gc, r++, "Branch Code",     txtBranch);
        addRow(form, gc, r++, "Address",         txtAddress);
        addRow(form, gc, r++, "City",            txtCity);
        addRow(form, gc, r++, "Country",         txtCountry);
        addRow(form, gc, r++, "Phone",           txtPhone);
        addRow(form, gc, r++, "Email",           txtEmail);
        addRow(form, gc, r++, "Currency",        txtCurrency);
        addRow(form, gc, r++, "Default VAT %",   txtVatRate);

        // Separator
        gc.gridx=0; gc.gridy=r++; gc.gridwidth=2; form.add(new JSeparator(), gc);
        gc.gridwidth=1;

        addRow(form, gc, r++, "EFRIS Device No.", txtEfrisDevice);
        addRow(form, gc, r++, "EFRIS Username",   txtEfrisUser);
        addRow(form, gc, r++, "EFRIS Password",   txtEfrisPass);
        addRow(form, gc, r++, "EFRIS Branch Id",  txtEfrisBranch);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(btnNew);
        buttons.add(btnReload);
        buttons.add(btnSave);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(new JScrollPane(form), BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        // defaults
        txtCurrency.setText("UGX");
        txtVatRate.setText("18");

        // events
        btnReload.addActionListener(e -> loadData());
        btnNew.addActionListener(e -> clearForm());
        btnSave.addActionListener(e -> saveData());

        loadData(); // initial
    }

    private static void addRow(JPanel panel, GridBagConstraints gc, int row, String label, JComponent field) {
        gc.gridx=0; gc.gridy=row; gc.weightx=0; panel.add(new JLabel(label), gc);
        gc.gridx=1; gc.weightx=1; panel.add(field, gc);
    }

    private void loadData() {
        try {
            current = BusinessDAO.loadSingle();
            if (current == null) {
                clearForm();
                return;
            }
            txtName.setText(nv(current.name));
            txtTIN.setText(nv(current.tin));
            txtBranch.setText(nv(current.branchCode));
            txtAddress.setText(nv(current.addressLine));
            txtCity.setText(nv(current.city));
            txtCountry.setText(nv(current.country));
            txtPhone.setText(nv(current.phone));
            txtEmail.setText(nv(current.email));
            txtCurrency.setText(nv(current.currency));
            txtVatRate.setText(current.vatRate != null ? noComma(current.vatRate) : "");

            txtEfrisDevice.setText(nv(current.efrisDeviceNo));
            txtEfrisUser.setText(nv(current.efrisUsername));
            txtEfrisPass.setText(nv(current.efrisPassword));
            txtEfrisBranch.setText(nv(current.efrisBranchId));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Load failed: " + ex.getMessage());
        }
    }

    private void clearForm() {
        current = null;
        txtName.setText("");
        txtTIN.setText("");
        txtBranch.setText("");
        txtAddress.setText("");
        txtCity.setText("");
        txtCountry.setText("Uganda");
        txtPhone.setText("");
        txtEmail.setText("");
        txtCurrency.setText("UGX");
        txtVatRate.setText("18");
        txtEfrisDevice.setText("");
        txtEfrisUser.setText("");
        txtEfrisPass.setText("");
        txtEfrisBranch.setText("");
    }

    private void saveData() {
        // minimal validation
        String name = txtName.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Business Name is required.");
            txtName.requestFocus();
            return;
        }

        Business b = (current != null) ? current : new Business();
        b.name = name;
        b.tin = emptyNull(txtTIN.getText());
        b.branchCode = emptyNull(txtBranch.getText());
        b.addressLine = emptyNull(txtAddress.getText());
        b.city = emptyNull(txtCity.getText());
        b.country = emptyNull(txtCountry.getText().isBlank() ? "Uganda" : txtCountry.getText());
        b.phone = emptyNull(txtPhone.getText());
        b.email = emptyNull(txtEmail.getText());
        b.currency = emptyNull(txtCurrency.getText().isBlank() ? "UGX" : txtCurrency.getText());
        b.vatRate = parseDoubleOrNull(txtVatRate.getText());

        b.efrisDeviceNo = emptyNull(txtEfrisDevice.getText());
        b.efrisUsername = emptyNull(txtEfrisUser.getText());
        b.efrisPassword = new String(txtEfrisPass.getPassword()); // TODO: encrypt/hash before persist
        if (b.efrisPassword != null && b.efrisPassword.isBlank()) b.efrisPassword = null;
        b.efrisBranchId = emptyNull(txtEfrisBranch.getText());

        try {
            if (b.id == null) {
                long id = BusinessDAO.insert(b);
                b.id = id;
                current = b;
                JOptionPane.showMessageDialog(this, "Business saved (ID " + id + ").");
            } else {
                BusinessDAO.update(b);
                JOptionPane.showMessageDialog(this, "Business updated.");
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
        }
    }

    // helpers
    private static String nv(String s) { return s == null ? "" : s; }
    private static String emptyNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
    private static String noComma(Double d) {
        if (d == null) return "";
        String s = String.format(java.util.Locale.US, "%.2f", d);
        return s.replace(",", "");
    }
    private static Double parseDoubleOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }
}