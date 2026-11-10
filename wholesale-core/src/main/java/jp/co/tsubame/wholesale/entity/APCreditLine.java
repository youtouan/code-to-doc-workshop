package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;

public class APCreditLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private APCredit credit;
    private APInvoiceLine invoiceLine;
    private int lineNumber;
    private String description;
    private BigDecimal netAmount;
    private BigDecimal taxRate;

    public APCredit getCredit() { return credit; }
    public void setCredit(APCredit value) { credit = value; }
    public APInvoiceLine getInvoiceLine() { return invoiceLine; }
    public void setInvoiceLine(APInvoiceLine value) { invoiceLine = value; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int value) { lineNumber = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal value) { netAmount = value; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal value) { taxRate = value; }
}
