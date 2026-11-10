package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.Money;

public class APInvoice extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Supplier supplier;
    private String supplierName;
    private String supplierAddress;
    private String supplierInvoiceNumber;
    private String status = "DRAFT";
    private Date invoiceDate;
    private Date dueDate;
    private Date postedDate;
    private String taxRounding;
    private BigDecimal netAmount = Money.ZERO;
    private BigDecimal taxAmount = Money.ZERO;
    private BigDecimal totalAmount = Money.ZERO;
    private BigDecimal paidAmount = Money.ZERO;
    private BigDecimal creditedAmount = Money.ZERO;
    private BigDecimal varianceAmount = Money.ZERO;
    private BigDecimal absoluteVarianceAmount = Money.ZERO;
    private String varianceStatus = "NONE";
    private String varianceReason = "";
    private String varianceApprovedBy;
    private Long varianceApprovedById;
    private Date varianceApprovedAt;
    private String reviewFingerprint;
    private int changeNumber;
    private String notes = "";
    private String createdBy;
    private Long createdById;
    private Date createdAt;
    private String lastChangedBy;
    private Long lastChangedById;
    private Date lastChangedAt;
    private String postedBy;
    private Date postedAt;
    private String cancelledBy;
    private Date cancelledAt;
    private String cancellationReason;
    private List<APInvoiceLine> lines = new ArrayList<APInvoiceLine>();

    public String getNumber() { return number; }
    public void setNumber(String value) { number = value; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier value) { supplier = value; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String value) { supplierName = value; }
    public String getSupplierAddress() { return supplierAddress; }
    public void setSupplierAddress(String value) { supplierAddress = value; }
    public String getSupplierInvoiceNumber() { return supplierInvoiceNumber; }
    public void setSupplierInvoiceNumber(String value) { supplierInvoiceNumber = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Date getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(Date value) { invoiceDate = value; }
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date value) { dueDate = value; }
    public Date getPostedDate() { return postedDate; }
    public void setPostedDate(Date value) { postedDate = value; }
    public String getTaxRounding() { return taxRounding; }
    public void setTaxRounding(String value) { taxRounding = value; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal value) { netAmount = value; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal value) { taxAmount = value; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal value) { totalAmount = value; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal value) { paidAmount = value; }
    public BigDecimal getCreditedAmount() { return creditedAmount; }
    public void setCreditedAmount(BigDecimal value) { creditedAmount = value; }
    public BigDecimal getOutstandingAmount() {
        return "POSTED".equals(status) ? totalAmount.subtract(paidAmount).subtract(creditedAmount).max(Money.ZERO) : Money.ZERO;
    }
    public BigDecimal getVarianceAmount() { return varianceAmount; }
    public void setVarianceAmount(BigDecimal value) { varianceAmount = value; }
    public BigDecimal getAbsoluteVarianceAmount() { return absoluteVarianceAmount; }
    public void setAbsoluteVarianceAmount(BigDecimal value) { absoluteVarianceAmount = value; }
    public String getVarianceStatus() { return varianceStatus; }
    public void setVarianceStatus(String value) { varianceStatus = value; }
    public String getVarianceReason() { return varianceReason; }
    public void setVarianceReason(String value) { varianceReason = value; }
    public String getVarianceApprovedBy() { return varianceApprovedBy; }
    public void setVarianceApprovedBy(String value) { varianceApprovedBy = value; }
    public Long getVarianceApprovedById() { return varianceApprovedById; }
    public void setVarianceApprovedById(Long value) { varianceApprovedById = value; }
    public Date getVarianceApprovedAt() { return varianceApprovedAt; }
    public void setVarianceApprovedAt(Date value) { varianceApprovedAt = value; }
    public String getReviewFingerprint() { return reviewFingerprint; }
    public void setReviewFingerprint(String value) { reviewFingerprint = value; }
    public int getChangeNumber() { return changeNumber; }
    public void setChangeNumber(int value) { changeNumber = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Long getCreatedById() { return createdById; }
    public void setCreatedById(Long value) { createdById = value; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date value) { createdAt = value; }
    public String getLastChangedBy() { return lastChangedBy; }
    public void setLastChangedBy(String value) { lastChangedBy = value; }
    public Long getLastChangedById() { return lastChangedById; }
    public void setLastChangedById(Long value) { lastChangedById = value; }
    public Date getLastChangedAt() { return lastChangedAt; }
    public void setLastChangedAt(Date value) { lastChangedAt = value; }
    public String getPostedBy() { return postedBy; }
    public void setPostedBy(String value) { postedBy = value; }
    public Date getPostedAt() { return postedAt; }
    public void setPostedAt(Date value) { postedAt = value; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String value) { cancelledBy = value; }
    public Date getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Date value) { cancelledAt = value; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String value) { cancellationReason = value; }
    public List<APInvoiceLine> getLines() { return lines; }
    public void setLines(List<APInvoiceLine> value) { lines = value; }
    public boolean isFullyMatched() {
        if (lines.isEmpty()) { return false; }
        for (APInvoiceLine line : lines) {
            if (line.getMatchedQuantity() != line.getQuantity()) { return false; }
        }
        return true;
    }
}
