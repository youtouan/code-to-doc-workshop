package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;

public class StockTransferLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private StockTransfer transfer;
    private Product product;
    private int quantity;
    private int dispatchedQuantity;
    private int receivedQuantity;
    private int lostQuantity;
    private BigDecimal unitCost = new BigDecimal("0.00");

    public StockTransfer getTransfer() { return transfer; }
    public void setTransfer(StockTransfer transfer) { this.transfer = transfer; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getDispatchedQuantity() { return dispatchedQuantity; }
    public void setDispatchedQuantity(int value) { dispatchedQuantity = value; }
    public int getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(int value) { receivedQuantity = value; }
    public int getLostQuantity() { return lostQuantity; }
    public void setLostQuantity(int value) { lostQuantity = value; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal cost) { unitCost = cost; }
    public String getCostBasis() { return "STANDARD_COST_AT_DISPATCH"; }
    public int getInTransitQuantity() { return dispatchedQuantity - receivedQuantity - lostQuantity; }
    public BigDecimal getInTransitValue() {
        return unitCost.multiply(BigDecimal.valueOf(getInTransitQuantity()));
    }
    public BigDecimal getLostValue() { return unitCost.multiply(BigDecimal.valueOf(lostQuantity)); }
}
