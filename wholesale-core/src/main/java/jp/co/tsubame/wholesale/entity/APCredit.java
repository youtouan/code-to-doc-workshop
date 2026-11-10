package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.Money;

public class APCredit extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Supplier supplier;
    private APInvoice invoice;
    private String supplierCreditNumber;
    private String status = "DRAFT";
    private Date creditDate;
    private Date postedDate;
    private String reason;
    private BigDecimal netAmount = Money.ZERO;
    private BigDecimal taxAmount = Money.ZERO;
    private BigDecimal totalAmount = Money.ZERO;
    private String createdBy;
    private Long createdById;
    private Date createdAt;
    private String approvedBy;
    private Long approvedById;
    private Date approvedAt;
    private String cancelledBy;
    private Date cancelledAt;
    private String cancellationReason;
    private List<APCreditLine> lines = new ArrayList<APCreditLine>();

    public String getNumber() { return number; }
    public void setNumber(String value) { number = value; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier value) { supplier = value; }
    public APInvoice getInvoice() { return invoice; }
    public void setInvoice(APInvoice value) { invoice = value; }
    public String getSupplierCreditNumber() { return supplierCreditNumber; }
    public void setSupplierCreditNumber(String value) { supplierCreditNumber = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Date getCreditDate() { return creditDate; }
    public void setCreditDate(Date value) { creditDate = value; }
    public Date getPostedDate() { return postedDate; }
    public void setPostedDate(Date value) { postedDate = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal value) { netAmount = value; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal value) { taxAmount = value; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal value) { totalAmount = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Long getCreatedById() { return createdById; }
    public void setCreatedById(Long value) { createdById = value; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date value) { createdAt = value; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String value) { approvedBy = value; }
    public Long getApprovedById() { return approvedById; }
    public void setApprovedById(Long value) { approvedById = value; }
    public Date getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Date value) { approvedAt = value; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String value) { cancelledBy = value; }
    public Date getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Date value) { cancelledAt = value; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String value) { cancellationReason = value; }
    public List<APCreditLine> getLines() { return lines; }
    public void setLines(List<APCreditLine> value) { lines = value; }
}
