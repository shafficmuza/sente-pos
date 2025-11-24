package com.promedia.sentepos.efris;

import com.promedia.sentepos.model.*;
import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.dao.EfrisDAO;
import com.promedia.sentepos.print.Json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class EfrisPayloadBuilder {

        /**
     * Build inner T109 invoice JSON based on your DB models (Sale + SaleItem + Business).
     *
     * This is an OFFLINE-friendly T109-like payload. The outer envelope (Base64 + globalInfo)
     * is handled by EfrisClient; here we only build the inner JSON.
     */
    // inside com.promedia.sentepos.efris.EfrisPayloadBuilder

// Build a proper T109 invoice payload for OFFLINE TCS
public static String buildInvoicePayload(long saleId, Sale sale, Payment payment) throws Exception {
    Business b = BusinessDAO.loadSingle();
    if (b == null) {
        throw new IllegalStateException("Business not configured.");
    }

    // Basic totals from sale items
    double subtotal = 0.0;
    double vatTotal = 0.0;
    for (SaleItem it : sale.items) {
        subtotal += it.lineTotal;
        vatTotal += it.vatAmount;
    }
    double total = subtotal + vatTotal;

    String invoiceNo   = "INV-" + saleId;  // you can later switch to your receipt number
    String issuedDate  = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String referenceNo = "POS-" + saleId;

    Map<String, Object> root = new HashMap<>();

    // ---------------- sellerDetails ----------------
    Map<String, Object> seller = new HashMap<>();
    seller.put("tin", nz(b.tin));
    seller.put("ninBrn", nz(b.branchCode));                     // BRN / branch id
    seller.put("legalName", nz(b.name));                        // legal name
    seller.put("businessName", nz(b.name));                     // trade name (same for now)
    seller.put("address", nz(b.addressLine));                   // physical address
    seller.put("mobilePhone", nz(b.phone));                     // mobile
    seller.put("linePhone", nz(b.phone));                       // landline (reuse for now)
    seller.put("emailAddress", nz(b.email));
    seller.put("placeOfBusiness", nz(b.city, "Kampala"));
    seller.put("referenceNo", referenceNo);
    root.put("sellerDetails", seller);

    // ---------------- basicInformation ----------------
    Map<String, Object> basic = new HashMap<>();
    basic.put("invoiceNo", invoiceNo);
    basic.put("antifakeCode", "");                              // filled by EFRIS in response
    basic.put("deviceNo", nz(b.efrisDeviceNo));
    basic.put("issuedDate", issuedDate);
    basic.put("operator", nz(b.efrisUsername, "admin"));
    basic.put("currency", nz(b.currency, "UGX"));
    basic.put("invoiceType", "1");                              // 1 = standard invoice
    basic.put("invoiceKind", "2");                              // 1 = normal invoice 2 = Receipt
    basic.put("dataSource", "103");                             // 103 = POS (per spec)
    basic.put("oriInvoiceId", "");                              // not a credit-note here
    basic.put("payWay", mapPayWay(payment));                    // maps CASH/CARD/MOBILE → code
    root.put("basicInformation", basic);

    // ---------------- buyerDetails ----------------
    Map<String, Object> buyer = new HashMap<>();
    buyer.put("buyerTin", "");
    buyer.put("buyerNinBrn", "");
    buyer.put("buyerPassportNum", "");
    buyer.put("buyerLegalName", "WALK-IN CUSTOMER");
    buyer.put("buyerBusinessName", "");
    buyer.put("buyerAddress", "");
    buyer.put("buyerEmail", "");
    buyer.put("buyerMobilePhone", "");
    buyer.put("buyerLinePhone", "");
    buyer.put("buyerPlaceOfBusi", "");
    buyer.put("buyerType", "1");
    buyer.put("buyerCitizenship", "");
    buyer.put("buyerSector", "");
    buyer.put("buyerReferenceNo", "");
    root.put("buyerDetails", buyer);

    // ---------------- goodsDetails ----------------
    java.util.List<Map<String, Object>> goods = new java.util.ArrayList<>();
    int order = 1;
    for (SaleItem it : sale.items) {
        Map<String, Object> g = new HashMap<>();
        g.put("deemedFlag", "2");                               // 2 = normal
        g.put("discountFlag", "2");                             // 2 = no discount
        g.put("item", nz(it.itemName));
        g.put("itemCode", nz(it.sku));
        g.put("qty", it.qty);
        g.put("unitPrice", it.unitPrice);
        g.put("total", it.lineTotal);                           // net amount
        g.put("tax", it.vatAmount);                             // VAT amount
        g.put("unitOfMeasure", "10");                           // generic pcs; adjust later
        g.put("taxRate", it.vatRate / 100.0);                   // 18% → 0.18
        g.put("discountTaxRate", 0.0);
        g.put("orderNumber", order++);
        g.put("exciseFlag", "2");
        g.put("categoryId", "");
        g.put("categoryName", "");
        g.put("goodsCategoryId", "");
        g.put("goodsCategoryName", "");
        goods.add(g);
    }
    root.put("goodsDetails", goods);

    // ---------------- taxDetails ----------------
    Map<String, Object> tax = new HashMap<>();
    tax.put("taxCategory", "VAT");
    tax.put("netAmount", subtotal);
    tax.put("taxAmount", vatTotal);
    tax.put("grossAmount", total);
    tax.put("exciseUnit", "");
    tax.put("exciseCurrency", nz(b.currency, "UGX"));
    java.util.List<Map<String, Object>> taxDetails = new java.util.ArrayList<>();
    taxDetails.add(tax);
    root.put("taxDetails", taxDetails);

    // ---------------- summary ----------------
    Map<String, Object> summary = new HashMap<>();
    summary.put("netAmount", subtotal);
    summary.put("taxAmount", vatTotal);
    summary.put("grossAmount", total);
    summary.put("itemCount", sale.items.size());
    summary.put("modeCode", "0");
    summary.put("remarks", nz(sale.note, "POS sale"));
    summary.put("qrCode", "");                                  // filled in response
    root.put("summary", summary);

    // ---------------- extend ----------------
    Map<String, Object> extend = new HashMap<>();
    extend.put("reason", "");
    extend.put("reasonCode", "");
    root.put("extend", extend);

    // Final JSON string
    return Json.stringify(root);
}
// imports not required; pure String building

private static String esc(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
}

public static String buildCreditNotePayload(long creditNoteId, com.promedia.sentepos.dao.CreditNoteDAO.Head head,
        java.util.List<com.promedia.sentepos.dao.CreditNoteDAO.Item> items) throws Exception {

    Business b = BusinessDAO.loadSingle();
    if (b == null) throw new IllegalStateException("Business not configured.");

    // Look up original fiscalised invoice (if any)
    EfrisDAO.Rec inv = null;
    if (head != null) {
        try { inv = EfrisDAO.findBySaleId(head.sale_id); }
        catch (Exception ignore) {}
    }

    double subtotal = 0.0;
    double vatTotal = 0.0;
    for (var it : items) {
        subtotal += it.line_total;
        vatTotal += it.vat_amount;
    }
    double total = subtotal + vatTotal;

    String creditNo   = "CN-" + creditNoteId;
    String issuedDate = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    String referenceNo = "CN-" + creditNoteId;

    Map<String,Object> root = new HashMap<>();

    // -------- sellerDetails (same as invoice) ----------
    Map<String,Object> seller = new HashMap<>();
    seller.put("tin", nz(b.tin));
    seller.put("ninBrn", nz(b.branchCode));
    seller.put("legalName", nz(b.name));
    seller.put("businessName", nz(b.name));
    seller.put("address", nz(b.addressLine));
    seller.put("mobilePhone", nz(b.phone));
    seller.put("linePhone", nz(b.phone));
    seller.put("emailAddress", nz(b.email));
    seller.put("placeOfBusiness", nz(b.city, "Kampala"));
    seller.put("referenceNo", referenceNo);
    root.put("sellerDetails", seller);

    // -------- basicInformation (credit note) ----------
    Map<String,Object> basic = new HashMap<>();
    basic.put("invoiceNo", creditNo);          // local CN number
    basic.put("antifakeCode", "");
    basic.put("deviceNo", nz(b.efrisDeviceNo));
    basic.put("issuedDate", issuedDate);
    basic.put("operator", nz(b.efrisUsername, "admin"));
    basic.put("currency", nz(b.currency, "UGX"));
    basic.put("invoiceType", "2");            // 2 = credit note (per URA spec)
    basic.put("invoiceKind", "2");            // e-receipt
    basic.put("dataSource", "103");           // POS
    // reference original invoice if we have it
    basic.put("oriInvoiceId", "");            // we don’t store this yet
    basic.put("oriInvoiceNo", inv != null ? nz(inv.invoice_number) : "");
    basic.put("reason", nz(head != null ? head.reason : "", "Credit note"));
    basic.put("payWay", "102");               // same as original; not critical here
    root.put("basicInformation", basic);

    // -------- buyerDetails (walk-in) ----------
    Map<String,Object> buyer = new HashMap<>();
    buyer.put("buyerTin", "");
    buyer.put("buyerNinBrn", "");
    buyer.put("buyerLegalName", "WALK-IN CUSTOMER");
    buyer.put("buyerType", "1");
    root.put("buyerDetails", buyer);

    // -------- goodsDetails (items being returned) ----------
    java.util.List<Map<String,Object>> goods = new java.util.ArrayList<>();
    int order = 1;
    for (var it : items) {
        Map<String,Object> g = new HashMap<>();
        g.put("deemedFlag", "2");
        g.put("discountFlag", "2");
        g.put("item", nz(it.item_name));
        g.put("itemCode", nz(it.sku));
        g.put("qty", it.qty);                 // positive quantity; CN context tells URA it’s a return
        g.put("unitPrice", it.unit_price);
        g.put("total", it.line_total);
        g.put("tax", it.vat_amount);
        g.put("unitOfMeasure", "10");
        g.put("taxRate", it.vat_rate / 100.0);
        g.put("discountTaxRate", 0.0);
        g.put("orderNumber", order++);
        g.put("exciseFlag", "2");
        g.put("categoryId", "");
        g.put("categoryName", "");
        g.put("goodsCategoryId", "");
        g.put("goodsCategoryName", "");
        goods.add(g);
    }
    root.put("goodsDetails", goods);

    // -------- taxDetails ----------
    Map<String,Object> tax = new HashMap<>();
    tax.put("taxCategory", "VAT");
    tax.put("netAmount", subtotal);
    tax.put("taxAmount", vatTotal);
    tax.put("grossAmount", total);
    tax.put("exciseUnit", "");
    tax.put("exciseCurrency", nz(b.currency, "UGX"));
    java.util.List<Map<String,Object>> taxDetails = new java.util.ArrayList<>();
    taxDetails.add(tax);
    root.put("taxDetails", taxDetails);

    // -------- summary ----------
    Map<String,Object> summary = new HashMap<>();
    summary.put("netAmount", subtotal);
    summary.put("taxAmount", vatTotal);
    summary.put("grossAmount", total);
    summary.put("itemCount", items.size());
    summary.put("modeCode", "1");  // CN
    summary.put("remarks", nz(head != null ? head.reason : "", "Credit note"));
    summary.put("qrCode", "");     // filled by EFRIS response
    root.put("summary", summary);

    // -------- extend ----------
    Map<String,Object> extend = new HashMap<>();
    extend.put("reason", nz(head != null ? head.reason : "", "Credit note"));
    extend.put("reasonCode", "");
    root.put("extend", extend);

    return Json.stringify(root);
}

public static String buildCreditNoteCancelPayload(
        long creditNoteId,
        com.promedia.sentepos.dao.CreditNoteDAO.Head head,
        String reason
) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("{\"type\":\"CREDIT_NOTE_CANCEL\"");
    sb.append(",\"creditNoteId\":").append(creditNoteId);
    sb.append(",\"saleId\":").append(head != null ? head.sale_id : 0);
    sb.append(",\"reason\":\"").append(esc(reason)).append("\"}");
    return sb.toString();
}
    
    private static String n(String s){ return (s==null || s.isBlank()) ? null : s; }
    
        /** Join address fields from Business into a single string. */
    private static String joinAddress(Business b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder();
        if (b.addressLine != null && !b.addressLine.isBlank()) sb.append(b.addressLine);
        if (b.city != null && !b.city.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(b.city);
        }
        if (b.country != null && !b.country.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(b.country);
        }
        return sb.toString();
    }
    
    // --------- helpers for T109 payload ---------

    // null-to-empty
    private static String nz(String s) {
        return (s == null) ? "" : s;
    }

// prefer first non-blank, else second, else ""
private static String nz(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return (b == null) ? "" : b;
}

// Map your Payment.Method to EFRIS payWay codes
private static String mapPayWay(com.promedia.sentepos.model.Payment payment) {
    // default to CASH
    String def = "102"; // 102 = CASH (per EFRIS code table)

    if (payment == null || payment.method == null) {
        return def;
    }

    switch (payment.method) {
        case CASH:
            return "102"; // cash
        case CARD:
            return "103"; // card / bank card (adjust if your spec differs)
        case MOBILE:
            return "104"; // mobile money
        default:
            return def;
    }
}
}