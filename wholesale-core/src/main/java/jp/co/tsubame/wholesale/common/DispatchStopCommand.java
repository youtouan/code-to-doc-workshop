package jp.co.tsubame.wholesale.common;

public class DispatchStopCommand {
    private Long shipmentId;
    private int expectedShipmentVersion;
    private String note = "";
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long id) { shipmentId = id; }
    public int getExpectedShipmentVersion() { return expectedShipmentVersion; }
    public void setExpectedShipmentVersion(int version) { expectedShipmentVersion = version; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
