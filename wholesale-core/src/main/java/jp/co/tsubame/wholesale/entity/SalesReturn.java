package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.TaxAmounts;

public class SalesReturn extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Shipment shipment;
    private String status;
    private String reason;
    private Date requestedDate;
    private Date receivedDate;
    private String createdBy;
    private String approvedBy;
    private String receivedBy;
    private Date createdAt;
    private Date approvedAt;
    private Date receivedAt;
    private String notes;
    private List<SalesReturnLine> lines = new ArrayList<SalesReturnLine>();

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public Shipment getShipment() {
        return shipment;
    }

    public void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Date getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(Date requestedDate) {
        this.requestedDate = requestedDate;
    }

    public Date getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(Date receivedDate) {
        this.receivedDate = receivedDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Date approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Date getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Date receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<SalesReturnLine> getLines() {
        return lines;
    }

    public void setLines(List<SalesReturnLine> lines) {
        this.lines = lines;
    }

    public BigDecimal getNetAmount() {
        return amounts().getNetAmount();
    }

    public BigDecimal getTaxAmount() {
        return amounts().getTaxAmount();
    }

    private TaxAmounts amounts() {
        TaxAmounts result = new TaxAmounts(shipment.getOrder().getTaxRounding());
        for (SalesReturnLine line : lines) {
            result.add(line.getNetAmount(), line.getTaxRate());
        }
        return result;
    }
}
