package com.promedia.sentepos.print;

import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.dao.CreditNoteDAO;
import com.promedia.sentepos.dao.EfrisDAO;
import com.promedia.sentepos.model.Business;


import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** 58mm CREDIT NOTE printer + preview (EFRIS-compliant block). */
public class CreditNotePrinter implements Printable {

    // --------- Factory: build from DB (no checked exceptions leak) ----------
    public static CreditNotePrinter usingCreditNoteFromDb(long creditNoteId) {
        Business b = null;                  // <-- initialize, don't call here
        CreditNoteDAO.Head h = null;
        List<CreditNoteDAO.Item> items = null;
        EfrisDAO.Rec e = null;

        try { b = BusinessDAO.loadSingle(); } catch (Exception ignored) {}
        try { h = CreditNoteDAO.findHead(creditNoteId); } catch (Exception ignored) {}
        try { items = CreditNoteDAO.listItems(creditNoteId); } catch (Exception ignored) {}
       
        

        // Business header
        String name   = b != null ? nvl(b.name, "SentePOS") : "SentePOS";
        String addr   = join(", ", get(b, b!=null?b.addressLine:null), get(b, b!=null?b.city:null), get(b, b!=null?b.country:null));
        String phone  = get(b, b!=null?b.phone:null);
        String tin    = get(b, b!=null?b.tin:null);
        String branch = get(b, b!=null?b.branchCode:null);
        String device = get(b, b!=null?b.efrisDeviceNo:null);

        // EFRIS fields
        String status  = e != null ? e.status : "PENDING";
        String fdn     = e != null ? e.invoice_number : null;       // FDN / invoice_number
        String vcode   = e != null ? e.verification_code : null;    // Verification Code
        String qrB64   = e != null ? e.qr_base64 : null;

        // Fallbacks if head is missing
        String dateStr = h != null ? h.date_time : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date());
        String reason  = h != null ? nvl(h.reason, "") : "";

        double sub = h != null ? h.subtotal : 0.0;
        double vat = h != null ? h.vat_total : 0.0;
        double tot = h != null ? h.total    : 0.0;

        return new CreditNotePrinter(creditNoteId, name, addr, phone, tin, branch, device,
                                     dateStr, reason, sub, vat, tot, items, status, fdn, vcode, qrB64);
    }
    
    // Let callers ask for preview by id + any AWT Component
public static void previewFor(long creditNoteId, java.awt.Component parent) {
    CreditNotePrinter p = usingCreditNoteFromDb(creditNoteId);
    p.preview(parent);
}

