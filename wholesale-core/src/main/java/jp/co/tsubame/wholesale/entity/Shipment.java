package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.TaxAmounts;

public class Shipment extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private SalesOrder order;
    private String status;
    private Date plannedDate;
    private Date shippedDate;
    private String carrier;
    private String trackingNumber;
    private String note;
    private String createdBy;
    private Date createdAt;
    private String confirmedBy;
    private Date confirmedAt;
    private String cancellationReason;
    private Invoice invoice;
    private List<ShipmentLine> lines = new ArrayList<ShipmentLine>();

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public SalesOrder getOrder() {
        return order;
    }

    public void setOrder(SalesOrder order) {
        this.order = order;
    }

    public Warehouse getWarehouse() {
        return order.getWarehouse();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(Date plannedDate) {
        this.plannedDate = plannedDate;
    }

    public Date getShippedDate() {
        return shippedDate;
    }

    public void setShippedDate(Date shippedDate) {
        this.shippedDate = shippedDate;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public Date getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Date confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public List<ShipmentLine> getLines() {
        return lines;
    }

    public void setLines(List<ShipmentLine> lines) {
        this.lines = lines;
    }

    public BigDecimal getNetAmount() {
        return amounts().getNetAmount();
    }

    public BigDecimal getTaxAmount() {
        return amounts().getTaxAmount();
    }

    public BigDecimal getTotalAmount() {
        return amounts().getTotalAmount();
    }

    private TaxAmounts amounts() {
        TaxAmounts result = new TaxAmounts(order.getTaxRounding());
        for (ShipmentLine line : lines) {
            result.add(line.getNetAmount(), line.getTaxRate());
        }
        return result;
    }
}
