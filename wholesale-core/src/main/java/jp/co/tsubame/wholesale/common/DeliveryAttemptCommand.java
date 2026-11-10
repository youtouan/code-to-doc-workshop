package jp.co.tsubame.wholesale.common;

import java.util.Date;

public class DeliveryAttemptCommand {
    private Long shipmentId;
    private Long expectedLatestEventId;
    private String requestKey;
    private Date attemptAt;
    private String outcome;
    private String reportingCompany;
    private String evidenceReference;
    private String reason = "";
    private Date nextAttemptDate;
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long id) { shipmentId = id; }
    public Long getExpectedLatestEventId() { return expectedLatestEventId; }
    public void setExpectedLatestEventId(Long id) { expectedLatestEventId = id; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String key) { requestKey = key; }
    public Date getAttemptAt() { return attemptAt; }
    public void setAttemptAt(Date at) { attemptAt = at; }
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
}
