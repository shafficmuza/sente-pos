package com.promedia.sentepos.model;

public class SaleItem {
    public Long productId;
    public String itemName;
    public String sku;
    public double qty;
    public double unitPrice;
    public double vatRate;   // e.g., 18
    public double lineTotal; // qty * unitPrice
    public double vatAmount; // lineTotal * (vatRate/100)
}