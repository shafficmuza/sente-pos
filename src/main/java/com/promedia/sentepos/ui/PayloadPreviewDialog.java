package com.promedia.sentepos.ui;

import javax.swing.*;
import java.awt.*;

public class PayloadPreviewDialog extends JDialog {
    public PayloadPreviewDialog(Window owner, String title, String content) {
        super(owner, title, ModalityType.MODELESS);
        JTextArea area = new JTextArea(content == null ? "" : content);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(area);
        getContentPane().add(sp, BorderLayout.CENTER);
        setSize(800, 600);
        setLocationRelativeTo(owner);
    }

    public static void show(Window owner, String title, String content) {
        PayloadPreviewDialog d = new PayloadPreviewDialog(owner, title, content);
        d.setVisible(true);
    }
}