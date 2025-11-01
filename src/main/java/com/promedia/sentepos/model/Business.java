package com.promedia.sentepos.model;

public class Business {
    public Long id;
    public String name;
    public String tin;
    public String branchCode;
    public String addressLine;
    public String city;
    public String country;
    public String phone;
    public String email;
    public String currency;
    public Double vatRate;

    // EFRIS placeholders
    public String efrisDeviceNo;
    public String efrisUsername;
    public String efrisPassword; // TODO: store hashed/obfuscated later
    public String efrisBranchId;

    public Business() {}

    public Business(Long id, String name, String tin, String branchCode,
                    String addressLine, String city, String country,
                    String phone, String email, String currency, Double vatRate,
                    String efrisDeviceNo, String efrisUsername, String efrisPassword, String efrisBranchId) {
        this.id = id;
        this.name = name;
        this.tin = tin;
        this.branchCode = branchCode;
        this.addressLine = addressLine;
        this.city = city;
        this.country = country;
        this.phone = phone;
        this.email = email;
        this.currency = currency;
        this.vatRate = vatRate;
        this.efrisDeviceNo = efrisDeviceNo;
        this.efrisUsername = efrisUsername;
        this.efrisPassword = efrisPassword;
        this.efrisBranchId = efrisBranchId;
    }
}