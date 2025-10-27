package com.promedia.sentepos.model;

import java.util.ArrayList;
import java.util.List;

public class Sale {
    public Long id;
    public String receiptNo;
    public double subtotal;
    public double vatTotal;
    public double total;
    public double paid;
    public double changeDue;
    public String note;

    public final List<SaleItem> items = new ArrayList<>();
}