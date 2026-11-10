package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.Money;

public class PaymentReceipt extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Customer customer;
    private String customerName;
    private String requestKey;
    private String payloadFingerprint;
    private Date receivedDate;
    private BigDecimal amount = Money.ZERO;
    private BigDecimal allocatedAmount = Money.ZERO;
    private String method;
    private String reference;
    private String notes;
    private String status;
    private String createdBy;
    private Date createdAt;
    private String cancelledBy;
    private Date cancelledAt;
    private Date cancellationDate;
    private String cancellationReason;
    private List<PaymentAllocation> allocations = new ArrayList<PaymentAllocation>();

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getPayloadFingerprint() { return payloadFingerprint; }
    public void setPayloadFingerprint(String payloadFingerprint) { this.payloadFingerprint = payloadFingerprint; }
    public Date getReceivedDate() { return receivedDate; }
    public void setReceivedDate(Date receivedDate) { this.receivedDate = receivedDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }
    public BigDecimal getUnallocatedAmount() { return "CANCELLED".equals(status) ? Money.ZERO : amount.subtract(allocatedAmount); }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public Date getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Date cancelledAt) { this.cancelledAt = cancelledAt; }
    public Date getCancellationDate() { return cancellationDate; }
    public void setCancellationDate(Date cancellationDate) { this.cancellationDate = cancellationDate; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public List<PaymentAllocation> getAllocations() { return allocations; }
    public void setAllocations(List<PaymentAllocation> allocations) { this.allocations = allocations; }
}
