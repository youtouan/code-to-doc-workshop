package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class QuotationRevision extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Quotation quotation;
    private int revisionNumber;
    private Warehouse warehouse;
    private String customerName;
    private Date quoteDate;
    private Date validUntil;
    private Date requestedDate;
    private String deliveryAddress;
    private String externalReference;
    private String notes;
    private String taxRounding;
    private BigDecimal netAmount;
    private BigDecimal taxAmount;
    private String fingerprint;
    private Long authoredById;
    private String authoredBy;
    private Date authoredAt;
    private String changeReason;
    private List<QuotationLine> lines = new ArrayList<QuotationLine>();
    public Quotation getQuotation() { return quotation; }
    public void setQuotation(Quotation value) { quotation = value; }
    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int value) { revisionNumber = value; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse value) { warehouse = value; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = value; }
    public Date getQuoteDate() { return quoteDate; }
    public void setQuoteDate(Date value) { quoteDate = value; }
    public Date getValidUntil() { return validUntil; }
    public void setValidUntil(Date value) { validUntil = value; }
    public Date getRequestedDate() { return requestedDate; }
    public void setRequestedDate(Date value) { requestedDate = value; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String value) { deliveryAddress = value; }
    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String value) { externalReference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public String getTaxRounding() { return taxRounding; }
    public void setTaxRounding(String value) { taxRounding = value; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal value) { netAmount = value; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal value) { taxAmount = value; }
    public BigDecimal getTotalAmount() { return netAmount.add(taxAmount); }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String value) { fingerprint = value; }
    public Long getAuthoredById() { return authoredById; }
    public void setAuthoredById(Long value) { authoredById = value; }
    public String getAuthoredBy() { return authoredBy; }
    public void setAuthoredBy(String value) { authoredBy = value; }
    public Date getAuthoredAt() { return authoredAt; }
    public void setAuthoredAt(Date value) { authoredAt = value; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String value) { changeReason = value; }
    public List<QuotationLine> getLines() { return lines; }
    public void setLines(List<QuotationLine> value) { lines = value; }
}
