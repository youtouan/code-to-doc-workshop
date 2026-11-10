package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;

public class Product extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String code;
    private String name;
    private String unit = "個";
    private String taxCategory = "STANDARD";
    private BigDecimal listPrice = new BigDecimal("0.00");
    private BigDecimal standardCost = new BigDecimal("0.00");
    private int packSize = 1;
    private int reorderPoint;
    private int reorderQuantity;
    private boolean active = true;
    private String notes = "";

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getTaxCategory() { return taxCategory; }
    public void setTaxCategory(String taxCategory) { this.taxCategory = taxCategory; }
    public BigDecimal getListPrice() { return listPrice; }
    public void setListPrice(BigDecimal listPrice) { this.listPrice = listPrice; }
    public BigDecimal getStandardCost() { return standardCost; }
    public void setStandardCost(BigDecimal standardCost) { this.standardCost = standardCost; }
    public int getPackSize() { return packSize; }
    public void setPackSize(int packSize) { this.packSize = packSize; }
    public int getReorderPoint() { return reorderPoint; }
    public void setReorderPoint(int reorderPoint) { this.reorderPoint = reorderPoint; }
    public int getReorderQuantity() { return reorderQuantity; }
    public void setReorderQuantity(int reorderQuantity) { this.reorderQuantity = reorderQuantity; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
