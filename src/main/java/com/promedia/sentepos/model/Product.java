package com.promedia.sentepos.model;

public class Product {
    private String itemName;
    private String sku;
    private String commodityCode;
    private int    isService;
    private String measureUnit;
    private double unitPrice;
    private String currency;
    private String vatCategory;
    private double vatRate;
    private String barcode;
    private String brand;
    private String specification;
    private String packageUnit;
    private Integer packageQty;
    private Double stockQty;
    private Double reorderLevel;
    private int active;

    public Product() {}

    public Product(String itemName, String sku, String commodityCode, int isService,
                   String measureUnit, double unitPrice, String currency,
                   String vatCategory, double vatRate, String barcode,
                   String brand, String specification, String packageUnit,
                   Integer packageQty, Double stockQty, Double reorderLevel, int active) {
        this.itemName = itemName;
        this.sku = sku;
        this.commodityCode = commodityCode;
        this.isService = isService;
        this.measureUnit = measureUnit;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.vatCategory = vatCategory;
        this.vatRate = vatRate;
        this.barcode = barcode;
        this.brand = brand;
        this.specification = specification;
        this.packageUnit = packageUnit;
        this.packageQty = packageQty;
        this.stockQty = stockQty;
        this.reorderLevel = reorderLevel;
        this.active = active;
    }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }
    public int getIsService() { return isService; }
    public void setIsService(int isService) { this.isService = isService; }
    public String getMeasureUnit() { return measureUnit; }
    public void setMeasureUnit(String measureUnit) { this.measureUnit = measureUnit; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getVatCategory() { return vatCategory; }
    public void setVatCategory(String vatCategory) { this.vatCategory = vatCategory; }
    public double getVatRate() { return vatRate; }
    public void setVatRate(double vatRate) { this.vatRate = vatRate; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public String getPackageUnit() { return packageUnit; }
    public void setPackageUnit(String packageUnit) { this.packageUnit = packageUnit; }
    public Integer getPackageQty() { return packageQty; }
    public void setPackageQty(Integer packageQty) { this.packageQty = packageQty; }
    public Double getStockQty() { return stockQty; }
    public void setStockQty(Double stockQty) { this.stockQty = stockQty; }
    public Double getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(Double reorderLevel) { this.reorderLevel = reorderLevel; }
    public int getActive() { return active; }
    public void setActive(int active) { this.active = active; }
}
