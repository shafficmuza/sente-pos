package com.promedia.sentepos.efris;

import com.promedia.sentepos.model.*;
import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.dao.EfrisDAO;
import com.promedia.sentepos.dao.ProductDAO;
import com.promedia.sentepos.dao.UomDAO;
import com.promedia.sentepos.model.Uom;
import com.promedia.sentepos.db.Db;
import com.promedia.sentepos.print.Json;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;






public class EfrisPayloadBuilder {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        String uomCode = resolveUomCodeForSku(it.sku);
        g.put("unitOfMeasure", uomCode);                           // generic pcs; adjust later
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

public static String buildCreditNotePayload(
        long creditNoteId,
        com.promedia.sentepos.dao.CreditNoteDAO.Head head,
        java.util.List<com.promedia.sentepos.dao.CreditNoteDAO.Item> items
) throws Exception {

    Business b = BusinessDAO.loadSingle();
    if (b == null) throw new IllegalStateException("Business not configured.");

    // 1) Look up EFRIS info of the original sale (T109 result)
    EfrisDAO.Rec inv = null;
    if (head != null) {
        try {
            inv = EfrisDAO.findBySaleId(head.sale_id);
        } catch (Exception ignore) {}
    }

    // We ONLY accept EFRIS invoiceId / invoiceNo stored in our efris table,
    // never the local "INV-xxx".
    boolean hasInvoiceId = inv != null
            && inv.invoice_id != null
            && !inv.invoice_id.isBlank();
    boolean hasInvoiceNo = inv != null
            && inv.invoice_number != null
            && !inv.invoice_number.isBlank();

    if (!hasInvoiceId && !hasInvoiceNo) {
        throw new IllegalStateException(
                "Original sale has no EFRIS invoiceId or invoiceNo – cannot issue credit note.");
    }

    // 2) Query T108 to fetch the ORIGINAL invoice payload from EFRIS
    //    Use whichever we have (id and/or number).
    EfrisClient client = new EfrisClient();
    EfrisClient.Result t108 = client.queryInvoiceT108(
            hasInvoiceId ? inv.invoice_id : null,
            hasInvoiceNo ? inv.invoice_number : null,
            b.efrisDeviceNo
    );

    if (!t108.ok) {
        throw new IllegalStateException("T108 query failed: " + t108.error);
    }
    if (t108.innerContentJson == null || t108.innerContentJson.isBlank()) {
        throw new IllegalStateException("T108 returned empty content for original invoice.");
    }

    JsonNode orig = MAPPER.readTree(t108.innerContentJson);
    JsonNode origBasic   = orig.get("basicInformation");
    JsonNode origGoods   = orig.get("goodsDetails");
    JsonNode origTaxArr  = orig.get("taxDetails");
    JsonNode origBuyer   = orig.get("buyerDetails");
    JsonNode origPayWay  = orig.get("payWay");
    JsonNode origSummary = orig.get("summary");

    // 3) Compute CN totals from local items (positive numbers here)
    double subtotal = 0.0;
    double vatTotal = 0.0;
    for (var it : items) {
        subtotal += it.line_total;
        vatTotal += it.vat_amount;
    }
    double grossTotal = subtotal + vatTotal;

    // 4) Build CREDIT NOTE payload per URA credit-note application spec
    ObjectNode root = MAPPER.createObjectNode();

    // --- Core reference to original invoice (uses EFRIS ids/numbers only) ---
    if (hasInvoiceId) {
        root.put("oriInvoiceId", inv.invoice_id);
    } else {
        root.put("oriInvoiceId", ""); // allowed to be blank if invoiceNo is provided
    }
    if (hasInvoiceNo) {
        root.put("oriInvoiceNo", inv.invoice_number);
    } else {
        root.put("oriInvoiceNo", "");
    }

    // --- Reason fields ---
    root.put("reasonCode", mapReasonToCode(head != null ? head.reason : null));
    root.put("reason", nz(head != null ? head.reason : "", "Credit note"));

    // --- Application time ---
    String applicationTime = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    root.put("applicationTime", applicationTime);

    // --- Invoice apply category code (101 = creditNote) ---
    root.put("invoiceApplyCategoryCode", "101");

    // --- Currency must match original invoice currency ---
    String origCurrency = (origBasic != null && origBasic.hasNonNull("currency"))
            ? origBasic.get("currency").asText()
            : nz(b.currency, "UGX");
    root.put("currency", origCurrency);

    // --- Contact details (you can refine later) ---
    root.put("contactName", nz(b.name, "Contact"));
    root.put("contactMobileNum", nz(b.phone, ""));
    root.put("contactEmail", nz(b.email, ""));

    // --- Source: 106 = Offline Mode Enabler per URA dictionary ---
    root.put("source", "106");

    // --- Remarks (free text) ---
    root.put("remarks", nz(head != null ? head.note : "", ""));

    // --- Seller's reference number: use original invoiceNo or a custom value ---
    String sellersRef = (origBasic != null && origBasic.hasNonNull("invoiceNo"))
            ? origBasic.get("invoiceNo").asText()
            : (hasInvoiceNo ? inv.invoice_number : "");
    root.put("sellersReferenceNo", sellersRef);

    // ----------------------------------------------------------------
    // 5) goodsDetails – build from T108 goods lines, but with NEGATIVE
    //    qty/total/tax for the items being returned.
    // ----------------------------------------------------------------
    ArrayNode cnGoods = MAPPER.createArrayNode();

    // index original goods by itemCode for easy match
    java.util.Map<String, JsonNode> goodsByCode = new java.util.HashMap<>();
    if (origGoods != null && origGoods.isArray()) {
        for (JsonNode g : origGoods) {
            String code = g.path("itemCode").asText(null);
            if (code != null && !code.isBlank()) {
                goodsByCode.put(code, g);
            }
        }
    }

    int orderNo = 0;
    for (var it : items) {
        String itemCode = it.sku; // you said sku is what you used as itemCode in T109
        JsonNode origG = goodsByCode.get(itemCode);

        if (origG == null) {
            throw new IllegalStateException(
                "T108 invoice does not contain itemCode/sku '" + itemCode + "'.");
        }

        ObjectNode g = cnGoods.addObject();
        orderNo++;

        // Copy original fields verbatim where spec says "same as original invoice"
        g.put("item", origG.path("item").asText(""));
        g.put("itemCode", origG.path("itemCode").asText(""));
        g.put("unitOfMeasure", origG.path("unitOfMeasure").asText(""));
        g.put("unitPrice", origG.path("unitPrice").asText(""));
        g.put("deemedFlag", origG.path("deemedFlag").asText("2"));
        g.put("discountFlag", origG.path("discountFlag").asText("2"));
        g.put("exciseFlag", origG.path("exciseFlag").asText("2"));
        g.put("categoryId", origG.path("categoryId").asText(""));
        g.put("categoryName", origG.path("categoryName").asText(""));
        g.put("goodsCategoryId", origG.path("goodsCategoryId").asText(""));
        g.put("goodsCategoryName", origG.path("goodsCategoryName").asText(""));
        g.put("exciseRate", origG.path("exciseRate").asText(""));
        g.put("exciseRule", origG.path("exciseRule").asText(""));
        g.put("exciseTax", origG.path("exciseTax").asText(""));
        g.put("pack", origG.path("pack").asText(""));
        g.put("stick", origG.path("stick").asText(""));
        g.put("exciseUnit", origG.path("exciseUnit").asText(""));
        g.put("exciseCurrency", origG.path("exciseCurrency").asText(""));
        g.put("exciseRateName", origG.path("exciseRateName").asText(""));
        g.put("vatApplicableFlag", origG.path("vatApplicableFlag").asText("1"));

        // Tax rate – copy same as original (usually "0.18", "0", "-")
        g.put("taxRate", origG.path("taxRate").asText(
                String.valueOf(it.vat_rate / 100.0)));

        // Now override the amounts with NEGATIVE values for returned qty
        double lineNet = it.line_total;    // positive net in DB
        double lineVat = it.vat_amount;    // positive VAT in DB

        g.put("qty", -it.qty);          // must be negative
        g.put("total", -lineNet);       // netAmount negative
        g.put("tax", -lineVat);         // tax negative

        // optional discount fields
        g.put("discountTotal", "");
        g.put("discountTaxRate", "");

        g.put("orderNumber", String.valueOf(orderNo));
    }

    root.set("goodsDetails", cnGoods);

    // ----------------------------------------------------------------
    // 6) taxDetails – aggregate by taxRate and map to original taxCategoryCode
    // ----------------------------------------------------------------
    class RateTotals { double net; double tax; }
    java.util.Map<Double, RateTotals> byRate = new java.util.HashMap<>();
    for (var it : items) {
        double rate = it.vat_rate;
        RateTotals rt = byRate.computeIfAbsent(rate, v -> new RateTotals());
        rt.net += it.line_total;
        rt.tax += it.vat_amount;
    }

    ArrayNode cnTaxArr = MAPPER.createArrayNode();

    for (var e : byRate.entrySet()) {
        double rate = e.getKey();
        RateTotals rt = e.getValue();
        if (rt.net == 0 && rt.tax == 0) continue;

        // find original taxDetails row with same taxRate (if possible)
        JsonNode match = null;
        if (origTaxArr != null && origTaxArr.isArray()) {
            for (JsonNode t : origTaxArr) {
                String rStr = t.path("taxRate").asText(null);
                if (rStr == null) continue;
                try {
                    double rVal = Double.parseDouble(rStr);
                    if (Math.abs(rVal - (rate / 100.0)) < 0.0000001) {
                        match = t;
                        break;
                    }
                } catch (NumberFormatException ignore) {}
            }
        }

        ObjectNode t = cnTaxArr.addObject();
        String taxCatCode = (match != null && match.hasNonNull("taxCategoryCode"))
                ? match.get("taxCategoryCode").asText()
                : "01"; // default

        t.put("taxCategoryCode", taxCatCode);
        t.put("netAmount", -rt.net);
        t.put("taxRate", (match != null && match.hasNonNull("taxRate"))
                ? match.get("taxRate").asText()
                : String.valueOf(rate / 100.0));
        t.put("taxAmount", -rt.tax);
        t.put("grossAmount", -(rt.net + rt.tax));

        if (match != null) {
            t.put("exciseUnit", match.path("exciseUnit").asText(""));
            t.put("exciseCurrency", match.path("exciseCurrency").asText(origCurrency));
            t.put("taxRateName", match.path("taxRateName").asText(""));
        } else {
            t.put("exciseUnit", "");
            t.put("exciseCurrency", origCurrency);
            t.put("taxRateName", "");
        }
    }

    root.set("taxDetails", cnTaxArr);

    // ----------------------------------------------------------------
    // 7) summary – negative totals
    // ----------------------------------------------------------------
    ObjectNode summary = MAPPER.createObjectNode();
    summary.put("netAmount", -subtotal);
    summary.put("taxAmount", -vatTotal);
    summary.put("grossAmount", -(grossTotal));
    summary.put("itemCount", items.size());
    summary.put("modeCode", "0");  // per spec
    summary.put("qrCode", "");
    summary.put("remarks", nz(head != null ? head.reason : "", "Credit note"));
    root.set("summary", summary);

    // ----------------------------------------------------------------
    // 8) payWay – simple single-mode refund (you can refine if needed)
    // ----------------------------------------------------------------
    ArrayNode payWay = MAPPER.createArrayNode();
    ObjectNode p = payWay.addObject();
    p.put("paymentMode", "102"); // cash; adjust to match original sale if needed
    p.put("paymentAmount", String.valueOf(grossTotal)); // positive
    p.put("orderNumber", "a");
    root.set("payWay", payWay);

    // ----------------------------------------------------------------
    // 9) buyerDetails – reuse original buyer (if any)
    // ----------------------------------------------------------------
    if (origBuyer != null && origBuyer.isObject()) {
        root.set("buyerDetails", origBuyer);
    } else {
        ObjectNode buyer = MAPPER.createObjectNode();
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
        buyer.put("buyerCitizenship", "0");
        buyer.put("buyerSector", "0");
        buyer.put("buyerReferenceNo", "");
        root.set("buyerDetails", buyer);
    }

    // ----------------------------------------------------------------
    // 10) importServicesSeller – copy from original if present, else empty
    // ----------------------------------------------------------------
    JsonNode origImport = orig.get("importServicesSeller");
    if (origImport != null && origImport.isObject()) {
        root.set("importServicesSeller", origImport);
    } else {
        ObjectNode imp = MAPPER.createObjectNode();
        imp.put("importBusinessName", "");
        imp.put("importEmailAddress", "");
        imp.put("importContactNumber", "");
        imp.put("importAddress", "");
        imp.put("importInvoiceDate", applicationTime.substring(0, 10));
        imp.put("importAttachmentName", "");
        imp.put("importAttachmentContent", "");
        root.set("importServicesSeller", imp);
    }

    // ----------------------------------------------------------------
    // 11) basicInformation – subset required in credit note application
    // ----------------------------------------------------------------
    ObjectNode basic = MAPPER.createObjectNode();
    if (origBasic != null) {
        basic.put("operator", origBasic.path("operator").asText(nz(b.efrisUsername, "admin")));
        basic.put("invoiceKind", origBasic.path("invoiceKind").asText("1"));
        basic.put("invoiceIndustryCode", origBasic.path("invoiceIndustryCode").asText("101"));
    } else {
        basic.put("operator", nz(b.efrisUsername, "admin"));
        basic.put("invoiceKind", "1");
        basic.put("invoiceIndustryCode", "101");
    }
    basic.put("branchId", nz(b.efrisBranchId, ""));
    root.set("basicInformation", basic);

    // ----------------------------------------------------------------
    // 12) attachmentList – empty for now
    // ----------------------------------------------------------------
    root.set("attachmentList", MAPPER.createArrayNode());

    // Final JSON
    return MAPPER.writeValueAsString(root);
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

    // Use the unit of measure *exactly* as stored in products.measure_unit.
// Falls back to "PCS" if nothing found.
private static String resolveUomForItem(long productId, String sku) {
    try {
        com.promedia.sentepos.dao.ProductDAO.ProductRow p = null;

        // If you have ProductDAO.findById(long), use it:
        try {
            if (productId > 0) {
                p = com.promedia.sentepos.dao.ProductDAO.findById(productId);
            }
        } catch (Throwable ignore) {}

        // Fallback: lookup by SKU / barcode
        if (p == null && sku != null && !sku.isBlank()) {
            try {
                p = com.promedia.sentepos.dao.ProductDAO.findBySkuOrBarcode(sku);
            } catch (Throwable ignore) {}
        }

        if (p != null && p.measureUnit != null && !p.measureUnit.isBlank()) {
            // IMPORTANT: this should already be an EFRIS UOM like "KG", "PP", "PCS"…
            return p.measureUnit;
        }
    } catch (Throwable ignore) {
        // ignore and fall through
    }
    // safe default – but ideally all items have a real UOM from goods maintenance
    return "PCE";
}
    
    /**
 * Maps your local measure_unit text → EFRIS numeric unitOfMeasure code.
 * EFRIS only accepts numeric UOM codes.
 */
private static String mapUomToEfrisCode(String measureUnit) {
    if (measureUnit == null) return "101"; // default = Other

    String u = measureUnit.trim().toLowerCase();

    switch (u) {
        case "pc":
        case "pcs":
        case "piece":
        case "pieces":
        case "unit":
        case "each":
            return "10"; // PCS

        case "kg":
        case "kgs":
        case "kilogram":
        case "kilograms":
            return "11"; // KG

        case "g":
        case "gram":
        case "grams":
            return "13"; // GRAM

        case "mg":
        case "milligram":
        case "milligrams":
            return "131"; // hypothetical, depends on URA dictionary

        case "ltr":
        case "l":
        case "litre":
        case "liter":
        case "litres":
        case "liters":
            return "12"; // LITRE

        case "ml":
        case "millilitre":
        case "milliliter":
        case "millilitres":
        case "milliliters":
            return "14"; // MILLILITRE

        case "m":
        case "meter":
        case "metre":
            return "15"; // METER

        case "box":
        case "boxes":
            return "16"; // BOX

        case "roll":
        case "rolls":
            return "17"; // ROLL

        case "bag":
        case "bags":
            return "18"; // BAG

        case "bottle":
        case "bottles":
            return "19"; // BOTTLE

        case "carton":
        case "ctn":
            return "20"; // CARTON

        default:
            return "101"; // OTHER
    }
}

// Map your free-text reason to a URA reasonCode (101–105)
private static String mapReasonToCode(String reason) {
    if (reason == null) return "105";
    String r = reason.toLowerCase();
    if (r.contains("expiry") || r.contains("damag")) return "101";
    if (r.contains("cancel")) return "102";
    if (r.contains("miscalc") || r.contains("price") || r.contains("discount")) return "103";
    if (r.contains("waive") || r.contains("waiver")) return "104";
    return "105"; // Others
}

    /**
     * Resolve the UOM code to send to EFRIS for an invoice line.
     *
     * Priority:
     *   1) products.measure_unit for this SKU (already an EFRIS code from T115)
     *   2) default "PCE" if present in efris_uom
     *   3) first UOM from dictionary
     *   4) final hard-fallback literal "PCE"
     */
    private static String resolveUomCodeForSku(String sku) {
        try {
            // 1) Use measure_unit from products table if available
            if (sku != null && !sku.isBlank()) {
                String fromProduct = ProductDAO.findMeasureUnitBySku(sku);
                if (fromProduct != null && !fromProduct.isBlank()) {
                    return fromProduct.trim();
                }
            }

            // 2) Prefer PCE from dictionary if it exists
            Uom pce = null;
            try {
                pce = UomDAO.findByCode("PCE");
            } catch (Exception ignore) { }
            if (pce != null && pce.getCode() != null && !pce.getCode().isBlank()) {
                return pce.getCode().trim();
            }

            // 3) Fall back to first UOM in dictionary
            try {
                java.util.List<Uom> list = UomDAO.listAll();
                if (!list.isEmpty() && list.get(0).getCode() != null) {
                    return list.get(0).getCode().trim();
                }
            } catch (Exception ignore) { }

        } catch (Exception ignore) {
            // swallow and fall through to literal fallback
        }
        // 4) last resort
        return "PCE";
    }


}