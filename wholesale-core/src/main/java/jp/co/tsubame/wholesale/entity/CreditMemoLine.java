package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;

public class CreditMemoLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private CreditMemo creditMemo;
    private SalesReturnLine salesReturnLine;
    private ShipmentLine shipmentLine;
    private int lineNumber;
    private String productCode;
    private String description;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal netAmount;

    public CreditMemo getCreditMemo() { return creditMemo; }
    public void setCreditMemo(CreditMemo creditMemo) { this.creditMemo = creditMemo; }
    public SalesReturnLine getSalesReturnLine() { return salesReturnLine; }
    public void setSalesReturnLine(SalesReturnLine salesReturnLine) { this.salesReturnLine = salesReturnLine; }
    public ShipmentLine getShipmentLine() { return shipmentLine; }
    public void setShipmentLine(ShipmentLine shipmentLine) { this.shipmentLine = shipmentLine; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
}
