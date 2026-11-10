package jp.co.tsubame.wholesale.web.form;

public class OperationsReportForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String dimension = "CUSTOMER";
    private String productId = "";
    private String supplierId = "";
    private String exceptionsOnly = "false";
    public String getDimension() { return dimension; }
    public void setDimension(String value) { dimension = value; }
    public String getProductId() { return productId; }
    public void setProductId(String value) { productId = value; }
    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String value) { supplierId = value; }
    public String getExceptionsOnly() { return exceptionsOnly; }
    public void setExceptionsOnly(String value) { exceptionsOnly = value; }
}
