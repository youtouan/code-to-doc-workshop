package jp.co.tsubame.wholesale.entity;

import java.util.Date;

public class QuotationEvent extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Quotation quotation;
    private int revisionNumber;
    private String operation;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private String fingerprint;
    private Long actorId;
    private String actor;
    private Date occurredAt;
    public Quotation getQuotation() { return quotation; }
    public void setQuotation(Quotation value) { quotation = value; }
    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int value) { revisionNumber = value; }
    public String getOperation() { return operation; }
    public void setOperation(String value) { operation = value; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String value) { fromStatus = value; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String value) { toStatus = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String value) { fingerprint = value; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long value) { actorId = value; }
    public String getActor() { return actor; }
    public void setActor(String value) { actor = value; }
    public Date getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Date value) { occurredAt = value; }
}
