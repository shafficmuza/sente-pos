package com.promedia.sentepos;

import com.promedia.sentepos.db.Migrations;
import com.promedia.sentepos.ui.Login;
import com.promedia.sentepos.ui.LoginFrame;

import javax.swing.*;

public class SentePOS {
    public static void main(String[] args) {
        Migrations.ensure();
        SwingUtilities.invokeLater(() -> {
            Login lf = new Login();
            lf.setLocationRelativeTo(null);
            lf.setVisible(true);
        });
    }
}
