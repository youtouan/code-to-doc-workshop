package jp.co.tsubame.wholesale.web.form;

public class DispatchForm extends ProductLinesForm {
    private static final long serialVersionUID = 1L;
    private String plannedDispatchDate = "";
    private String dispatchDate = "";
    private String carrier = "";
    private String[] shipmentChoice = new String[0];
    private String[] stopNote = new String[0];
    private String[] shipmentId = new String[0];
    private String[] trackingReference = new String[0];
    public String getPlannedDispatchDate() { return plannedDispatchDate; }
    public void setPlannedDispatchDate(String value) { plannedDispatchDate = value; }
    public String getDispatchDate() { return dispatchDate; }
    public void setDispatchDate(String value) { dispatchDate = value; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String value) { carrier = value; }
    public String[] getShipmentChoice() { return shipmentChoice; }
    public void setShipmentChoice(String[] value) { shipmentChoice = value; }
    public String[] getStopNote() { return stopNote; }
    public void setStopNote(String[] value) { stopNote = value; }
    public String[] getShipmentId() { return shipmentId; }
    public void setShipmentId(String[] value) { shipmentId = value; }
    public String[] getTrackingReference() { return trackingReference; }
    public void setTrackingReference(String[] value) { trackingReference = value; }
    public void rows(int count) {
        super.rows(count); shipmentChoice = grow(shipmentChoice, count); stopNote = grow(stopNote, count);
    }
}
