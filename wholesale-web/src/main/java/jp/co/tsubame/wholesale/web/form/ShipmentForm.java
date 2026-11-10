package jp.co.tsubame.wholesale.web.form;

public class ShipmentForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String orderId = "";
    private String plannedDate = "";
    private String shippedDate = "";
    private String carrier = "";
    private String trackingNumber = "";
    private String note = "";
    private String[] lineId = new String[0];
    private String[] quantity = new String[0];
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }
    public String getPlannedDate() { return plannedDate; }
    public void setPlannedDate(String value) { plannedDate = value; }
    public String getShippedDate() { return shippedDate; }
    public void setShippedDate(String value) { shippedDate = value; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String value) { carrier = value; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String value) { trackingNumber = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public String[] getLineId() { return lineId; }
    public void setLineId(String[] value) { lineId = value; }
    public String[] getQuantity() { return quantity; }
    public void setQuantity(String[] value) { quantity = value; }
}
