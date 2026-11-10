package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import jp.co.tsubame.wholesale.common.Money;

public class SalesOrderLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private SalesOrder order;
    private int lineNumber;
    private Product product;
    private String productCode;
    private String productName;
    private String unit;
    private int packSize;
    private int quantity;
    private int allocatedQuantity;
    private int shippedQuantity;
    private int cancelledQuantity;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private String priceReason;

    public SalesOrder getOrder() {
        return order;
    }

    public void setOrder(SalesOrder order) {
        this.order = order;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
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

    public int getPackSize() {
        return packSize;
    }

    public void setPackSize(int packSize) {
        this.packSize = packSize;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getAllocatedQuantity() {
        return allocatedQuantity;
    }

    public void setAllocatedQuantity(int allocatedQuantity) {
        this.allocatedQuantity = allocatedQuantity;
    }

    public int getShippedQuantity() {
        return shippedQuantity;
    }

    public void setShippedQuantity(int shippedQuantity) {
        this.shippedQuantity = shippedQuantity;
    }

    public int getCancelledQuantity() {
        return cancelledQuantity;
    }

    public void setCancelledQuantity(int cancelledQuantity) {
        this.cancelledQuantity = cancelledQuantity;
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

    public String getPriceReason() {
        return priceReason;
    }

    public void setPriceReason(String priceReason) {
        this.priceReason = priceReason;
    }

    public BigDecimal getNetAmount() {
        return Money.amount(unitPrice, quantity);
    }

    public int getOpenQuantity() {
        return quantity - shippedQuantity - cancelledQuantity;
    }

    public int getShortageQuantity() {
        return getOpenQuantity() - allocatedQuantity;
    }
}
