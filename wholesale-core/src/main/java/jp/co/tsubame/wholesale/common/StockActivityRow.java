package jp.co.tsubame.wholesale.common;

import java.util.Date;

public final class StockActivityRow {
    private final Date recordedDate;
    private final String warehouseCode;
    private final String movementType;
    private final long movementCount;
    private final long inboundQuantity;
    private final long outboundQuantity;
    private final long reservationIncrease;
    private final long reservationDecrease;

    public StockActivityRow(Date recordedDate, String warehouseCode, String movementType, long movementCount,
                            long inboundQuantity, long outboundQuantity, long reservationIncrease,
                            long reservationDecrease) {
        this.recordedDate = recordedDate;
        this.warehouseCode = warehouseCode;
        this.movementType = movementType;
        this.movementCount = movementCount;
        this.inboundQuantity = inboundQuantity;
        this.outboundQuantity = outboundQuantity;
        this.reservationIncrease = reservationIncrease;
        this.reservationDecrease = reservationDecrease;
    }

    public Date getRecordedDate() { return recordedDate; }
    public String getWarehouseCode() { return warehouseCode; }
    public String getMovementType() { return movementType; }
    public long getMovementCount() { return movementCount; }
    public long getInboundQuantity() { return inboundQuantity; }
    public long getOutboundQuantity() { return outboundQuantity; }
    public long getReservationIncrease() { return reservationIncrease; }
    public long getReservationDecrease() { return reservationDecrease; }

    public long getNetQuantity() {
        return inboundQuantity - outboundQuantity;
    }

    public long getNetReservationChange() {
        return reservationIncrease - reservationDecrease;
    }
}
