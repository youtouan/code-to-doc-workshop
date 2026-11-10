package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OrderAmendment extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private SalesOrder order;
    private int baseOrderVersion;
    private String sourceFingerprint;
    private String status = "REQUESTED";
    private Date originalRequestedDate;
    private Date requestedDate;
    private String reason;
    private Long requestedById;
    private String requestedBy;
    private Date requestedAt;
    private Long decidedById;
    private String decidedBy;
    private Date decidedAt;
    private String decisionReason = "";
    private BigDecimal originalNetAmount;
    private BigDecimal originalTaxAmount;
    private BigDecimal proposedNetAmount;
    private BigDecimal proposedTaxAmount;
    private BigDecimal exposureDelta;
    private Integer appliedOrderVersion;
    private List<OrderAmendmentLine> lines = new ArrayList<OrderAmendmentLine>();
    public String getNumber() { return number; }
    public void setNumber(String value) { number = value; }
    public SalesOrder getOrder() { return order; }
    public void setOrder(SalesOrder value) { order = value; }
    public int getBaseOrderVersion() { return baseOrderVersion; }
    public void setBaseOrderVersion(int value) { baseOrderVersion = value; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String value) { sourceFingerprint = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Date getOriginalRequestedDate() { return originalRequestedDate; }
    public void setOriginalRequestedDate(Date value) { originalRequestedDate = value; }
    public Date getRequestedDate() { return requestedDate; }
    public void setRequestedDate(Date value) { requestedDate = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public Long getRequestedById() { return requestedById; }
    public void setRequestedById(Long value) { requestedById = value; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String value) { requestedBy = value; }
    public Date getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Date value) { requestedAt = value; }
    public Long getDecidedById() { return decidedById; }
    public void setDecidedById(Long value) { decidedById = value; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String value) { decidedBy = value; }
    public Date getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Date value) { decidedAt = value; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String value) { decisionReason = value; }
    public BigDecimal getOriginalNetAmount() { return originalNetAmount; }
    public void setOriginalNetAmount(BigDecimal value) { originalNetAmount = value; }
    public BigDecimal getOriginalTaxAmount() { return originalTaxAmount; }
    public void setOriginalTaxAmount(BigDecimal value) { originalTaxAmount = value; }
    public BigDecimal getProposedNetAmount() { return proposedNetAmount; }
    public void setProposedNetAmount(BigDecimal value) { proposedNetAmount = value; }
    public BigDecimal getProposedTaxAmount() { return proposedTaxAmount; }
    public void setProposedTaxAmount(BigDecimal value) { proposedTaxAmount = value; }
    public BigDecimal getExposureDelta() { return exposureDelta; }
    public void setExposureDelta(BigDecimal value) { exposureDelta = value; }
    public Integer getAppliedOrderVersion() { return appliedOrderVersion; }
    public void setAppliedOrderVersion(Integer value) { appliedOrderVersion = value; }
    public List<OrderAmendmentLine> getLines() { return lines; }
    public void setLines(List<OrderAmendmentLine> value) { lines = value; }
    public BigDecimal getOriginalTotalAmount() { return originalNetAmount.add(originalTaxAmount); }
    public BigDecimal getProposedTotalAmount() { return proposedNetAmount.add(proposedTaxAmount); }
}
