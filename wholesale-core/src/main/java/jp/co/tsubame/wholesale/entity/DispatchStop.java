package jp.co.tsubame.wholesale.entity;

public class DispatchStop extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private DispatchManifest manifest;
    private Shipment shipment;
    private int stopSequence;
    private int sourceShipmentVersion;
    private int sourceOrderVersion;
    private String sourceFingerprint;
    private String customerName;
    private String deliveryAddress;
    private String note = "";
    private boolean active = true;
    public DispatchManifest getManifest() { return manifest; }
    public void setManifest(DispatchManifest manifest) { this.manifest = manifest; }
    public Shipment getShipment() { return shipment; }
    public void setShipment(Shipment shipment) { this.shipment = shipment; }
    public int getStopSequence() { return stopSequence; }
    public void setStopSequence(int sequence) { stopSequence = sequence; }
    public int getSourceShipmentVersion() { return sourceShipmentVersion; }
    public void setSourceShipmentVersion(int version) { sourceShipmentVersion = version; }
    public int getSourceOrderVersion() { return sourceOrderVersion; }
    public void setSourceOrderVersion(int version) { sourceOrderVersion = version; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String fingerprint) { sourceFingerprint = fingerprint; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String name) { customerName = name; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String address) { deliveryAddress = address; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
