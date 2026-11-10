package jp.co.tsubame.wholesale.web.form;

import org.apache.struts.validator.ValidatorForm;

public class BaseForm extends ValidatorForm {
    private static final long serialVersionUID = 1L;
    private String op = "list";
    private String id = "";
    private String version = "0";
    private String text = "";
    private String status = "";
    private String customerId = "";
    private String warehouseId = "";
    private String from = "";
    private String to = "";
    private String pageNumber = "1";
    private String size = "25";
    private String reason = "";
    private String csrfToken = "";
    public String getOp() { return op; }
    public void setOp(String value) { op = value; }
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getVersion() { return version; }
    public void setVersion(String value) { version = value; }
    public String getText() { return text; }
    public void setText(String value) { text = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String value) { customerId = value; }
    public String getWarehouseId() { return warehouseId; }
    public void setWarehouseId(String value) { warehouseId = value; }
    public String getFrom() { return from; }
    public void setFrom(String value) { from = value; }
    public String getTo() { return to; }
    public void setTo(String value) { to = value; }
    public String getPageNumber() { return pageNumber; }
    public void setPageNumber(String value) { pageNumber = value; }
    public String getSize() { return size; }
    public void setSize(String value) { size = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getCsrfToken() { return csrfToken; }
    public void setCsrfToken(String value) { csrfToken = value; }
}
