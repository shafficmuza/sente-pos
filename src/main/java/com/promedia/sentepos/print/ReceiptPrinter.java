package com.promedia.sentepos.print;

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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * 58mm receipt using a fixed 40-column monospace grid for all text.
 */
public class ReceiptPrinter implements Printable {

    // ---------- factory ----------
    public static ReceiptPrinter usingBusinessFromDb(Sale sale, Payment payment, long saleId) {
        Object b = null;
        Object rec = null;
        try { b = com.promedia.sentepos.dao.BusinessDAO.loadSingle(); } catch (Exception ignore) {}
        try { rec = com.promedia.sentepos.dao.EfrisDAO.findBySaleId(saleId); } catch (Exception ignore) {}

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
        String verCode    = str(rec, "verificationCode", "verification_code");

        return new ReceiptPrinter(
                shopName, shopAddress, phone, tin, branchCode, deviceNo,
                sale, payment, saleId,
                invoiceNo, qrBase64, status, verCode
        );
    }

    // ----------------- instance fields -----------------
    private final String shopName, shopAddress, shopPhone, shopTin, branchCode, deviceNo;
    private final Sale sale;
    private final Payment payment;
    private final long saleId;

    private final String efrisInvoiceNo;        // FDN
    private final String efrisQrValue;
    private final String efrisStatus;
    private final String efrisVerificationCode;

    // layout
    private static final double PAPER_WIDTH_INCH = 2.28;
    private static final int LEFT_PAD = 10;

    private static final int TEXT_COLS = 40;     // total chars per line
    private static final int ITEM_NAME_WIDTH = 18;

    private final Font font     = new Font(Font.MONOSPACED, Font.PLAIN, 9);
    private final Font fontBold = new Font(Font.MONOSPACED, Font.BOLD, 9);

    public ReceiptPrinter(String shopName, String shopAddress, String shopPhone, String shopTin,
                          String branchCode, String deviceNo,
                          Sale sale, Payment payment, long saleId,
                          String efrisInvoiceNo, String efrisQrValue,
                          String efrisStatus, String efrisVerificationCode) {
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
        this.efrisQrValue = efrisQrValue;
        this.efrisStatus = efrisStatus;
        this.efrisVerificationCode = efrisVerificationCode;
    }

