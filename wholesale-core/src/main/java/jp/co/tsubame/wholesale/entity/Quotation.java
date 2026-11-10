package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Quotation extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Customer customer;
    private Warehouse warehouse;
    private int revisionNumber;
    private String status = "DRAFT";
    private Long createdById;
    private String createdBy;
    private Date createdAt;
    private Long submittedById;
    private String submittedBy;
    private Date submittedAt;
    private Long approvedById;
    private String approvedBy;
    private Date approvedAt;
    private String approvedFingerprint;
    private Long acceptedById;
    private String acceptedBy;
    private Date acceptedAt;
    private Date acceptedOn;
    private String acceptanceReference = "";
    private SalesOrder convertedOrder;
    private Date convertedAt;
    private Date updatedAt;
    private List<QuotationRevision> revisions = new ArrayList<QuotationRevision>();
    public String getNumber() { return number; }
    public void setNumber(String value) { number = value; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer value) { customer = value; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse value) { warehouse = value; }
    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int value) { revisionNumber = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Long getCreatedById() { return createdById; }
    public void setCreatedById(Long value) { createdById = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date value) { createdAt = value; }
    public Long getSubmittedById() { return submittedById; }
    public void setSubmittedById(Long value) { submittedById = value; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String value) { submittedBy = value; }
    public Date getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Date value) { submittedAt = value; }
    public Long getApprovedById() { return approvedById; }
    public void setApprovedById(Long value) { approvedById = value; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String value) { approvedBy = value; }
    public Date getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Date value) { approvedAt = value; }
    public String getApprovedFingerprint() { return approvedFingerprint; }
    public void setApprovedFingerprint(String value) { approvedFingerprint = value; }
    public Long getAcceptedById() { return acceptedById; }
    public void setAcceptedById(Long value) { acceptedById = value; }
    public String getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(String value) { acceptedBy = value; }
    public Date getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Date value) { acceptedAt = value; }
    public Date getAcceptedOn() { return acceptedOn; }
    public void setAcceptedOn(Date value) { acceptedOn = value; }
    public String getAcceptanceReference() { return acceptanceReference; }
    public void setAcceptanceReference(String value) { acceptanceReference = value; }
    public SalesOrder getConvertedOrder() { return convertedOrder; }
    public void setConvertedOrder(SalesOrder value) { convertedOrder = value; }
    public Date getConvertedAt() { return convertedAt; }
    public void setConvertedAt(Date value) { convertedAt = value; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date value) { updatedAt = value; }
    public List<QuotationRevision> getRevisions() { return revisions; }
    public void setRevisions(List<QuotationRevision> value) { revisions = value; }
    public QuotationRevision getCurrentRevision() {
        for (QuotationRevision revision : revisions) {
            if (revision.getRevisionNumber() == revisionNumber) { return revision; }
        }
        throw new IllegalStateException("Quotation current revision is missing: " + getId());
    }
    public BigDecimal getNetAmount() { return getCurrentRevision().getNetAmount(); }
    public BigDecimal getTaxAmount() { return getCurrentRevision().getTaxAmount(); }
    public BigDecimal getTotalAmount() { return getCurrentRevision().getTotalAmount(); }
    public Date getQuoteDate() { return getCurrentRevision().getQuoteDate(); }
    public Date getValidUntil() { return getCurrentRevision().getValidUntil(); }
    public Date getRequestedDate() { return getCurrentRevision().getRequestedDate(); }
    public List<QuotationLine> getLines() { return getCurrentRevision().getLines(); }
    public boolean isEditable() {
        return "DRAFT".equals(status) || "WITHDRAWN".equals(status) || "REJECTED".equals(status);
    }
}
