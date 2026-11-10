package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.Date;

public class PurchaseOrderLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private PurchaseOrder order;
    private int lineNumber;
    private Product product;
    private String productCode;
    private String productName;
    private String unit;
    private String supplierProductCode = "";
    private int quantity;
    private int receivedQuantity;
    private int rejectedQuantity;
    private int cancelledQuantity;
    private int orderPackSize;
    private int leadTimeDays;
    private BigDecimal unitCost;
    private BigDecimal lineAmount;
    private Date expectedDate;
    private String notes = "";

    public PurchaseOrder getOrder() { return order; }
    public void setOrder(PurchaseOrder order) { this.order = order; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSupplierProductCode() { return supplierProductCode; }
    public void setSupplierProductCode(String supplierProductCode) { this.supplierProductCode = supplierProductCode; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(int receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public int getRejectedQuantity() { return rejectedQuantity; }
    public void setRejectedQuantity(int rejectedQuantity) { this.rejectedQuantity = rejectedQuantity; }
    public int getCancelledQuantity() { return cancelledQuantity; }
    public void setCancelledQuantity(int cancelledQuantity) { this.cancelledQuantity = cancelledQuantity; }
    public int getOrderPackSize() { return orderPackSize; }
    public void setOrderPackSize(int orderPackSize) { this.orderPackSize = orderPackSize; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(int leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public BigDecimal getLineAmount() { return lineAmount; }
    public void setLineAmount(BigDecimal lineAmount) { this.lineAmount = lineAmount; }
    public Date getExpectedDate() { return expectedDate; }
    public void setExpectedDate(Date expectedDate) { this.expectedDate = expectedDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public int getOutstandingQuantity() { return quantity - receivedQuantity - cancelledQuantity; }
}
