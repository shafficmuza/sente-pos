package com.promedia.sentepos;

import com.promedia.sentepos.db.Migrations;
import com.promedia.sentepos.license.LicenseManager;
import com.promedia.sentepos.ui.ActivationWindow;
import com.promedia.sentepos.ui.Login;
import com.promedia.sentepos.ui.LoginFrame;
import com.promedia.sentepos.util.AppLog;

import javax.swing.*;

public class SentePOS {
    public static void main(String[] args) {
        AppLog.init(); // ← creates Logs/ and writes a “Logger initialized” line
        
        /***** Disabling Licensing Feature
        LicenseManager.initializeTrial();
    
    if (!LicenseManager.isTrialActive()) {
        JOptionPane.showMessageDialog(null,
                "Your trial for SentePOS has expired. The software will now close.",
                "Trial Expired",
                JOptionPane.WARNING_MESSAGE);
        ActivationWindow aw = new ActivationWindow();
        aw.setVisible(true);
        
        
    }else{
    
        Migrations.ensure();
        SwingUtilities.invokeLater(() -> {
            Login lf = new Login();
            lf.setLocationRelativeTo(null);
            lf.setVisible(true);
        });
    }
    
    **/
        // Delete the line below to enable the License again.
    Migrations.ensure();
        SwingUtilities.invokeLater(() -> {
            Login lf = new Login();
            lf.setLocationRelativeTo(null);
            lf.setVisible(true);
        });
    
    
    
    }
}
