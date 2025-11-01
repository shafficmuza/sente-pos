package com.promedia.sentepos.ui;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {
    private final JTextField txtUser = new JTextField(16);
    private final JPasswordField txtPass = new JPasswordField(16);
    private final JButton btnLogin = new JButton("Login");

    public LoginFrame() {
        super("SentePOS — Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6,6,6,6);
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx=0; gc.gridy=0; p.add(new JLabel("Username:"), gc);
        gc.gridx=1; p.add(txtUser, gc);
        gc.gridx=0; gc.gridy=1; p.add(new JLabel("Password:"), gc);
        gc.gridx=1; p.add(txtPass, gc);
        gc.gridx=1; gc.gridy=2; gc.anchor = GridBagConstraints.EAST; p.add(btnLogin, gc);

        btnLogin.addActionListener(e -> doLogin());
        txtUser.addActionListener(e -> doLogin());
        txtPass.addActionListener(e -> doLogin());
        setContentPane(p);
        pack();
    }

    private void doLogin() {
        String u = txtUser.getText().trim();
        String p = new String(txtPass.getPassword());
        if ("admin".equals(u) && "1234".equals(p)) {
            JOptionPane.showMessageDialog(this, "Login OK");
            DashboardFrame df = new DashboardFrame(u);
            df.setLocationRelativeTo(this);
            df.setVisible(true);
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Invalid credentials");
        }
    }
}
