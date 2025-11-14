package com.promedia.sentepos.ui;

import javax.swing.*;
import java.awt.*;

public class DashboardFrame extends JFrame {
    private final String username;

    public DashboardFrame(String username) {
        super("SentePOS — Dashboard");
        this.username = username;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 500);
        setLocationByPlatform(true);

        JLabel lbl = new JLabel("Welcome, " + username, SwingConstants.LEFT);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 16f));
        JPanel top = new JPanel(new BorderLayout());
        top.add(lbl, BorderLayout.WEST);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnAddProduct = new JButton("Add Product");
        btnAddProduct.addActionListener(e -> {
            AddProductDialog dlg = new AddProductDialog(this);
            dlg.setLocationRelativeTo(this);
            dlg.setVisible(true);
        });

        JButton btnProducts = new JButton("Products");
        btnProducts.addActionListener(e -> {
            ProductsListFrame f = new ProductsListFrame();
            f.setLocationRelativeTo(this);
            f.setVisible(true);
        });
        
        JButton btnPOS = new JButton("Open POS");
        btnPOS.addActionListener(e -> {
            PosFrame pf = new PosFrame();
            pf.setLocationRelativeTo(this);
            pf.setVisible(true);
        });
        
        // inside DashboardFrame after other buttons:
        JButton btnBusinessSetup = new JButton("Business Setup");
        btnBusinessSetup.addActionListener(e -> {
            BusinessSetupFrame f = new BusinessSetupFrame();
            f.setLocationRelativeTo(this);
            f.setVisible(true);
        });
        
        // Example: inside your dashboard init code (e.g., in SentePOS after building the JFrame)
        JButton btnSales = new JButton("Sales");
        btnSales.addActionListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(btnSales);
            JDialog dlg = new SalesListDialog(owner instanceof Frame ? (Frame) owner : null);
            dlg.setLocationRelativeTo(owner);
            dlg.setVisible(true);
        });
        
        JButton btnFiscal = new JButton("Sales Fiscalisation");
        btnFiscal.addActionListener(e -> {
            // open as a dialog
            SalesFiscalisationDialog.open(this);

            // or embed as a tab:
            // yourTabbedPane.addTab("Fiscalisation", new SalesFiscalisationPanel());
        });
        center.add(btnFiscal);

        // Add to whatever container you already have:
        center.add(btnSales);

        center.add(btnBusinessSetup); // or wherever you keep your nav buttons

        
        center.add(btnPOS);

        center.add(btnAddProduct);
        center.add(btnProducts);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }
}
