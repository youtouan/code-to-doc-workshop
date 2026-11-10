package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.Date;

public class SupplierProduct extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Supplier supplier;
    private Product product;
    private String supplierProductCode = "";
    private Date validFrom;
    private Date validTo;
    private int minimumQuantity = 1;
    private int orderPackSize = 1;
    private int leadTimeDays = 3;
    private BigDecimal unitCost;
    private boolean preferred;
    private boolean active = true;
    private String notes = "";

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getSupplierProductCode() { return supplierProductCode; }
    public void setSupplierProductCode(String supplierProductCode) { this.supplierProductCode = supplierProductCode; }
    public Date getValidFrom() { return validFrom; }
    public void setValidFrom(Date validFrom) { this.validFrom = validFrom; }
    public Date getValidTo() { return validTo; }
    public void setValidTo(Date validTo) { this.validTo = validTo; }
    public int getMinimumQuantity() { return minimumQuantity; }
    public void setMinimumQuantity(int minimumQuantity) { this.minimumQuantity = minimumQuantity; }
    public int getOrderPackSize() { return orderPackSize; }
    public void setOrderPackSize(int orderPackSize) { this.orderPackSize = orderPackSize; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(int leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public boolean isPreferred() { return preferred; }
    public void setPreferred(boolean preferred) { this.preferred = preferred; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
