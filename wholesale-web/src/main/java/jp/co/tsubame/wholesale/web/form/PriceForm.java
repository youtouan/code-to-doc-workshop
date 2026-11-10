package jp.co.tsubame.wholesale.web.form;

public class PriceForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String productId = "";
    private String validFrom = "";
    private String validTo = "";
    private String minimumQuantity = "1";
    private String unitPrice = "";
    private String notes = "";
    public String getProductId() { return productId; }
    public void setProductId(String value) { productId = value; }
    public String getValidFrom() { return validFrom; }
    public void setValidFrom(String value) { validFrom = value; }
    public String getValidTo() { return validTo; }
    public void setValidTo(String value) { validTo = value; }
    public String getMinimumQuantity() { return minimumQuantity; }
    public void setMinimumQuantity(String value) { minimumQuantity = value; }
    public String getUnitPrice() { return unitPrice; }
    public void setUnitPrice(String value) { unitPrice = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
}
