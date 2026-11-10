package jp.co.tsubame.wholesale.common;

public class StockAdjustmentCommand {
    private Long warehouseId;
    private Long productId;
    private int quantityChange;
    private String reason;

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long id) { warehouseId = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long id) { productId = id; }
    public int getQuantityChange() { return quantityChange; }
    public void setQuantityChange(int value) { quantityChange = value; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
