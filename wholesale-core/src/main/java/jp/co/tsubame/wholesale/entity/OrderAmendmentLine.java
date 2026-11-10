package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;

public class OrderAmendmentLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private OrderAmendment amendment;
    private SalesOrderLine orderLine;
    private int originalQuantity;
    private int targetQuantity;
    private int originalAllocatedQuantity;
    private int originalShippedQuantity;
    private int originalCancelledQuantity;
    private int packSize;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    public OrderAmendment getAmendment() { return amendment; }
    public void setAmendment(OrderAmendment value) { amendment = value; }
    public SalesOrderLine getOrderLine() { return orderLine; }
    public void setOrderLine(SalesOrderLine value) { orderLine = value; }
    public int getOriginalQuantity() { return originalQuantity; }
    public void setOriginalQuantity(int value) { originalQuantity = value; }
    public int getTargetQuantity() { return targetQuantity; }
    public void setTargetQuantity(int value) { targetQuantity = value; }
    public int getOriginalAllocatedQuantity() { return originalAllocatedQuantity; }
    public void setOriginalAllocatedQuantity(int value) { originalAllocatedQuantity = value; }
    public int getOriginalShippedQuantity() { return originalShippedQuantity; }
    public void setOriginalShippedQuantity(int value) { originalShippedQuantity = value; }
    public int getOriginalCancelledQuantity() { return originalCancelledQuantity; }
    public void setOriginalCancelledQuantity(int value) { originalCancelledQuantity = value; }
    public int getPackSize() { return packSize; }
    public void setPackSize(int value) { packSize = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { unitPrice = value; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal value) { taxRate = value; }
}
