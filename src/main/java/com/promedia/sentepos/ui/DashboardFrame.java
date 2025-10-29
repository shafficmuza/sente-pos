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
        center.add(btnPOS);

        center.add(btnAddProduct);
        center.add(btnProducts);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }
}
