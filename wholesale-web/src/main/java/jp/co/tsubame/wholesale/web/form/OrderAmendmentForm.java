package jp.co.tsubame.wholesale.web.form;

public class OrderAmendmentForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String orderId = "";
    private String expectedOrderVersion = "0";
    private String requestedDate = "";
    private String[] orderLineId = new String[0];
    private String[] targetQuantity = new String[0];
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }
    public String getExpectedOrderVersion() { return expectedOrderVersion; }
    public void setExpectedOrderVersion(String value) { expectedOrderVersion = value; }
    public String getRequestedDate() { return requestedDate; }
    public void setRequestedDate(String value) { requestedDate = value; }
    public String[] getOrderLineId() { return orderLineId; }
    public void setOrderLineId(String[] value) { orderLineId = value; }
    public String[] getTargetQuantity() { return targetQuantity; }
    public void setTargetQuantity(String[] value) { targetQuantity = value; }
}
