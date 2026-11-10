package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.Date;

public class StockCountLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private StockCount stockCount;
    private StockBalance balance;
    private int snapshotOnHand;
    private int snapshotReserved;
    private int snapshotVersion;
    private Date snapshotMovementAt;
    private BigDecimal unitCost;
    private Integer countedQuantity;
    private String note = "";
    private String countedBy;
    private Date countedAt;
    private boolean holding = true;

    public StockCount getStockCount() { return stockCount; }
    public void setStockCount(StockCount value) { stockCount = value; }
    public StockBalance getBalance() { return balance; }
    public void setBalance(StockBalance balance) { this.balance = balance; }
    public Product getProduct() { return balance.getProduct(); }
    public int getSnapshotOnHand() { return snapshotOnHand; }
    public void setSnapshotOnHand(int value) { snapshotOnHand = value; }
    public int getSnapshotReserved() { return snapshotReserved; }
    public void setSnapshotReserved(int value) { snapshotReserved = value; }
    public int getSnapshotVersion() { return snapshotVersion; }
    public void setSnapshotVersion(int value) { snapshotVersion = value; }
    public Date getSnapshotMovementAt() { return snapshotMovementAt; }
    public void setSnapshotMovementAt(Date value) { snapshotMovementAt = value; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal value) { unitCost = value; }
    public Integer getCountedQuantity() { return countedQuantity; }
    public void setCountedQuantity(Integer value) { countedQuantity = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public String getCountedBy() { return countedBy; }
    public void setCountedBy(String value) { countedBy = value; }
    public Date getCountedAt() { return countedAt; }
    public void setCountedAt(Date value) { countedAt = value; }
    public boolean isHolding() { return holding; }
    public void setHolding(boolean holding) { this.holding = holding; }
    public Integer getDifference() {
        return countedQuantity == null ? null : Integer.valueOf(countedQuantity - snapshotOnHand);
    }
    public BigDecimal getDifferenceValue() {
        return getDifference() == null ? null : unitCost.multiply(BigDecimal.valueOf(getDifference()));
    }
    public Integer getReservationShortage() {
        return countedQuantity == null ? null : Integer.valueOf(Math.max(0, snapshotReserved - countedQuantity));
    }
    public String getCostBasis() { return "STANDARD_COST_AT_COUNT_BEGIN"; }
}
