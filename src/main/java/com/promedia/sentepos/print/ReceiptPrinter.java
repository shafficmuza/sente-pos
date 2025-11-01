package com.promedia.sentepos.print;

import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.dao.EfrisDAO;
import com.promedia.sentepos.model.Payment;
import com.promedia.sentepos.model.Sale;
import com.promedia.sentepos.model.SaleItem;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 58mm receipt that reads Business & EFRIS fields using camelCase OR snake_case via reflection. */
public class ReceiptPrinter implements Printable {

    // ---------- factory (load Business/EFIRS with flexible field names) ----------
public static ReceiptPrinter usingBusinessFromDb(Sale sale, Payment payment, long saleId) {
    Object b = null;
    Object rec = null;
    try {
        b = com.promedia.sentepos.dao.BusinessDAO.loadSingle();
    } catch (Exception e) { // <-- was SQLException
        System.err.println("WARN: Failed to load Business: " + e.getMessage());
    }
    try {
        rec = com.promedia.sentepos.dao.EfrisDAO.findBySaleId(saleId);
    } catch (Exception e) { // <-- was SQLException
        System.err.println("INFO: No EFRIS record for sale " + saleId + ": " + e.getMessage());
    }

    String shopName   = str(b, "name");
    if (isBlank(shopName)) shopName = "SentePOS";

    String addressLine = str(b, "addressLine", "address_line");
    String city        = str(b, "city");
    String country     = str(b, "country");
    String shopAddress = joinNonBlank(", ", addressLine, city, country);

    String phone      = str(b, "phone");
    String tin        = str(b, "tin");
    String branchCode = str(b, "branchCode", "branch_code");
    String deviceNo   = str(b, "efrisDeviceNo", "efris_device_no");

    String invoiceNo  = str(rec, "invoiceNumber", "invoice_number");
    String qrBase64   = str(rec, "qrBase64", "qr_base64");
    String status     = str(rec, "status");
    String verCode    = str(rec, "verificationCode", "verification_code"); // NEW

   return new ReceiptPrinter(
    shopName, shopAddress, phone, tin, branchCode, deviceNo,
    sale, payment, saleId,
    invoiceNo, qrBase64, status,
    verCode // NEW
);
}

    // ----------------------- instance fields -----------------------
    private final String shopName, shopAddress, shopPhone, shopTin, branchCode, deviceNo;
    private final Sale sale;
    private final Payment payment;
    private final long saleId;

    // EFRIS
    private final String efrisInvoiceNo;  // invoiceNumber / invoice_number
    private final String efrisQrBase64;   // qrBase64 / qr_base64
    private final String efrisStatus;     // PENDING | SENT | FAILED
    private final String efrisVerificationCode;   // NEW (optional)

    // layout
    private static final double PAPER_WIDTH_INCH = 2.28;
    private static final int LEFT_PAD = 10;
    private final Font font = new Font(Font.MONOSPACED, Font.PLAIN, 9);
    private final Font fontBold = new Font(Font.MONOSPACED, Font.BOLD, 9);

 public ReceiptPrinter(String shopName, String shopAddress, String shopPhone, String shopTin,
                      String branchCode, String deviceNo,
                      Sale sale, Payment payment, long saleId,
                      String efrisInvoiceNo, String efrisQrBase64, String efrisStatus,
                      String efrisVerificationCode) {          // +1 param
            this.shopName = shopName;
            this.shopAddress = shopAddress;
            this.shopPhone = shopPhone;
            this.shopTin = shopTin;
            this.branchCode = branchCode;
            this.deviceNo = deviceNo;
            this.sale = sale;
            this.payment = payment;
            this.saleId = saleId;
            this.efrisInvoiceNo = efrisInvoiceNo;
            this.efrisQrBase64 = efrisQrBase64;
            this.efrisStatus = efrisStatus;
            this.efrisVerificationCode = efrisVerificationCode;        // assign
}

