package jp.co.tsubame.wholesale.web.form;

public class SupplierProductForm extends PriceForm {
    private static final long serialVersionUID = 1L;
    private String supplierId = "";
    private String supplierProductCode = "";
    private String orderPackSize = "1";
    private String leadTimeDays = "3";
    private String unitCost = "";
    private String preferred = "false";
    private String active = "true";
    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String value) { supplierId = value; }
    public String getSupplierProductCode() { return supplierProductCode; }
    public void setSupplierProductCode(String value) { supplierProductCode = value; }
    public String getOrderPackSize() { return orderPackSize; }
    public void setOrderPackSize(String value) { orderPackSize = value; }
    public String getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(String value) { leadTimeDays = value; }
    public String getUnitCost() { return unitCost; }
    public void setUnitCost(String value) { unitCost = value; }
    public String getPreferred() { return preferred; }
    public void setPreferred(String value) { preferred = value; }
    public String getActive() { return active; }
    public void setActive(String value) { active = value; }
}