    // ---------------- Preview ----------------
    public void preview(Component parent) {
        Window owner = SwingUtilities.getWindowAncestor(parent);

        JDialog dlg = new JDialog(
                owner,
                "Receipt Preview",
                Dialog.ModalityType.MODELESS
        );
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setAlwaysOnTop(true);

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
                int w = (int) (PAPER_WIDTH_INCH * 72.0);
                return new Dimension(w + 40, 1200);
            }
        };

        JButton btnPrint = new JButton("Print");
        btnPrint.addActionListener(e -> {
            try { this.print(null); }
            catch (PrinterException ex) {
                JOptionPane.showMessageDialog(dlg, "Print failed: " + ex.getMessage());
            }
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btnPrint);

        dlg.getContentPane().setLayout(new BorderLayout());
        dlg.getContentPane().add(new JScrollPane(paper), BorderLayout.CENTER);
        dlg.getContentPane().add(south, BorderLayout.SOUTH);
        dlg.setSize(420, 700);
        dlg.setLocationRelativeTo(owner != null ? owner : parent);
        dlg.setVisible(true);
        dlg.toFront();
        dlg.requestFocus();
    }

    // ---------------- Print ----------------
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
                if (ps.getName().toLowerCase(Locale.ROOT)
                        .contains(preferredPrinterName.toLowerCase(Locale.ROOT))) {
                    try { job.setPrintService(ps); } catch (Exception ignore) {}
                    break;
                }
            }
        }
        job.print();
    }

    // ---------------- Rendering ----------------
    @Override
    public int print(Graphics g, PageFormat pf, int pageIndex) {
        if (pageIndex > 0) return NO_SUCH_PAGE;
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.BLACK);
        g2.setFont(font);

        int y = 12;

        // ----- HEADER (centered inside 40-char grid) -----
        g2.setFont(fontBold);
        y = drawText(g2, centeredLine(shopName), LEFT_PAD, y);
        g2.setFont(font);
        if (!isBlank(shopAddress)) {
            for (String l : wrap(shopAddress, 28)) {
                y = drawText(g2, centeredLine(l), LEFT_PAD, y);
            }
        }
        if (!isBlank(shopPhone))   y = drawText(g2, centeredLine("Tel: " + shopPhone), LEFT_PAD, y);
        if (!isBlank(shopTin))     y = drawText(g2, centeredLine("TIN: " + shopTin), LEFT_PAD, y);
        if (!isBlank(branchCode))  y = drawText(g2, centeredLine("Branch: " + branchCode), LEFT_PAD, y);
        if (!isBlank(deviceNo))    y = drawText(g2, centeredLine("Device: " + deviceNo), LEFT_PAD, y);

        y += 4;
        y = drawText(g2, lineOf('-'), LEFT_PAD, y);
        y += 4;

        // ----- Sale meta -----
        y = drawText(g2, kvLine("Sale:", String.valueOf(saleId)), LEFT_PAD, y);
        y = drawText(g2, centeredLine(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())), LEFT_PAD, y);
        y = drawText(g2, lineOf('-'), LEFT_PAD, y);

        // ----- Items header -----
        String hdr = String.format(
                "%-" + ITEM_NAME_WIDTH + "s %4s   %7s %8s",
                "Item", "Qty", "Price", "Amount"
        );
        y = drawText(g2, hdr, LEFT_PAD, y);
        y = drawText(g2, lineOf('-'), LEFT_PAD, y);

        // ----- Items (single line per item) -----
        double sub = 0.0, vat = 0.0;
        for (SaleItem it : sale.items) {
            String name   = safe(it.itemName, ITEM_NAME_WIDTH);
            String qty    = fmtQty(it.qty);
            String price  = fmtPlain(it.unitPrice);
            double line   = it.lineTotal;
            double v      = it.vatAmount;
            String amount = fmtPlain(line + v);

            String row = String.format(
                    "%-" + ITEM_NAME_WIDTH + "s %4s x %7s %8s",
                    name, qty, price, amount
            );
            y = drawText(g2, row, LEFT_PAD, y);

            sub += line;
            vat += v;
        }

        y = drawText(g2, lineOf('-'), LEFT_PAD, y);

        double total = sub + vat;

        // ----- Totals (all on one line each, aligned with grid) -----
        y = drawText(g2, kvLine("Subtotal:", fmtMoney(sub)), LEFT_PAD, y);
        y = drawText(g2, kvLine("VAT:",      fmtMoney(vat)), LEFT_PAD, y);
        g2.setFont(fontBold);
        y = drawText(g2, kvLine("TOTAL:",    fmtMoney(total)), LEFT_PAD, y);
        g2.setFont(font);

        // Payment
        y += 2;
        String method = (payment != null && payment.method != null)
                ? payment.method.name()
                : "CASH";
        double paid   = (payment != null) ? payment.amount : total;
        y = drawText(g2, kvLine("Paid (" + method + "):", fmtMoney(paid)), LEFT_PAD, y);
        double change = paid - total;
        if (change > 0.0) {
            y = drawText(g2, kvLine("Change:", fmtMoney(change)), LEFT_PAD, y);
        }

        // ----- EFRIS block -----
        y += 4;
        y = drawText(g2, lineOf('-'), LEFT_PAD, y);
        y += 2;
        g2.setFont(fontBold);
        y = drawText(g2, centeredLine("EFRIS"), LEFT_PAD, y);
        g2.setFont(font);

        boolean sent = "SENT".equalsIgnoreCase(efrisStatus);
        if (!sent) {
            String st = "Status: " + (isBlank(efrisStatus) ? "PENDING" : efrisStatus);
            y = drawText(g2, centeredLine(st), LEFT_PAD, y);
        } else {
            if (!isBlank(efrisInvoiceNo)) {
                y = drawText(g2, kvLine("FDN:", efrisInvoiceNo), LEFT_PAD, y);
            }
            if (!isBlank(efrisVerificationCode)) {
                y = drawText(g2, kvLine("Verification:", efrisVerificationCode), LEFT_PAD, y);
            }

            BufferedImage qr = decodeQrImage(efrisQrValue);
            if (qr != null) {
                int maxW = 140;
                int drawW = Math.min(qr.getWidth(), maxW);
                int drawH = (int) (qr.getHeight() * (drawW / (double) qr.getWidth()));

                int x = LEFT_PAD + (TEXT_COLS * 5 - drawW) / 2;
                g2.drawImage(qr, x, y, drawW, drawH, null);
                y += drawH + 4;
            }
        }

        y = drawText(g2, centeredLine("Thank you!"), LEFT_PAD, y + 2);
        y = drawText(g2, centeredLine("Powered by SentePOS"), LEFT_PAD, y + 2);

        return PAGE_EXISTS;
    }

    // ---------------- helpers ----------------

    private static boolean isBlank(String s){ return s == null || s.isBlank(); }
    private static String nonEmpty(String s,String fb){ return isBlank(s) ? fb : s; }

    private static String fmtMoney(double v){
        return "UGX " + String.format(Locale.US, "%,.0f", v);
    }

    private static String fmtPlain(double v){
        return String.format(Locale.US, "%,.0f", v);
    }

    private static String fmtQty(double q){
        if (Math.abs(q - Math.round(q)) < 0.0001) {
            return String.format(Locale.US, "%d", Math.round(q));
        }
        return String.format(Locale.US, "%.3f", q);
    }

    private static String safe(String s,int max){
        if (s == null) return "";
        return (s.length() > max) ? s.substring(0, max) : s;
    }

    private static String padRight(String s,int n){
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }

    private static String padLeft(String s,int n){
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(s.length() - n);
        return " ".repeat(n - s.length()) + s;
    }

    /** whole line of `-` with TEXT_COLS columns. */
    private static String lineOf(char c){
        return String.valueOf(c).repeat(TEXT_COLS);
    }

    /** Center text in TEXT_COLS columns using spaces. */
    private static String centeredLine(String txt){
        txt = nonEmpty(txt, "");
        if (txt.length() >= TEXT_COLS) return txt.substring(0, TEXT_COLS);
        int pad = (TEXT_COLS - txt.length()) / 2;
        return " ".repeat(pad) + txt;
    }

    /** "key ..... value" in one TEXT_COLS line. */
    private static String kvLine(String key,String value){
        key   = nonEmpty(key, "");
        value = nonEmpty(value, "");
        int keyWidth = 13; // tuned so value column lines up nicely with item Amount column
        if (key.length() > keyWidth) key = key.substring(0, keyWidth);
        String left  = padRight(key, keyWidth);
        String right = padLeft(value, TEXT_COLS - keyWidth);
        return left + right;
    }

    private static int drawText(Graphics2D g2,String txt,int x,int y){
        g2.drawString(txt, x, y);
        return y + g2.getFontMetrics().getHeight();
    }

    private static BufferedImage decodeQrImage(String value) {
        if (isBlank(value)) return null;

        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) return img;
        } catch (Exception ignore) {}

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(value, BarcodeFormat.QR_CODE, 200, 200);
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    img.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            return img;
        } catch (Exception e) {
            System.err.println("QR generation failed: " + e.getMessage());
            return null;
        }
    }

    /** reflection helper */
    private static String str(Object o, String... candidates) {
        if (o == null) return null;
        for (String name : candidates) {
            try {
                Field f = o.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(o);
                return v != null ? String.valueOf(v) : null;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                // ignore
            }
        }
        return null;
    }

    private static String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!isBlank(p)) {
                if (sb.length() > 0) sb.append(sep);
                sb.append(p);
            }
        }
        return sb.toString();
    }

    // simple manual wrapper for address lines
    private static java.util.List<String> wrap(String text,int maxChars){
        java.util.List<String> out = new java.util.ArrayList<>();
        if (isBlank(text)) return out;
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w: words){
            if (line.length()==0){
                line.append(w);
            }else if(line.length()+1+w.length()<=maxChars){
                line.append(' ').append(w);
            }else{
                out.add(line.toString());
                line.setLength(0);
                line.append(w);
            }
        }
        if(line.length()>0) out.add(line.toString());
        return out;
    }
}