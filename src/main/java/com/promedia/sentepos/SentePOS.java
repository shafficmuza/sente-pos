package com.promedia.sentepos;

import com.promedia.sentepos.db.Migrations;
import com.promedia.sentepos.license.LicenseManager;
import static com.promedia.sentepos.license.LicenseManager.isLicensedNow;
import com.promedia.sentepos.momo.MomoClient;
import com.promedia.sentepos.ui.ActivationWindow;
import com.promedia.sentepos.ui.Login;
import com.promedia.sentepos.ui.LoginFrame;
import com.promedia.sentepos.util.AppLog;

import javax.swing.*;

public class SentePOS {
    
    private static final String MOMO_HOST = "https://sandbox.momodeveloper.mtn.com";

    // Product base path
    private static final String COLLECTION_BASE_URL = MOMO_HOST + "/collection/v1_0";

    // Subscription key (Primary)
    private static final String SUBSCRIPTION_KEY = "9dc12346168343468b0ef45536ba2953";

    // API user + key (yours that worked)
    private static final String API_USER_ID = "b4a4c5a8-0d57-4b18-b056-5e6f1c3da7c9";
    private static final String API_KEY     = "39c0b5ea4916450d84f715c95ddc27b6";
    
    // sandbox / production
    private static final String TARGET_ENVIRONMENT = "sandbox";
    
    

    
    public static void main(String[] args) {
        AppLog.init(); // ← creates Logs/ and writes a “Logger initialized” line
            // Delete the line below to enable the License again.
        Migrations.ensure();
        
       if (!LicenseManager.isLicensedNow()) {
    javax.swing.SwingUtilities.invokeLater(() -> {
        com.promedia.sentepos.ui.ActivationWindow aw = new com.promedia.sentepos.ui.ActivationWindow();
        aw.setLocationRelativeTo(null);
        aw.setVisible(true);
    });
    return;
}
    
        SwingUtilities.invokeLater(() -> {
            Login lf = new Login();
            lf.setLocationRelativeTo(null);
            lf.setVisible(true);
        });
        
        
        
      
    }
    
    public static boolean isLicensed() {
    return isLicensedNow();
}
}
