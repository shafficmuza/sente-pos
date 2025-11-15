package com.promedia.sentepos.efris;

import com.promedia.sentepos.model.*;
import com.promedia.sentepos.dao.BusinessDAO;
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

public static String buildInvoicePayload(long saleId, Sale sale, Payment payment) throws Exception {
    Business b = BusinessDAO.loadSingle();
    if (b == null) {
        throw new IllegalStateException("Business not configured.");
    }

    // --- basic totals from sale items ---
    double net = 0.0;
    double vat = 0.0;
    for (SaleItem it : sale.items) {
        net += it.lineTotal;  // amount before VAT
        vat += it.vatAmount;  // VAT amount
    }
    double gross = net + vat;

    // invoice number & date – you can swap to your own receiptNo if you prefer
    String invoiceNo  = "INV" + saleId;
    String issuedDate = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .format(new java.util.Date());

    // payWay code mapping (adjust to your EFRIS code table if needed)
    String payWay = mapPayWay(payment);

    StringBuilder sb = new StringBuilder(2048);
    sb.append('{');

    // =========================================================
    // sellerDetails  (T109 spec – this is what error 2067 wants)
    // =========================================================
    sb.append("\"sellerDetails\":{");
    sb.append("\"tin\":\"").append(esc(nz(b.tin))).append("\",");
    sb.append("\"ninBrn\":\"").append(esc(nz(b.branchCode, b.efrisBranchId))).append("\",");
    sb.append("\"legalName\":\"").append(esc(nz(b.name, "UNKNOWN"))).append("\",");
    sb.append("\"businessName\":\"").append(esc(nz(b.name, "UNKNOWN"))).append("\",");
    sb.append("\"address\":\"").append(esc(nz(b.addressLine, "Kampala, Uganda"))).append("\",");
    sb.append("\"mobilePhone\":\"").append(esc(nz(b.phone, ""))).append("\",");
    sb.append("\"linePhone\":\"").append(esc(nz(b.phone, ""))).append("\",");
    sb.append("\"emailAddress\":\"").append(esc(nz(b.email, ""))).append("\",");
    sb.append("\"placeOfBusiness\":\"").append(esc(nz(b.city, "Kampala"))).append("\",");
    sb.append("\"referenceNo\":\"").append(esc(invoiceNo)).append("\"");
    sb.append("},");

    // =========================================================
    // basicInformation
    // =========================================================
    sb.append("\"basicInformation\":{");
    sb.append("\"invoiceNo\":\"").append(esc(invoiceNo)).append("\",");
    sb.append("\"antifakeCode\":\"\",");
    sb.append("\"deviceNo\":\"").append(esc(nz(b.efrisDeviceNo, "TCS-UNKNOWN"))).append("\",");
    sb.append("\"issuedDate\":\"").append(esc(issuedDate)).append("\",");
    sb.append("\"operator\":\"").append(esc(nz(b.efrisUsername, "admin"))).append("\",");
    sb.append("\"currency\":\"UGX\",");
    sb.append("\"invoiceType\":\"1\",");      // 1 = standard invoice (check your spec)
    sb.append("\"invoiceKind\":\"1\",");
    sb.append("\"dataSource\":\"103\",");     // 103 = POS (from URA docs)
    sb.append("\"oriInvoiceId\":\"\",");
    sb.append("\"payWay\":\"").append(esc(payWay)).append("\"");
    sb.append("},");

    // =========================================================
    // buyerDetails  (we use WALK-IN for now)
    // =========================================================
    sb.append("\"buyerDetails\":{");
    sb.append("\"buyerTin\":\"\",");
    sb.append("\"buyerNinBrn\":\"\",");
    sb.append("\"buyerPassportNum\":\"\",");
    sb.append("\"buyerLegalName\":\"WALK-IN CUSTOMER\",");
    sb.append("\"buyerBusinessName\":\"\",");
    sb.append("\"buyerAddress\":\"\",");
    sb.append("\"buyerEmail\":\"\",");
    sb.append("\"buyerMobilePhone\":\"\",");
    sb.append("\"buyerLinePhone\":\"\",");
    sb.append("\"buyerPlaceOfBusi\":\"\",");
    sb.append("\"buyerType\":\"1\",");
    sb.append("\"buyerCitizenship\":\"\",");
    sb.append("\"buyerSector\":\"\",");
    sb.append("\"buyerReferenceNo\":\"\"");
    sb.append("},");

    // =========================================================
    // goodsDetails – one row per SaleItem
    // =========================================================
    sb.append("\"goodsDetails\":[");
    boolean first = true;
    int order = 1;
    for (SaleItem it : sale.items) {
        if (!first) sb.append(',');
        first = false;

        double lineNet  = it.lineTotal;
        double lineVat  = it.vatAmount;
        double lineGross = lineNet + lineVat;

        sb.append('{');
        sb.append("\"deemedFlag\":\"2\",");      // normal sale
        sb.append("\"discountFlag\":\"2\",");    // no discount at line level
        sb.append("\"item\":\"").append(esc(nz(it.itemName, "ITEM"))).append("\",");
        sb.append("\"itemCode\":\"").append(esc(nz(it.sku, "ITEM" + order))).append("\",");
        sb.append("\"qty\":").append(it.qty).append(',');
        sb.append("\"unitPrice\":").append(it.unitPrice).append(',');
        sb.append("\"total\":").append(lineNet).append(','); // net
        sb.append("\"tax\":").append(lineVat).append(',');
        sb.append("\"unitOfMeasure\":\"10\",");  // 10 = “PCS” in many EFRIS examples
        sb.append("\"taxRate\":").append(it.vatRate / 100.0).append(',');
        sb.append("\"discountTaxRate\":0.0,");
        sb.append("\"orderNumber\":").append(order).append(',');
        sb.append("\"exciseFlag\":\"2\",");
        sb.append("\"categoryId\":\"\",");
        sb.append("\"categoryName\":\"\",");
        sb.append("\"goodsCategoryId\":\"\",");
        sb.append("\"goodsCategoryName\":\"\"");
        sb.append('}');
        order++;
    }
    sb.append("],");

    // =========================================================
    // taxDetails – simple single VAT bucket
    // =========================================================
    sb.append("\"taxDetails\":[");
    sb.append('{');
    sb.append("\"taxCategory\":\"VAT\",");
    sb.append("\"netAmount\":").append(net).append(',');
    sb.append("\"taxAmount\":").append(vat).append(',');
    sb.append("\"grossAmount\":").append(gross).append(',');
    sb.append("\"exciseUnit\":\"\",");
    sb.append("\"exciseCurrency\":\"UGX\"");
    sb.append('}');
    sb.append("],");

    // =========================================================
    // summary
    // =========================================================
    sb.append("\"summary\":{");
    sb.append("\"netAmount\":").append(net).append(',');
    sb.append("\"taxAmount\":").append(vat).append(',');
    sb.append("\"grossAmount\":").append(gross).append(',');
    sb.append("\"itemCount\":").append(sale.items.size()).append(',');
    sb.append("\"modeCode\":\"0\",");  // 0 = normal
    sb.append("\"remarks\":\"").append(esc(nz(sale.note, ""))).append("\",");
    sb.append("\"qrCode\":\"\"");
    sb.append("},");

    // =========================================================
    // extend (optional)
    // =========================================================
    sb.append("\"extend\":{");
    sb.append("\"reason\":\"\",");
    sb.append("\"reasonCode\":\"\"");
    sb.append("}");

    sb.append('}');
    return sb.toString();
}

