package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StockTransfer extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private String status = "DRAFT";
    private String note = "";
    private Long createdById;
    private String createdBy;
    private Date createdAt;
    private Date updatedAt;
    private String approvedBy;
    private Date approvedAt;
    private String dispatchedBy;
    private Date dispatchedAt;
    private Date completedAt;
    private String cancellationReason = "";
    private List<StockTransferLine> lines = new ArrayList<StockTransferLine>();

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Warehouse getSourceWarehouse() { return sourceWarehouse; }
    public void setSourceWarehouse(Warehouse value) { sourceWarehouse = value; }
    public Warehouse getDestinationWarehouse() { return destinationWarehouse; }
    public void setDestinationWarehouse(Warehouse value) { destinationWarehouse = value; }
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
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String value) { approvedBy = value; }
    public Date getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Date value) { approvedAt = value; }
    public String getDispatchedBy() { return dispatchedBy; }
    public void setDispatchedBy(String value) { dispatchedBy = value; }
    public Date getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(Date value) { dispatchedAt = value; }
    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date value) { completedAt = value; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String value) { cancellationReason = value; }
    public List<StockTransferLine> getLines() { return lines; }
    public void setLines(List<StockTransferLine> lines) { this.lines = lines; }
    public long getInTransitQuantity() {
        long result = 0;
        for (StockTransferLine line : lines) { result += line.getInTransitQuantity(); }
        return result;
    }
    public BigDecimal getInTransitValue() {
        BigDecimal result = new BigDecimal("0.00");
        for (StockTransferLine line : lines) { result = result.add(line.getInTransitValue()); }
        return result;
    }
}
