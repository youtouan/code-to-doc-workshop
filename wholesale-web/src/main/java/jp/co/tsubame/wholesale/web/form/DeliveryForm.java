package jp.co.tsubame.wholesale.web.form;

public class DeliveryForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String shipmentId = "";
    private String expectedLatestEventId = "";
    private String targetEventId = "";
    private String requestKey = "";
    private String attemptAt = "";
    private String outcome = "";
    private String reportingCompany = "";
    private String evidenceReference = "";
    private String nextAttemptDate = "";
    private String correctionReason = "";
    private String asOfRecordedAt = "";
    private String dueOnOrBefore = "";
    public String getShipmentId() { return shipmentId; }
    public void setShipmentId(String value) { shipmentId = value; }
    public String getExpectedLatestEventId() { return expectedLatestEventId; }
    public void setExpectedLatestEventId(String value) { expectedLatestEventId = value; }
    public String getTargetEventId() { return targetEventId; }
    public void setTargetEventId(String value) { targetEventId = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getAttemptAt() { return attemptAt; }
    public void setAttemptAt(String value) { attemptAt = value; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String value) { outcome = value; }
    public String getReportingCompany() { return reportingCompany; }
    public void setReportingCompany(String value) { reportingCompany = value; }
    public String getEvidenceReference() { return evidenceReference; }
    public void setEvidenceReference(String value) { evidenceReference = value; }
    public String getNextAttemptDate() { return nextAttemptDate; }
    public void setNextAttemptDate(String value) { nextAttemptDate = value; }
    public String getCorrectionReason() { return correctionReason; }
    public void setCorrectionReason(String value) { correctionReason = value; }
    public String getAsOfRecordedAt() { return asOfRecordedAt; }
    public void setAsOfRecordedAt(String value) { asOfRecordedAt = value; }
    public String getDueOnOrBefore() { return dueOnOrBefore; }
    public void setDueOnOrBefore(String value) { dueOnOrBefore = value; }
}
