package jp.co.tsubame.wholesale.common;

public class DispatchTrackingCommand {
    private Long shipmentId;
    private String trackingReference;
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long id) { shipmentId = id; }
    public String getTrackingReference() { return trackingReference; }
    public void setTrackingReference(String reference) { trackingReference = reference; }
}
