package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;

public class StockValuationRow {
    private final Long warehouseId;
    private final String warehouseCode;
    private final Long productId;
    private final String productCode;
    private final String productName;
    private final long onHand;
    private final long reserved;
    private final boolean blocked;
    private final BigDecimal currentStandardCost;
    private final long outgoingTransitQuantity;
    private final BigDecimal outgoingTransitValue;
    private final long incomingTransitQuantity;
    private final BigDecimal incomingTransitValue;

    public StockValuationRow(Object[] row) {
        warehouseId = number(row[0]); warehouseCode = (String) row[1];
        productId = number(row[2]); productCode = (String) row[3]; productName = (String) row[4];
        onHand = number(row[5]); reserved = number(row[6]); blocked = (Boolean) row[7];
        currentStandardCost = (BigDecimal) row[8];
        outgoingTransitQuantity = number(row[9]); outgoingTransitValue = (BigDecimal) row[10];
        incomingTransitQuantity = number(row[11]); incomingTransitValue = (BigDecimal) row[12];
    }

    private static long number(Object value) { return ((Number) value).longValue(); }
    public Long getWarehouseId() { return warehouseId; }
    public String getWarehouseCode() { return warehouseCode; }
    public Long getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public long getOnHand() { return onHand; }
    public long getReserved() { return reserved; }
    public boolean isBlocked() { return blocked; }
    public long getAvailable() { return blocked ? 0L : onHand - reserved; }
    public BigDecimal getCurrentStandardCost() { return currentStandardCost; }
    public String getPhysicalCostBasis() { return "CURRENT_PRODUCT_STANDARD_COST"; }
    public String getTransitCostBasis() { return "STANDARD_COST_AT_DISPATCH"; }
    public BigDecimal getOnHandValue() { return currentStandardCost.multiply(BigDecimal.valueOf(onHand)); }
    public BigDecimal getReservedValue() { return currentStandardCost.multiply(BigDecimal.valueOf(reserved)); }
    public BigDecimal getAvailableValue() { return currentStandardCost.multiply(BigDecimal.valueOf(getAvailable())); }
    public long getOutgoingTransitQuantity() { return outgoingTransitQuantity; }
    public BigDecimal getOutgoingTransitValue() { return outgoingTransitValue; }
    public long getIncomingTransitQuantity() { return incomingTransitQuantity; }
    public BigDecimal getIncomingTransitValue() { return incomingTransitValue; }
    public BigDecimal getAttributedInventoryValue() { return getOnHandValue().add(outgoingTransitValue); }
}
