package com.promedia.sentepos.efris;

import com.promedia.sentepos.model.*;
import com.promedia.sentepos.dao.BusinessDAO;
import com.promedia.sentepos.print.Json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EfrisPayloadBuilder {

    /** Build a generic invoice payload for EFRIS. Adjust keys to the official spec later. */
    public static String buildInvoicePayload(long saleId, Sale sale, Payment payment) throws Exception {
        Business b = BusinessDAO.loadSingle();
        if (b == null) throw new IllegalStateException("Business not configured.");

        Map<String,Object> root = new HashMap<>();

        // Seller/Business
        Map<String,Object> seller = new HashMap<>();
        seller.put("name", n(b.name));
        seller.put("tin", n(b.tin));
        seller.put("branchCode", n(b.branchCode));
        seller.put("address", n(b.addressLine));
        seller.put("city", n(b.city));
        seller.put("country", n(b.country));
        seller.put("phone", n(b.phone));
        seller.put("email", n(b.email));
        seller.put("currency", n(b.currency));
        seller.put("vatRate", b.vatRate == null ? 18.0 : b.vatRate);
        seller.put("deviceNo", n(b.efrisDeviceNo));
        seller.put("efrisUser", n(b.efrisUsername));
        seller.put("efrisPass", n(b.efrisPassword));
        seller.put("efrisBranchId", n(b.efrisBranchId));
        root.put("seller", seller);

        // Invoice meta
        Map<String,Object> inv = new HashMap<>();
        inv.put("localSaleId", saleId);
        inv.put("note", n(sale.note));
        inv.put("dateTime", System.currentTimeMillis()); // or ISO8601
        root.put("invoice", inv);

        // Items
        double subtotal = 0, vat = 0, total = 0;
        List<SaleItem> items = sale.items;
        var arr = new java.util.ArrayList<Map<String,Object>>();
        for (SaleItem it : items) {
            Map<String,Object> row = new HashMap<>();
            row.put("name", n(it.itemName));
            row.put("sku", n(it.sku));
            row.put("qty", it.qty);
            row.put("unitPrice", it.unitPrice);
            row.put("vatRate", it.vatRate);
            row.put("lineTotal", it.lineTotal);
            row.put("vatAmount", it.vatAmount);
            arr.add(row);

            subtotal += it.lineTotal;
            vat += it.vatAmount;
        }
        total = subtotal + vat;
        root.put("items", arr);

        // Totals
        Map<String,Object> totals = new HashMap<>();
        totals.put("subtotal", subtotal);
        totals.put("vat", vat);
        totals.put("total", total);
        root.put("totals", totals);

        // Payment
        Map<String,Object> pay = new HashMap<>();
        pay.put("method", payment != null ? payment.method.name() : "CASH");
        pay.put("amount", payment != null ? payment.amount : total);
        root.put("payment", pay);

        // Return JSON
        return Json.stringify(root);
    }

    private static String n(String s){ return (s==null || s.isBlank()) ? null : s; }
}