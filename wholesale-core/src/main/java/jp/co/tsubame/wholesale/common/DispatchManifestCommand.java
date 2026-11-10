package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DispatchManifestCommand {
    private Long id;
    private int expectedVersion;
    private Long warehouseId;
    private Date plannedDispatchDate;
    private String carrier;
    private String note = "";
    private List<DispatchStopCommand> stops = new ArrayList<DispatchStopCommand>();
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(int version) { expectedVersion = version; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long id) { warehouseId = id; }
    public Date getPlannedDispatchDate() { return plannedDispatchDate; }
    public void setPlannedDispatchDate(Date date) { plannedDispatchDate = date; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<DispatchStopCommand> getStops() { return stops; }
    public void setStops(List<DispatchStopCommand> stops) { this.stops = stops; }
}
