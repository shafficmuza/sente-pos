package com.promedia.sentepos.model;

public class Payment {
    public enum Method { CASH, MOBILE, CARD }
    public Method method = Method.CASH;
    public double amount;
    public String reference; // mobile txn id, last 4, etc.
}