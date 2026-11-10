package jp.co.tsubame.wholesale.web.form;

public class CatalogForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String code = "";
    private String name = "";
    private String active = "true";
    private String onHold = "false";
    private String creditLimit = "0";
    private String closingDay = "31";
    private String paymentTermDays = "30";
    private String taxRounding = "DOWN";
    private String postalCode = "";
    private String address = "";
    private String telephone = "";
    private String notes = "";
    private String unit = "個";
    private String taxCategory = "STANDARD";
    private String listPrice = "0";
    private String standardCost = "0";
    private String packSize = "1";
    private String reorderPoint = "0";
    private String reorderQuantity = "1";
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getActive() { return active; }
    public void setActive(String value) { active = value; }
    public String getOnHold() { return onHold; }
    public void setOnHold(String value) { onHold = value; }
    public String getCreditLimit() { return creditLimit; }
    public void setCreditLimit(String value) { creditLimit = value; }
    public String getClosingDay() { return closingDay; }
    public void setClosingDay(String value) { closingDay = value; }
    public String getPaymentTermDays() { return paymentTermDays; }
    public void setPaymentTermDays(String value) { paymentTermDays = value; }
    public String getTaxRounding() { return taxRounding; }
    public void setTaxRounding(String value) { taxRounding = value; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String value) { postalCode = value; }
    public String getAddress() { return address; }
    public void setAddress(String value) { address = value; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String value) { telephone = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public String getTaxCategory() { return taxCategory; }
    public void setTaxCategory(String value) { taxCategory = value; }
    public String getListPrice() { return listPrice; }
    public void setListPrice(String value) { listPrice = value; }
    public String getStandardCost() { return standardCost; }
    public void setStandardCost(String value) { standardCost = value; }
    public String getPackSize() { return packSize; }
    public void setPackSize(String value) { packSize = value; }
    public String getReorderPoint() { return reorderPoint; }
    public void setReorderPoint(String value) { reorderPoint = value; }
    public String getReorderQuantity() { return reorderQuantity; }
    public void setReorderQuantity(String value) { reorderQuantity = value; }
}
