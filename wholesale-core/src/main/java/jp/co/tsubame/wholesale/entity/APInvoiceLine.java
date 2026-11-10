package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class APInvoiceLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private APInvoice invoice;
    private int lineNumber;
    private Product product;
    private String productCode;
    private String description;
    private String unit;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal netAmount;
    private List<APMatch> matches = new ArrayList<APMatch>();

    public APInvoice getInvoice() { return invoice; }
    public void setInvoice(APInvoice value) { invoice = value; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int value) { lineNumber = value; }
    public Product getProduct() { return product; }
    public void setProduct(Product value) { product = value; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String value) { productCode = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { unitPrice = value; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal value) { taxRate = value; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal value) { netAmount = value; }
    public List<APMatch> getMatches() { return matches; }
    public void setMatches(List<APMatch> value) { matches = value; }
    public int getMatchedQuantity() {
        int count = 0;
        for (APMatch match : matches) { count += match.getQuantity(); }
        return count;
    }
    public int getUnmatchedQuantity() { return quantity - getMatchedQuantity(); }
}
