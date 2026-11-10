package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PurchaseOrder extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Supplier supplier;
    private Warehouse warehouse;
    private Date orderDate;
    private Date expectedDate;
    private String status = "DRAFT";
    private String supplierName;
    private int closingDay;
    private int paymentTermDays;
    private String taxRounding;
    private String orderingInstructions = "";
    private BigDecimal totalAmount = new BigDecimal("0.00");
    private String createdBy;
    private Date createdAt;
    private String submittedBy;
    private Date submittedAt;
    private String approvedBy;
    private Date approvedAt;
    private String lastChangedBy;
    private Date lastChangedAt;
    private String decisionReason = "";
    private String notes = "";
    private List<PurchaseOrderLine> lines = new ArrayList<PurchaseOrderLine>();

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public Date getOrderDate() { return orderDate; }
    public void setOrderDate(Date orderDate) { this.orderDate = orderDate; }
    public Date getExpectedDate() { return expectedDate; }
    public void setExpectedDate(Date expectedDate) { this.expectedDate = expectedDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public int getClosingDay() { return closingDay; }
    public void setClosingDay(int closingDay) { this.closingDay = closingDay; }
    public int getPaymentTermDays() { return paymentTermDays; }
    public void setPaymentTermDays(int paymentTermDays) { this.paymentTermDays = paymentTermDays; }
    public String getTaxRounding() { return taxRounding; }
    public void setTaxRounding(String taxRounding) { this.taxRounding = taxRounding; }
    public String getOrderingInstructions() { return orderingInstructions; }
    public void setOrderingInstructions(String orderingInstructions) { this.orderingInstructions = orderingInstructions; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public Date getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Date submittedAt) { this.submittedAt = submittedAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Date getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Date approvedAt) { this.approvedAt = approvedAt; }
    public String getLastChangedBy() { return lastChangedBy; }
    public void setLastChangedBy(String lastChangedBy) { this.lastChangedBy = lastChangedBy; }
    public Date getLastChangedAt() { return lastChangedAt; }
    public void setLastChangedAt(Date lastChangedAt) { this.lastChangedAt = lastChangedAt; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<PurchaseOrderLine> getLines() { return lines; }
    public void setLines(List<PurchaseOrderLine> lines) { this.lines = lines; }
    public boolean isEditable() { return "DRAFT".equals(status) || "REJECTED".equals(status); }
    public boolean isReceivable() { return "APPROVED".equals(status) || "PART_RECEIVED".equals(status); }
    public long getOutstandingQuantity() {
        long result = 0L;
        for (PurchaseOrderLine line : lines) { result += line.getOutstandingQuantity(); }
        return result;
    }
}
