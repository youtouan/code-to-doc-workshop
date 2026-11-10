package jp.co.tsubame.wholesale.entity;

import java.util.Date;
import jp.co.tsubame.wholesale.common.Dates;

public class DeliveryAttempt extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Shipment shipment;
    private int sequenceNumber;
    private String eventType;
    private Long supersedesId;
    private String requestKey;
    private String fingerprint;
    private Date attemptAt;
    private Date businessDate;
    private String outcome;
    private String reportingCompany;
    private String evidenceReference;
    private String reason = "";
    private Date nextAttemptDate;
    private String correctionReason = "";
    private String recordedBy;
    private Date recordedAt;
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment shipment) { this.shipment = shipment; }
    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int number) { sequenceNumber = number; }
    public String getEventType() { return eventType; }
    public void setEventType(String type) { eventType = type; }
    public Long getSupersedesId() { return supersedesId; }
    public void setSupersedesId(Long id) { supersedesId = id; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String key) { requestKey = key; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public Date getAttemptAt() { return attemptAt; }
    public void setAttemptAt(Date at) { attemptAt = at; }
    public Date getBusinessDate() { return businessDate; }
    public void setBusinessDate(Date date) { businessDate = date; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getReportingCompany() { return reportingCompany; }
    public void setReportingCompany(String company) { reportingCompany = company; }
    public String getEvidenceReference() { return evidenceReference; }
    public void setEvidenceReference(String reference) { evidenceReference = reference; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Date getNextAttemptDate() { return nextAttemptDate; }
    public void setNextAttemptDate(Date date) { nextAttemptDate = date; }
    public String getCorrectionReason() { return correctionReason; }
    public void setCorrectionReason(String reason) { correctionReason = reason; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String actor) { recordedBy = actor; }
    public Date getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Date at) { recordedAt = at; }
    public String getEvidenceBasis() { return "MANUAL_UNVERIFIED_REPORT"; }
    public String getBusinessTimeZone() { return "Asia/Tokyo"; }
    public String getBusinessDateText() { return Dates.format(businessDate); }
}
