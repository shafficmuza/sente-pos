package com.promedia.sentepos.model;

public class Uom {
    private String code;        // EFRIS "value" (e.g. PCE, KGM, PP, etc.)
    private String name;        // EFRIS "name"  (e.g. "PCE-Piece")
    private String description; // Optional description from EFRIS

    public Uom() {}

    public Uom(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}