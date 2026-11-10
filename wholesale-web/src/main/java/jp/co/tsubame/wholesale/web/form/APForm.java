package jp.co.tsubame.wholesale.web.form;

public class APForm extends ProductLinesForm {
    private static final long serialVersionUID = 1L;
    private String supplierId = "";
    private String asOf = "";
    private String overdueOnly = "false";
    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String value) { supplierId = value; }
    public String getAsOf() { return asOf; }
    public void setAsOf(String value) { asOf = value; }
    public String getOverdueOnly() { return overdueOnly; }
    public void setOverdueOnly(String value) { overdueOnly = value; }
}
