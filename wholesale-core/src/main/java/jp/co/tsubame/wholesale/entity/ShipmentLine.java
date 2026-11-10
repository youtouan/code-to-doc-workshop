package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import jp.co.tsubame.wholesale.common.Money;

public class ShipmentLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Shipment shipment;
    private SalesOrderLine orderLine;
    private int lineNumber;
    private int quantity;
    private int returnedQuantity;
    private String productCode;
    private String productName;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;

    public Shipment getShipment() {
        return shipment;
    }

    public void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }

    public SalesOrderLine getOrderLine() {
        return orderLine;
    }

    public void setOrderLine(SalesOrderLine orderLine) {
        this.orderLine = orderLine;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getReturnedQuantity() {
        return returnedQuantity;
    }

    public void setReturnedQuantity(int returnedQuantity) {
        this.returnedQuantity = returnedQuantity;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getNetAmount() {
        return Money.amount(unitPrice, quantity);
    }

    public int getReturnableQuantity() {
        return quantity - returnedQuantity;
    }
}
