package jp.co.tsubame.wholesale.web.form;

public class ReturnForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String shipmentId = "";
    private String returnReason = "CUSTOMER_CHANGE";
    private String notes = "";
    private String receivedDate = "";
    private String[] lineId = new String[0];
    private String[] quantity = new String[0];
    private String[] restock = new String[0];
    public String getShipmentId() { return shipmentId; }
    public void setShipmentId(String value) { shipmentId = value; }
    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String value) { returnReason = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public String getReceivedDate() { return receivedDate; }
    public void setReceivedDate(String value) { receivedDate = value; }
    public String[] getLineId() { return lineId; }
    public void setLineId(String[] value) { lineId = value; }
    public String[] getQuantity() { return quantity; }
    public void setQuantity(String[] value) { quantity = value; }
    public String[] getRestock() { return restock; }
    public void setRestock(String[] value) { restock = value; }
}
