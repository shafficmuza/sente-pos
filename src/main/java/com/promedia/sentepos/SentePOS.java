package com.promedia.sentepos;

import com.promedia.sentepos.db.Migrations;
import com.promedia.sentepos.ui.LoginFrame;

import javax.swing.*;

public class SentePOS {
    public static void main(String[] args) {
        Migrations.ensure();
        SwingUtilities.invokeLater(() -> {
            LoginFrame lf = new LoginFrame();
            lf.setLocationRelativeTo(null);
            lf.setVisible(true);
        });
    }
}
