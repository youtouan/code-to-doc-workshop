package jp.co.tsubame.wholesale.web.form;

public class PurchaseReceiptForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String orderId = "";
    private String requestKey = "";
    private String receiptDate = "";
    private String supplierDeliveryNumber = "";
    private String notes = "";
    private String[] lineId = new String[0];
    private String[] acceptedQuantity = new String[0];
    private String[] rejectedQuantity = new String[0];
    private String[] rejectionReason = new String[0];
    private String[] lineNote = new String[0];
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getReceiptDate() { return receiptDate; }
    public void setReceiptDate(String value) { receiptDate = value; }
    public String getSupplierDeliveryNumber() { return supplierDeliveryNumber; }
    public void setSupplierDeliveryNumber(String value) { supplierDeliveryNumber = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public String[] getLineId() { return lineId; }
    public void setLineId(String[] value) { lineId = value; }
    public String[] getAcceptedQuantity() { return acceptedQuantity; }
    public void setAcceptedQuantity(String[] value) { acceptedQuantity = value; }
    public String[] getRejectedQuantity() { return rejectedQuantity; }
    public void setRejectedQuantity(String[] value) { rejectedQuantity = value; }
    public String[] getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String[] value) { rejectionReason = value; }
    public String[] getLineNote() { return lineNote; }
    public void setLineNote(String[] value) { lineNote = value; }
}
