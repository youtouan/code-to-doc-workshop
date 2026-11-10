package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.Date;

public class PaymentAllocation extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private PaymentReceipt receipt;
    private Invoice invoice;
    private BigDecimal amount;
    private Date allocationDate;
    private String status;
    private String createdBy;
    private Date createdAt;
    private String reversedBy;
    private Date reversedAt;
    private Date reversalDate;
    private String reversalReason;

    public PaymentReceipt getReceipt() { return receipt; }
    public void setReceipt(PaymentReceipt receipt) { this.receipt = receipt; }
    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Date getAllocationDate() { return allocationDate; }
    public void setAllocationDate(Date allocationDate) { this.allocationDate = allocationDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public String getReversedBy() { return reversedBy; }
    public void setReversedBy(String reversedBy) { this.reversedBy = reversedBy; }
    public Date getReversedAt() { return reversedAt; }
    public void setReversedAt(Date reversedAt) { this.reversedAt = reversedAt; }
    public Date getReversalDate() { return reversalDate; }
    public void setReversalDate(Date reversalDate) { this.reversalDate = reversalDate; }
    public String getReversalReason() { return reversalReason; }
    public void setReversalReason(String reversalReason) { this.reversalReason = reversalReason; }
}
