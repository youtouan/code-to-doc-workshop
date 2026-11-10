package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StockCount extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Warehouse warehouse;
    private String status = "COUNTING";
    private String note = "";
    private Long createdById;
    private String createdBy;
    private Date createdAt;
    private Date updatedAt;
    private String reviewedBy;
    private Date reviewedAt;
    private String approvedBy;
    private Date approvedAt;
    private String cancellationReason = "";
    private List<StockCountLine> lines = new ArrayList<StockCountLine>();

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getCreatedById() { return createdById; }
    public void setCreatedById(Long id) { createdById = id; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date value) { createdAt = value; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date value) { updatedAt = value; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String value) { reviewedBy = value; }
    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date value) { reviewedAt = value; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String value) { approvedBy = value; }
    public Date getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Date value) { approvedAt = value; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String value) { cancellationReason = value; }
    public List<StockCountLine> getLines() { return lines; }
    public void setLines(List<StockCountLine> lines) { this.lines = lines; }
    public int getUncountedLines() {
        int result = 0;
        for (StockCountLine line : lines) { if (line.getCountedQuantity() == null) { result++; } }
        return result;
    }
    public int getDiscrepancyLines() {
        int result = 0;
        for (StockCountLine line : lines) {
            if (line.getDifference() != null && line.getDifference() != 0) { result++; }
        }
        return result;
    }
    public BigDecimal getCountedDifferenceValue() {
        BigDecimal result = new BigDecimal("0.00");
        for (StockCountLine line : lines) {
            if (line.getDifferenceValue() != null) { result = result.add(line.getDifferenceValue()); }
        }
        return result;
    }
    public int getReservationShortageLines() {
        int result = 0;
        for (StockCountLine line : lines) {
            if (line.getReservationShortage() != null && line.getReservationShortage() > 0) { result++; }
        }
        return result;
    }
}
