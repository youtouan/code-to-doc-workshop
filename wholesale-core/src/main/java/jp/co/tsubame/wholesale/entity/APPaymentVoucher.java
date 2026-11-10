package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class APPaymentVoucher extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Supplier supplier;
    private String supplierName;
    private String requestKey;
    private String fingerprint;
    private Date paymentDate;
    private BigDecimal amount;
    private String method;
    private String reference;
    private String notes;
    private String status = "POSTED";
    private String createdBy;
    private Date createdAt;
    private String cancelledBy;
    private Date cancelledAt;
    private Date cancellationDate;
    private String cancellationReason;
    private List<APPaymentLine> lines = new ArrayList<APPaymentLine>();

    public String getNumber() { return number; }
    public void setNumber(String value) { number = value; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier value) { supplier = value; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String value) { supplierName = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String value) { fingerprint = value; }
    public Date getPaymentDate() { return paymentDate; }
    public void setPaymentDate(Date value) { paymentDate = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getMethod() { return method; }
    public void setMethod(String value) { method = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date value) { createdAt = value; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String value) { cancelledBy = value; }
    public Date getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Date value) { cancelledAt = value; }
    public Date getCancellationDate() { return cancellationDate; }
    public void setCancellationDate(Date value) { cancellationDate = value; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String value) { cancellationReason = value; }
    public List<APPaymentLine> getLines() { return lines; }
    public void setLines(List<APPaymentLine> value) { lines = value; }
}
