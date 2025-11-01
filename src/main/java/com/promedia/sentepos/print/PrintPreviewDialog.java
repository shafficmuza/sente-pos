package com.promedia.sentepos.print;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;

public class PrintPreviewDialog extends JDialog {

    private final Printable printable;
    private final PageFormat pageFormat;

    private final JLabel imageLabel = new JLabel();
    private final JSlider zoom = new JSlider(50, 200, 100); // %
    private BufferedImage pageImage;

    public PrintPreviewDialog(Window owner, String title, Printable printable, PageFormat pageFormat) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.printable = printable;
        this.pageFormat = pageFormat;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JButton btnClose = new JButton("Close");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Zoom:"));
        zoom.setPreferredSize(new Dimension(160, 22));
        top.add(zoom);
        top.add(Box.createHorizontalStrut(8));
        top.add(btnClose);
        add(top, BorderLayout.NORTH);

        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(new JScrollPane(imageLabel), BorderLayout.CENTER);

        zoom.addChangeListener(e -> refreshPreview());
        btnClose.addActionListener(e -> dispose());

        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) { renderPage(); }
        });

        setSize(420, 700);
        setLocationRelativeTo(owner);
    }

    private void renderPage() {
        int w = (int) Math.ceil(pageFormat.getImageableWidth());
        int h = (int) Math.ceil(pageFormat.getImageableHeight());
        if (w <= 0 || h <= 0) { w = 300; h = 1200; }

        pageImage = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2 = pageImage.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(Color.WHITE); g2.fillRect(0, 0, w, h);
            g2.setColor(Color.BLACK);
            int res = printable.print(g2, pageFormat, 0);
            if (res != Printable.PAGE_EXISTS) {
                Graphics2D g = pageImage.createGraphics();
                g.setColor(Color.RED);
                g.drawString("No content (pageIndex 0).", 10, 20);
                g.dispose();
            }
        } catch (PrinterException ex) {
            Graphics2D g = pageImage.createGraphics();
            g.setColor(Color.RED);
            g.drawString("Render error: " + ex.getMessage(), 10, 20);
            g.dispose();
        } finally {
            g2.dispose();
        }
        refreshPreview();
    }

    private void refreshPreview() {
        if (pageImage == null) return;
        double z = zoom.getValue() / 100.0;
        int sw = (int) Math.max(1, Math.round(pageImage.getWidth() * z));
        int sh = (int) Math.max(1, Math.round(pageImage.getHeight() * z));
        Image scaled = pageImage.getScaledInstance(sw, sh, Image.SCALE_SMOOTH);
        imageLabel.setIcon(new ImageIcon(scaled));
        imageLabel.revalidate();
    }
}