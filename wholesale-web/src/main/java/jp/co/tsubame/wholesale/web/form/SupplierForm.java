package jp.co.tsubame.wholesale.web.form;

public class SupplierForm extends CatalogForm {
    private static final long serialVersionUID = 1L;
    private String defaultLeadTimeDays = "3";
    private String minimumOrderAmount = "0";
    private String orderingInstructions = "";
    public String getDefaultLeadTimeDays() { return defaultLeadTimeDays; }
    public void setDefaultLeadTimeDays(String value) { defaultLeadTimeDays = value; }
    public String getMinimumOrderAmount() { return minimumOrderAmount; }
    public void setMinimumOrderAmount(String value) { minimumOrderAmount = value; }
    public String getOrderingInstructions() { return orderingInstructions; }
    public void setOrderingInstructions(String value) { orderingInstructions = value; }
}
