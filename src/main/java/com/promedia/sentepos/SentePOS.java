package com.promedia.sentepos;

import com.promedia.sentepos.db.Migrations;
import com.promedia.sentepos.ui.Login;
import com.promedia.sentepos.ui.LoginFrame;
import com.promedia.sentepos.util.AppLog;

import javax.swing.*;

public class SentePOS {
    public static void main(String[] args) {
        AppLog.init(); // ← creates Logs/ and writes a “Logger initialized” line
        Migrations.ensure();
        SwingUtilities.invokeLater(() -> {
            Login lf = new Login();
            lf.setLocationRelativeTo(null);
            lf.setVisible(true);
        });
    }
}
