package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;

public class APInvoiceLineInput {
    private Long productId;
    private String description;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;

    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { unitPrice = value; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal value) { taxRate = value; }
}
