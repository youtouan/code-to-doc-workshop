package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import jp.co.tsubame.wholesale.common.Money;

public class QuotationLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private QuotationRevision revision;
    private int lineNumber;
    private Product product;
    private String productCode;
    private String productName;
    private String unit;
    private int packSize;
    private int quantity;
    private BigDecimal catalogUnitPrice;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private String negotiationReason;
    private boolean negotiated;
    public QuotationRevision getRevision() { return revision; }
    public void setRevision(QuotationRevision value) { revision = value; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int value) { lineNumber = value; }
    public Product getProduct() { return product; }
    public void setProduct(Product value) { product = value; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String value) { productCode = value; }
    public String getProductName() { return productName; }
    public void setProductName(String value) { productName = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public int getPackSize() { return packSize; }
    public void setPackSize(int value) { packSize = value; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }
    public BigDecimal getCatalogUnitPrice() { return catalogUnitPrice; }
    public void setCatalogUnitPrice(BigDecimal value) { catalogUnitPrice = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { unitPrice = value; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal value) { taxRate = value; }
    public String getNegotiationReason() { return negotiationReason; }
    public void setNegotiationReason(String value) { negotiationReason = value; }
    public boolean isNegotiated() { return negotiated; }
    public void setNegotiated(boolean value) { negotiated = value; }
    public BigDecimal getNetAmount() { return Money.amount(unitPrice, quantity); }
}
