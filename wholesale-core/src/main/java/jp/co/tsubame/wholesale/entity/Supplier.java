package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;

public class Supplier extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String code;
    private String name;
    private boolean active = true;
    private boolean onHold;
    private int closingDay = 31;
    private int paymentTermDays = 30;
    private int defaultLeadTimeDays = 3;
    private BigDecimal minimumOrderAmount = new BigDecimal("0.00");
    private String taxRounding = "DOWN";
    private String postalCode = "";
    private String address = "";
    private String telephone = "";
    private String orderingInstructions = "";
    private String notes = "";

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isOnHold() { return onHold; }
    public void setOnHold(boolean onHold) { this.onHold = onHold; }
    public int getClosingDay() { return closingDay; }
    public void setClosingDay(int closingDay) { this.closingDay = closingDay; }
    public int getPaymentTermDays() { return paymentTermDays; }
    public void setPaymentTermDays(int paymentTermDays) { this.paymentTermDays = paymentTermDays; }
    public int getDefaultLeadTimeDays() { return defaultLeadTimeDays; }
    public void setDefaultLeadTimeDays(int defaultLeadTimeDays) { this.defaultLeadTimeDays = defaultLeadTimeDays; }
    public BigDecimal getMinimumOrderAmount() { return minimumOrderAmount; }
    public void setMinimumOrderAmount(BigDecimal minimumOrderAmount) { this.minimumOrderAmount = minimumOrderAmount; }
    public String getTaxRounding() { return taxRounding; }
    public void setTaxRounding(String taxRounding) { this.taxRounding = taxRounding; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getOrderingInstructions() { return orderingInstructions; }
    public void setOrderingInstructions(String orderingInstructions) { this.orderingInstructions = orderingInstructions; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