    // --------------------------- Preview ---------------------------
    public void preview(Component parent) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(parent), "Receipt Preview", Dialog.ModalityType.MODELESS);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel paper = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                try {
                    PrinterJob job = PrinterJob.getPrinterJob();
                    PageFormat pf = job.defaultPage();
                    Paper p = new Paper();
                    double widthPts = PAPER_WIDTH_INCH * 72.0;
                    double heightPts = 1400;
                    p.setSize(widthPts, heightPts);
                    p.setImageableArea(0, 0, widthPts, heightPts);
                    pf.setPaper(p);
                    ReceiptPrinter.this.print(g, pf, 0);
                } catch (Exception ignore) {}
            }
            @Override public Dimension getPreferredSize() {
                int w = (int)(PAPER_WIDTH_INCH * 72.0);
                return new Dimension(w + 40, 1200);
            }
        };

        JButton btnPrint = new JButton("Print");
        btnPrint.addActionListener(e -> {
            try { this.print(null); }
            catch (PrinterException ex) { JOptionPane.showMessageDialog(dlg, "Print failed: " + ex.getMessage()); }
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btnPrint);

        dlg.getContentPane().setLayout(new BorderLayout());
        dlg.getContentPane().add(new JScrollPane(paper), BorderLayout.CENTER);
        dlg.getContentPane().add(south, BorderLayout.SOUTH);
        dlg.setSize(420, 700);
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }

    // ---------------------------- Print ----------------------------
    public void print(String preferredPrinterName) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();

        PageFormat pf = job.defaultPage();
        Paper paper = new Paper();
        double widthPts = PAPER_WIDTH_INCH * 72.0;
        double heightPts = 1400;
        paper.setSize(widthPts, heightPts);
        paper.setImageableArea(0, 0, widthPts, heightPts);
        pf.setPaper(paper);
        job.setPrintable(this, pf);

        if (preferredPrinterName != null && !preferredPrinterName.isBlank()) {
            PrintService[] services = PrinterJob.lookupPrintServices();
            for (PrintService ps : services) {
                if (ps.getName().toLowerCase(Locale.ROOT).contains(preferredPrinterName.toLowerCase(Locale.ROOT))) {
                    try { job.setPrintService(ps); } catch (Exception ignore) {}
                    break;
                }
            }
        }
        job.print();
    }

    // ------------------------ Rendering ------------------------
    @Override
    public int print(Graphics g, PageFormat pf, int pageIndex) {
        if (pageIndex > 0) return NO_SUCH_PAGE;
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.BLACK);
        g2.setFont(font);

        int y = 12;
        int width = (int) pf.getImageableWidth();

        // Header
        g2.setFont(fontBold);
        y = drawCentered(g2, nonEmpty(shopName, "SentePOS"), y, width);
        g2.setFont(font);
        if (!isBlank(shopAddress)) y = drawWrappedCentered(g2, shopAddress, y, width, 28);
        if (!isBlank(shopPhone))   y = drawCentered(g2, "Tel: " + shopPhone, y, width);
        if (!isBlank(shopTin))     y = drawCentered(g2, "TIN: " + shopTin, y, width);
        if (!isBlank(branchCode))  y = drawCentered(g2, "Branch: " + branchCode, y, width);
        if (!isBlank(deviceNo))    y = drawCentered(g2, "Device: " + deviceNo, y, width);

        y += 6; drawLine(g2, y, width); y += 8;

        // Sale meta
        y = drawText(g2, "Sale: " + saleId, LEFT_PAD, y);
        y = drawText(g2, new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()), LEFT_PAD, y);
        drawLine(g2, y, width); y += 8;

        // Items header
        y = drawText(g2, "Item                 Qty   Price    Amount", LEFT_PAD, y);
        y = drawText(g2, "-------------------------------------------", LEFT_PAD, y);

        // Items
        List<SaleItem> items = sale.items;
        double sub = 0.0, vat = 0.0;
        for (SaleItem it : items) {
            String name  = safe(it.itemName, 18);
            String qty   = fmt0(it.qty);
            String price = fmt0(it.unitPrice);
            double line  = it.lineTotal;
            double v     = it.vatAmount;

            y = drawText(g2, padRight(name, 18), LEFT_PAD, y);
            String amount = fmt0(line + v);
            String line2 = padLeft(qty, 4) + " x " + padLeft(price, 6) + "  " + padLeft(amount, 7);
            y = drawText(g2, line2, LEFT_PAD, y);

            sub += line;
            vat += v;
        }

        drawLine(g2, y, width); y += 8;
        double total = sub + vat;

        // Totals
        y = drawKV(g2, "Subtotal:", fmt0(sub), LEFT_PAD, width, y);
        y = drawKV(g2, "VAT:",      fmt0(vat), LEFT_PAD, width, y);
        g2.setFont(fontBold);
        y = drawKV(g2, "TOTAL:",    fmt0(total), LEFT_PAD, width, y);
        g2.setFont(font);

        // Payment
        y += 4;
        String method = (payment != null && payment.method != null) ? payment.method.name() : "CASH";
        double paid   = (payment != null) ? payment.amount : total;
        y = drawKV(g2, "Paid (" + method + "):", fmt0(paid), LEFT_PAD, width, y);
        double change = paid - total;
        if (change > 0.0) y = drawKV(g2, "Change:", fmt0(change), LEFT_PAD, width, y);

        // EFRIS block
        y += 8; drawLine(g2, y, width); y += 8;
        g2.setFont(fontBold);
        y = drawCentered(g2, "EFRIS", y, width);
        g2.setFont(font);

        boolean sent = "SENT".equalsIgnoreCase(efrisStatus);
        if (!sent) {
            y = drawCentered(g2, "Status: " + (isBlank(efrisStatus) ? "PENDING" : efrisStatus), y, width);
        } else {
            if (!isBlank(efrisInvoiceNo)) {
                y = drawKV(g2, "FDN:", efrisInvoiceNo, LEFT_PAD, width, y);
            }
            if (!isBlank(efrisVerificationCode)) {               // optional
                y = drawKV(g2, "Verification:", efrisVerificationCode, LEFT_PAD, width, y);
            }
            if (!isBlank(efrisQrBase64)) {
                BufferedImage qr = decodeBase64Image(efrisQrBase64);
                if (qr != null) {
                    int maxW = Math.min(120, width - 20);
                    int drawW = Math.min(qr.getWidth(), maxW);
                    int drawH = (int) (qr.getHeight() * (drawW / (double) qr.getWidth()));
                    int x = (width - drawW) / 2;
                    g2.drawImage(qr, x, y, drawW, drawH, null);
                    y += drawH + 6;
                }
            }
        }
        // QR
        if (!isBlank(efrisQrBase64)) {
            BufferedImage qr = decodeBase64Image(efrisQrBase64);
            if (qr != null) {
                int maxW = Math.min(120, width - 20);
                int drawW = Math.min(qr.getWidth(), maxW);
                int drawH = (int) (qr.getHeight() * (drawW / (double) qr.getWidth()));
                int x = (width - drawW) / 2;
                g2.drawImage(qr, x, y, drawW, drawH, null);
                y += drawH + 6;
            }
        }

        y = drawCentered(g2, "Thank you!", y, width);
        y += 10;
        drawCentered(g2, "Powered by SentePOS", y, width);

        return PAGE_EXISTS;
    }

    // -------------------------- helpers --------------------------
    private static BufferedImage decodeBase64Image(String b64) {
        try { return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64))); }
        catch (Exception e) { return null; }
    }
    private static boolean isBlank(String s){ return s==null || s.isBlank(); }
    private static String nonEmpty(String s, String fallback){ return isBlank(s) ? fallback : s; }
    private static String fmt0(double v){ return String.format(Locale.US, "UGX %, .0f", v).replace(" ,"," "); }
    private static String padLeft(String s, int n){ if(s==null)s=""; if(s.length()>=n)return s; return " ".repeat(n-s.length())+s; }
    private static String padRight(String s,int n){ if(s==null)s=""; if(s.length()>=n)return s.substring(0,n); return s+" ".repeat(n-s.length()); }
    private static String safe(String s,int max){ if(s==null)return ""; return (s.length()>max)? s.substring(0,max): s; }
    private static int drawText(Graphics2D g2,String txt,int x,int y){ g2.drawString(txt,x,y); return y + g2.getFontMetrics().getHeight(); }
    private static int drawCentered(Graphics2D g2,String txt,int y,int w){ int tw=g2.getFontMetrics().stringWidth(txt); int x=Math.max(0,(w-tw)/2); g2.drawString(txt,x,y); return y + g2.getFontMetrics().getHeight(); }
    private static void drawLine(Graphics2D g2,int y,int w){ g2.drawLine(0,y,w,y); }
    private static int drawKV(Graphics2D g2,String k,String v,int x,int w,int y){
        y = drawText(g2, k, x, y);
        int tw = g2.getFontMetrics().stringWidth(v);
        g2.drawString(v, w-10 - tw, y - g2.getFontMetrics().getDescent());
        return y;
    }
    private static int drawWrappedCentered(Graphics2D g2,String text,int y,int width,int maxChars){
        if (isBlank(text)) return y;
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            if (line.length()==0) line.append(w);
            else if (line.length()+1+w.length() <= maxChars) line.append(' ').append(w);
            else { y = drawCentered(g2, line.toString(), y, width); line.setLength(0); line.append(w); }
        }
        if (line.length()>0) y = drawCentered(g2, line.toString(), y, width);
        return y;
    }

    /** Get string field with multiple candidate names (camelCase or snake_case). */
    private static String str(Object o, String... candidates) {
        if (o == null) return null;
        for (String name : candidates) {
            try {
                Field f = o.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(o);
                return v != null ? String.valueOf(v) : null;
            } catch (NoSuchFieldException ignored) { }
            catch (Throwable t) { /* ignore */ }
        }
        return null;
    }

    private static String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!isBlank(p)) {
                if (!sb.isEmpty()) sb.append(sep);
                sb.append(p);
            }
        }
        return sb.toString();
    }
}