// imports not required; pure String building

private static String esc(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
}

public static String buildCreditNotePayload(
        long creditNoteId,
        com.promedia.sentepos.dao.CreditNoteDAO.Head head,
        java.util.List<com.promedia.sentepos.dao.CreditNoteDAO.Item> items
) {
    StringBuilder sb = new StringBuilder(512);
    sb.append("{\"type\":\"CREDIT_NOTE\"");
    sb.append(",\"creditNoteId\":").append(creditNoteId);
    sb.append(",\"saleId\":").append(head != null ? head.sale_id : 0);
    sb.append(",\"reason\":\"").append(esc(head != null ? head.reason : "")).append("\"");
    sb.append(",\"totals\":{")
      .append("\"subtotal\":").append(head != null ? head.subtotal : 0).append(',')
      .append("\"vat\":").append(head != null ? head.vat_total : 0).append(',')
      .append("\"total\":").append(head != null ? head.total : 0)
      .append("}");
    sb.append(",\"items\":[");
    boolean first = true;
    if (items != null) for (var it : items) {
        if (!first) sb.append(',');
        first = false;
        sb.append('{')
          .append("\"productId\":").append(it.product_id).append(',')
          .append("\"itemName\":\"").append(esc(it.item_name)).append("\",")
          .append("\"sku\":\"").append(esc(it.sku)).append("\",")
          .append("\"qty\":").append(it.qty).append(',')
          .append("\"unitPrice\":").append(it.unit_price).append(',')
          .append("\"vatRate\":").append(it.vat_rate).append(',')
          .append("\"lineTotal\":").append(it.line_total).append(',')
          .append("\"vatAmount\":").append(it.vat_amount)
          .append('}');
    }
    sb.append("]}");
    return sb.toString();
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