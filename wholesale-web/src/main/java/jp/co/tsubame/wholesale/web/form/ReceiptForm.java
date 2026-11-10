package jp.co.tsubame.wholesale.web.form;

public class ReceiptForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String requestKey = "";
    private String productId = "";
    private String quantity = "";
    private String unitCost = "";
    private String receiptDate = "";
    private String reference = "";
    private String note = "";
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getProductId() { return productId; }
    public void setProductId(String value) { productId = value; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String value) { quantity = value; }
    public String getUnitCost() { return unitCost; }
    public void setUnitCost(String value) { unitCost = value; }
    public String getReceiptDate() { return receiptDate; }
    public void setReceiptDate(String value) { receiptDate = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
}
