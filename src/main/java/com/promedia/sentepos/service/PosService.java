package com.promedia.sentepos.service;

import com.promedia.sentepos.dao.CreditNoteDAO;
import com.promedia.sentepos.dao.ProductDAO;
import com.promedia.sentepos.dao.SaleDAO;
import com.promedia.sentepos.dao.ProductDAO.ProductRow;
import com.promedia.sentepos.model.Payment;
import com.promedia.sentepos.model.Sale;
import com.promedia.sentepos.model.SaleItem;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class PosService {
    
    
    private PosService(){}
    
    
    private static final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    public static ProductRow lookupProduct(String codeOrName) throws SQLException {
        // Try exact scan first
        ProductRow p = ProductDAO.findBySkuOrBarcode(codeOrName);
        if (p != null) return p;
        // Fallback: first match by name/SKU contains
        List<ProductRow> list = ProductDAO.searchByNameOrSku(codeOrName);
        return list.isEmpty()? null : list.get(0);
    }

    public static long saveSale(Sale sale, Payment payment) throws SQLException {
        Connection c = null;
        try {
            c = SaleDAO.beginTx();
            long saleId = SaleDAO.createEmptySale(c, nextReceiptNo());
            SaleDAO.addItems(c, saleId, sale.items);

            double subtotal = sale.items.stream().mapToDouble(it -> it.lineTotal).sum();
            double vatTotal = sale.items.stream().mapToDouble(it -> it.vatAmount).sum();
            double total = subtotal + vatTotal;
            double paid = payment != null ? payment.amount : 0;
            double changeDue = Math.max(0, paid - total);

            if (payment != null) SaleDAO.addPayment(c, saleId, payment);
            SaleDAO.finalizeTotals(c, saleId, subtotal, vatTotal, total, paid, changeDue, sale.note);

            SaleDAO.commit(c);
            return saleId;
        } catch (SQLException ex) {
            SaleDAO.rollback(c);
            throw ex;
        }
    }

    public static SaleItem makeItemFromProduct(ProductRow p, double qty) {
        SaleItem it = new SaleItem();
        it.productId = p.id;
        it.itemName = p.itemName;
        it.sku = p.sku;
        it.qty = qty;
        it.unitPrice = p.unitPrice != null ? p.unitPrice : 0d;
        it.vatRate = p.vatRate != null ? p.vatRate : 0d;
        it.lineTotal = qty * it.unitPrice;
        it.vatAmount = it.lineTotal * (it.vatRate / 100.0);
        return it;
    }

    private static String nextReceiptNo() {
        // Simple demo: SEN-<increasing number>. Replace with your own scheme.
        return "SEN-" + seq.incrementAndGet();
    }
    
     public static long issueCreditNote(
            long saleId,
            List<CreditNoteDAO.ItemLine> lines,
            String reason,
            String note,
            double subtotal,   // not required by the core service; kept to match caller
            double vat,        // "
            double total       // "
    ) throws Exception {

   // Map UI ItemLine rows into DAO Items (compute line_total & vat_amount)
List<CreditNoteDAO.Item> items = new ArrayList<>();
if (lines != null) {
    for (CreditNoteDAO.ItemLine ln : lines) {
        CreditNoteDAO.Item it = new CreditNoteDAO.Item();
        it.product_id = ln.product_id;
        it.item_name  = ln.item_name;
        it.sku        = ln.sku;

        // core numeric fields coming from ItemLine
        it.qty        = ln.qty;
        it.unit_price = ln.unit_price;
        it.vat_rate   = ln.vat_rate;

        // compute totals (avoid referencing non-existent fields)
        double line   = (it.qty) * (it.unit_price);
        double vatAmt = line * (it.vat_rate / 100.0);

        it.line_total = line;
        it.vat_amount = vatAmt;

        items.add(it);
    }
}

        // Delegate to the core service (recomputes totals from items)
        return CreditNoteService.issueCreditNote(saleId, items, reason, note);
    }
    
    
}