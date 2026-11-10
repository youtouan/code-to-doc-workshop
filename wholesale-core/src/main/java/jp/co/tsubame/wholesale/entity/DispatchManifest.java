package jp.co.tsubame.wholesale.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DispatchManifest extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private Warehouse warehouse;
    private String carrier;
    private Date plannedDispatchDate;
    private String status = "DRAFT";
    private String note = "";
    private String createdBy;
    private Date createdAt;
    private Date updatedAt;
    private String releasedBy;
    private Date releasedAt;
    private String dispatchedBy;
    private Date dispatchedAt;
    private Date dispatchDate;
    private String cancellationReason = "";
    private List<DispatchStop> stops = new ArrayList<DispatchStop>();
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public Date getPlannedDispatchDate() { return plannedDispatchDate; }
    public void setPlannedDispatchDate(Date date) { plannedDispatchDate = date; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date at) { createdAt = at; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date at) { updatedAt = at; }
    public String getReleasedBy() { return releasedBy; }
    public void setReleasedBy(String value) { releasedBy = value; }
    public Date getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Date at) { releasedAt = at; }
    public String getDispatchedBy() { return dispatchedBy; }
    public void setDispatchedBy(String value) { dispatchedBy = value; }
    public Date getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(Date at) { dispatchedAt = at; }
    public Date getDispatchDate() { return dispatchDate; }
    public void setDispatchDate(Date date) { dispatchDate = date; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String reason) { cancellationReason = reason; }
    public List<DispatchStop> getStops() { return stops; }
    public void setStops(List<DispatchStop> stops) { this.stops = stops; }
    public String getTrackingReferenceBasis() { return "OWN".equals(carrier) ? "MANUAL_INTERNAL_REFERENCE" : "MANUAL_CARRIER_REFERENCE"; }
}