// Your dialog calls this exact signature; delegate to the Component overload
public static void previewFor(long creditNoteId, com.promedia.sentepos.ui.CreditNoteDialog parent) {
    previewFor(creditNoteId, (java.awt.Component) parent);
}
    
    // Example helper inside CreditNotePrinter:
    private static class CreditNoteBundle {
        CreditNoteDAO.Head head;
        java.util.List<CreditNoteDAO.Item> items;
        EfrisDAO.Rec efris; // optional: if you want to show original sale’s IRN/QR
    }

    private static CreditNoteBundle loadBundle(long creditNoteId) throws Exception {
        CreditNoteBundle b = new CreditNoteBundle();
        b.head  = CreditNoteDAO.findHead(creditNoteId);
        if (b.head == null) throw new IllegalArgumentException("Credit note not found: " + creditNoteId);
        b.items = CreditNoteDAO.listItems(creditNoteId);

        // If you want to display the original sale’s EFRIS IRN/QR on the credit note:
        b.efris = EfrisDAO.findBySaleId(b.head.sale_id); // may be null
        return b;
    }

    // ----------------------- Instance state -----------------------
    private final long creditNoteId;
    private final String shopName, shopAddress, shopPhone, shopTin, branchCode, deviceNo;
    private final String dateTime, reason;
    private final double subtotal, vatTotal, total;
    private final List<CreditNoteDAO.Item> items;

    // EFRIS
    private final String efrisStatus;          // PENDING | SENT | FAILED | CANCELLED
    private final String efrisInvoiceNumber;   // FDN / invoice_number
    private final String efrisVerification;    // verification_code
    private final String efrisQrBase64;        // qr_base64

    // layout
    private static final double PAPER_WIDTH_INCH = 2.28; // 58mm
    private static final int LEFT_PAD = 10;
    private final Font font = new Font(Font.MONOSPACED, Font.PLAIN, 9);
    private final Font fontBold = new Font(Font.MONOSPACED, Font.BOLD, 9);

    public CreditNotePrinter(long creditNoteId,
                             String shopName, String shopAddress, String shopPhone, String shopTin,
                             String branchCode, String deviceNo,
                             String dateTime, String reason,
                             double subtotal, double vatTotal, double total,
                             List<CreditNoteDAO.Item> items,
                             String efrisStatus, String efrisInvoiceNumber, String efrisVerification, String efrisQrBase64) {
        this.creditNoteId = creditNoteId;
        this.shopName = shopName;
        this.shopAddress = shopAddress;
        this.shopPhone = shopPhone;
        this.shopTin = shopTin;
        this.branchCode = branchCode;
        this.deviceNo = deviceNo;
        this.dateTime = dateTime;
        this.reason = reason;
        this.subtotal = subtotal;
        this.vatTotal = vatTotal;
        this.total = total;
        this.items = items;
        this.efrisStatus = nvl(efrisStatus, "PENDING");
        this.efrisInvoiceNumber = efrisInvoiceNumber;
        this.efrisVerification = efrisVerification;
        this.efrisQrBase64 = efrisQrBase64;
    }

    // --------------------------- Preview UI ---------------------------
    public void preview(Component parent) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(parent), "Credit Note Preview", Dialog.ModalityType.MODELESS);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel paper = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                try {
                    PrinterJob job = PrinterJob.getPrinterJob();
                    PageFormat pf = job.defaultPage();
                    Paper p = new Paper();
                    double widthPts = PAPER_WIDTH_INCH * 72.0;
                    double heightPts = 1600;
                    p.setSize(widthPts, heightPts);
                    p.setImageableArea(0, 0, widthPts, heightPts);
                    pf.setPaper(p);
                    CreditNotePrinter.this.print(g, pf, 0);
                } catch (Exception ignore) {}
            }
            @Override public Dimension getPreferredSize() {
                int w = (int)(PAPER_WIDTH_INCH * 72.0);
                return new Dimension(w + 40, 1300);
            }
        };

        JButton btnPrint = new JButton("Print");
        btnPrint.addActionListener(e -> {
            try { this.print((String) null); }
            catch (PrinterException ex) { JOptionPane.showMessageDialog(dlg, "Print failed: " + ex.getMessage()); }
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btnPrint);

        dlg.getContentPane().setLayout(new BorderLayout());
        dlg.getContentPane().add(new JScrollPane(paper), BorderLayout.CENTER);
        dlg.getContentPane().add(south, BorderLayout.SOUTH);
        dlg.setSize(420, 720);
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }

    // ---------------------------- Print ----------------------------
    public void print(String preferredPrinterName) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();

        PageFormat pf = job.defaultPage();
        Paper paper = new Paper();
        double widthPts = PAPER_WIDTH_INCH * 72.0;
        double heightPts = 1600;
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

        // Shop header
        g2.setFont(fontBold);
        y = drawCentered(g2, nvl(shopName, "SentePOS"), y, width);
        g2.setFont(font);
        if (notBlank(shopAddress)) y = drawWrappedCentered(g2, shopAddress, y, width, 28);
        if (notBlank(shopPhone))   y = drawCentered(g2, "Tel: " + shopPhone, y, width);
        if (notBlank(shopTin))     y = drawCentered(g2, "TIN: " + shopTin, y, width);
        if (notBlank(branchCode))  y = drawCentered(g2, "Branch: " + branchCode, y, width);
        if (notBlank(deviceNo))    y = drawCentered(g2, "Device: " + deviceNo, y, width);

        // Title
        y += 8;
        g2.setFont(fontBold);
        y = drawCentered(g2, "CREDIT NOTE", y, width);
        g2.setFont(font);

        y += 6; drawLine(g2, y, width); y += 8;

        // Meta
        y = drawText(g2, "CN: " + creditNoteId, LEFT_PAD, y);
        y = drawText(g2, nvl(dateTime, new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date())), LEFT_PAD, y);
        if (notBlank(reason)) y = drawText(g2, "Reason: " + reason, LEFT_PAD, y);
        drawLine(g2, y, width); y += 8;

        // Items header
        y = drawText(g2, "Item                 Qty   Price    Amount", LEFT_PAD, y);
        y = drawText(g2, "-------------------------------------------", LEFT_PAD, y);

        // Items (display amounts as negative)
        double sub = 0.0, vat = 0.0;
        if (items != null) {
            for (CreditNoteDAO.Item it : items) {
                String name  = safe(it.item_name, 18);
                String qty   = fmt0(it.qty);
                String price = fmt0(it.unit_price);
                double line  = it.line_total;
                double v     = it.vat_amount;

                y = drawText(g2, padRight(name, 18), LEFT_PAD, y);
                String amount = "-" + fmt0(line + v);
                String line2 = padLeft(qty, 4) + " x " + padLeft(price, 6) + "  " + padLeft(amount, 7);
                y = drawText(g2, line2, LEFT_PAD, y);

                sub += line;
                vat += v;
            }
        }

        drawLine(g2, y, width); y += 8;

        // Totals (negative display)
        y = drawKV(g2, "Subtotal:", "-" + fmt0(sub), LEFT_PAD, width, y);
        y = drawKV(g2, "VAT:",      "-" + fmt0(vat), LEFT_PAD, width, y);
        g2.setFont(fontBold);
        y = drawKV(g2, "TOTAL:",    "-" + fmt0(sub + vat), LEFT_PAD, width, y);
        g2.setFont(font);

        // EFRIS block
        y += 8; drawLine(g2, y, width); y += 8;
        g2.setFont(fontBold);
        y = drawCentered(g2, "EFRIS", y, width);
        g2.setFont(font);
        y = drawCentered(g2, "Status: " + nvl(efrisStatus, "PENDING"), y, width);
        if (notBlank(efrisInvoiceNumber)) y = drawCentered(g2, "FDN: " + efrisInvoiceNumber, y, width);
        if (notBlank(efrisVerification))  y = drawCentered(g2, "Verification: " + efrisVerification, y, width);

        // QR
        if (notBlank(efrisQrBase64)) {
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
    private static String nvl(String s, String d){ return (s==null || s.isBlank())? d : s; }
    private static boolean notBlank(String s){ return s!=null && !s.isBlank(); }
    private static String get(Object o, String v){ return v; } // just clarity
    private static String join(String sep, String... parts){
        StringBuilder sb = new StringBuilder();
        for (String p : parts) if (notBlank(p)) { if (sb.length()>0) sb.append(sep); sb.append(p); }
        return sb.toString();
    }
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
        if (!notBlank(text)) return y;
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
    private static BufferedImage decodeBase64Image(String b64) {
        try { return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64))); }
        catch (Exception e) { return null; }
    }
}