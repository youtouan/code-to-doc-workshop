package jp.co.tsubame.wholesale.web.form;

import java.util.Arrays;
import jp.co.tsubame.wholesale.web.Inputs;

public class OrderForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String orderDate = "";
    private String requestedDate = "";
    private String deliveryAddress = "";
    private String externalReference = "";
    private String notes = "";
    private String[] productId = new String[0];
    private String[] quantity = new String[0];
    private String[] priceOverride = new String[0];
    private String[] priceReason = new String[0];
    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String value) { orderDate = value; }
    public String getRequestedDate() { return requestedDate; }
    public void setRequestedDate(String value) { requestedDate = value; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String value) { deliveryAddress = value; }
    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String value) { externalReference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public String[] getProductId() { return productId; }
    public void setProductId(String[] value) { productId = value; }
    public String[] getQuantity() { return quantity; }
    public void setQuantity(String[] value) { quantity = value; }
    public String[] getPriceOverride() { return priceOverride; }
    public void setPriceOverride(String[] value) { priceOverride = value; }
    public String[] getPriceReason() { return priceReason; }
    public void setPriceReason(String[] value) { priceReason = value; }
    public void rows(int count) {
        if (count < 1 || count > Inputs.MAX_LINES) { throw Inputs.invalid("明細", "明細は100行以内です。"); }
        productId = grow(productId, count);
        quantity = grow(quantity, count);
        priceOverride = grow(priceOverride, count);
        priceReason = grow(priceReason, count);
    }
    private String[] grow(String[] values, int count) {
        String[] result = Arrays.copyOf(values, count);
        for (int i = 0; i < count; i++) { if (result[i] == null) { result[i] = ""; } }
        return result;
    }
}
