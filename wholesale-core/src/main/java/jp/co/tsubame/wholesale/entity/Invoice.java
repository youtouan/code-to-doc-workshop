package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.Money;

public class Invoice extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Customer customer;
    private String customerName;
    private String billingAddress;
    private String postalCode;
    private String status;
    private Date periodStart;
    private Date periodEnd;
    private Date issuedDate;
    private Date dueDate;
    private String taxRounding;
    private int paymentTermDays;
    private BigDecimal netAmount = Money.ZERO;
    private BigDecimal taxAmount = Money.ZERO;
    private BigDecimal totalAmount = Money.ZERO;
    private BigDecimal paidAmount = Money.ZERO;
    private BigDecimal creditedAmount = Money.ZERO;
    private String createdBy;
    private Date createdAt;
    private String finalizedBy;
    private Date finalizedAt;
    private String cancelledBy;
    private Date cancelledAt;
    private String cancellationReason;
    private String claimFingerprint;
    private List<InvoiceLine> lines = new ArrayList<InvoiceLine>();

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getPeriodStart() { return periodStart; }
    public void setPeriodStart(Date periodStart) { this.periodStart = periodStart; }
    public Date getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Date periodEnd) { this.periodEnd = periodEnd; }
    public Date getIssuedDate() { return issuedDate; }
    public void setIssuedDate(Date issuedDate) { this.issuedDate = issuedDate; }
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }
    public String getTaxRounding() { return taxRounding; }
    public void setTaxRounding(String taxRounding) { this.taxRounding = taxRounding; }
    public int getPaymentTermDays() { return paymentTermDays; }
    public void setPaymentTermDays(int paymentTermDays) { this.paymentTermDays = paymentTermDays; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getCreditedAmount() { return creditedAmount; }
    public void setCreditedAmount(BigDecimal creditedAmount) { this.creditedAmount = creditedAmount; }
    public BigDecimal getOutstandingAmount() { return totalAmount.subtract(paidAmount).subtract(creditedAmount).max(Money.ZERO); }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public String getFinalizedBy() { return finalizedBy; }
    public void setFinalizedBy(String finalizedBy) { this.finalizedBy = finalizedBy; }
    public Date getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Date finalizedAt) { this.finalizedAt = finalizedAt; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public Date getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Date cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public String getClaimFingerprint() { return claimFingerprint; }
    public void setClaimFingerprint(String claimFingerprint) { this.claimFingerprint = claimFingerprint; }
    public List<InvoiceLine> getLines() { return lines; }
    public void setLines(List<InvoiceLine> lines) { this.lines = lines; }
}
