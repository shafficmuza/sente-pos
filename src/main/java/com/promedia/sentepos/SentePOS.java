package com.promedia.sentepos;

import com.promedia.sentepos.db.Migrations;
import com.promedia.sentepos.license.LicenseManager;
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
        
        
        MomoClient client = new MomoClient(
            COLLECTION_BASE_URL,
            SUBSCRIPTION_KEY,
            API_USER_ID,
            API_KEY,
            TARGET_ENVIRONMENT
    );

    try {
        String token = client.getAccessToken();
        System.out.println("Access token OK");

        // 1) Send a payment prompt to the phone
        String referenceId = client.requestToPay(
                token,
                "1000",
                "EUR",
                "256775200442",          // <-- put a test MSISDN here (digits only)
                "SENTE_LICENSE_0001",    // externalId (your own reference)
                "Pay SentePOS License",
                "SentePOS"
        );

        System.out.println("ReferenceId = " + referenceId);

        // 2) Poll status until it becomes SUCCESSFUL / FAILED / REJECTED
        for (int i = 0; i < 30; i++) {     // 30 tries
            Thread.sleep(3000);            // every 3 seconds

            var statusJson = client.getTransactionStatus(token, referenceId);
            System.out.println("STATUS JSON: " + statusJson.toPrettyString());

            String status = statusJson.hasNonNull("status")
                    ? statusJson.get("status").asText("")
                    : "";

            if ("SUCCESSFUL".equalsIgnoreCase(status)
                    || "FAILED".equalsIgnoreCase(status)
                    || "REJECTED".equalsIgnoreCase(status)) {
                System.out.println("FINAL STATUS = " + status);
                break;
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
        ///////////////Momo Clien End//////////////
        
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
           // Login lf = new Login();
           // lf.setLocationRelativeTo(null);
           // lf.setVisible(true);
        });
        
        ////////////// MOMO Client  Start/////////////
        ///
        // Base host (NO /collection/v1_0 here)
    
        
        
    
    
    
    }
}
