package com.promedia.sentepos.ui;

import javax.swing.*;
import java.awt.*;

public class SalesFiscalisationDialog extends JDialog {
    public SalesFiscalisationDialog(Window owner) {
        super(owner, "Sales Fiscalisation", ModalityType.MODELESS);
        getContentPane().add(new SalesFiscalisationPanel());
        setSize(950, 600);
        setLocationRelativeTo(owner);
    }

    public static void open(Window owner) {
        new SalesFiscalisationDialog(owner).setVisible(true);
    }
}