package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;

public final class SalesSummaryRow {
    private final Long dimensionId;
    private final String code;
    private final String name;
    private final long shippedQuantity;
    private final long returnedQuantity;
    private final long shipmentCount;
    private final long returnCount;
    private final BigDecimal shippedAmount;
    private final BigDecimal returnedAmount;

    public SalesSummaryRow(Long dimensionId, String code, String name, long shippedQuantity,
                           long returnedQuantity, long shipmentCount, long returnCount,
                           BigDecimal shippedAmount, BigDecimal returnedAmount) {
        this.dimensionId = dimensionId;
        this.code = code;
        this.name = name;
        this.shippedQuantity = shippedQuantity;
        this.returnedQuantity = returnedQuantity;
        this.shipmentCount = shipmentCount;
        this.returnCount = returnCount;
        this.shippedAmount = shippedAmount;
        this.returnedAmount = returnedAmount;
    }

    public Long getDimensionId() {
        return dimensionId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public long getShippedQuantity() {
        return shippedQuantity;
    }

    public long getReturnedQuantity() {
        return returnedQuantity;
    }

    public long getNetQuantity() {
        return shippedQuantity - returnedQuantity;
    }

    public long getShipmentCount() {
        return shipmentCount;
    }

    public long getReturnCount() {
        return returnCount;
    }

    public BigDecimal getShippedAmount() {
        return shippedAmount;
    }

    public BigDecimal getReturnedAmount() {
        return returnedAmount;
    }

    public BigDecimal getNetAmount() {
        return shippedAmount.subtract(returnedAmount);
    }
}
