package jp.co.tsubame.wholesale.common;

public class StockReconciliationRow {
    private final Long balanceId;
    private final Long warehouseId;
    private final String warehouseCode;
    private final Long productId;
    private final String productCode;
    private final String productName;
    private final long onHand;
    private final long reserved;
    private final boolean blocked;
    private final long ledgerOnHand;
    private final long ledgerReserved;
    private final long reservationQuantity;
    private final long allocatedOrderQuantity;
    private final long brokenMovementSnapshots;
    private final long invalidReservations;
    private final long activeCountHolds;
    private final boolean consistent;

    public StockReconciliationRow(Object[] row) {
        balanceId = number(row[0]); warehouseId = number(row[1]); warehouseCode = (String) row[2];
        productId = number(row[3]); productCode = (String) row[4]; productName = (String) row[5];
        onHand = number(row[6]); reserved = number(row[7]); blocked = (Boolean) row[8];
        ledgerOnHand = number(row[9]); ledgerReserved = number(row[10]);
        reservationQuantity = number(row[11]); allocatedOrderQuantity = number(row[12]);
        brokenMovementSnapshots = number(row[13]); invalidReservations = number(row[14]);
        activeCountHolds = number(row[15]); consistent = (Boolean) row[16];
    }

    private static long number(Object value) { return ((Number) value).longValue(); }
    public Long getBalanceId() { return balanceId; }
    public Long getWarehouseId() { return warehouseId; }
    public String getWarehouseCode() { return warehouseCode; }
    public Long getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public long getOnHand() { return onHand; }
    public long getReserved() { return reserved; }
    public boolean isBlocked() { return blocked; }
    public long getLedgerOnHand() { return ledgerOnHand; }
    public long getLedgerReserved() { return ledgerReserved; }
    public long getReservationQuantity() { return reservationQuantity; }
    public long getAllocatedOrderQuantity() { return allocatedOrderQuantity; }
    public long getBrokenMovementSnapshots() { return brokenMovementSnapshots; }
    public long getInvalidReservations() { return invalidReservations; }
    public long getActiveCountHolds() { return activeCountHolds; }
    public boolean isConsistent() { return consistent; }
    public long getStockDifference() { return onHand - ledgerOnHand; }
    public long getReservedLedgerDifference() { return reserved - ledgerReserved; }
    public long getReservationDifference() { return reserved - reservationQuantity; }
    public long getAllocationDifference() { return reservationQuantity - allocatedOrderQuantity; }
}
