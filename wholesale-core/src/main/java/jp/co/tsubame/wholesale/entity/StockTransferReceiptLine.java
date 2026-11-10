package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;

public class StockTransferReceiptLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private StockTransferReceipt receipt;
    private StockTransferLine transferLine;
    private int quantity;
    private BigDecimal unitCost;

    public StockTransferReceipt getReceipt() { return receipt; }
    public void setReceipt(StockTransferReceipt value) { receipt = value; }
    public StockTransferLine getTransferLine() { return transferLine; }
    public void setTransferLine(StockTransferLine value) { transferLine = value; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal value) { unitCost = value; }
    public BigDecimal getValue() { return unitCost.multiply(BigDecimal.valueOf(quantity)); }
}
