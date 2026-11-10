package jp.co.tsubame.wholesale.web.form;

public class AdjustmentForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String productId = "";
    private String quantityChange = "";
    public String getProductId() { return productId; }
    public void setProductId(String value) { productId = value; }
    public String getQuantityChange() { return quantityChange; }
    public void setQuantityChange(String value) { quantityChange = value; }
}
