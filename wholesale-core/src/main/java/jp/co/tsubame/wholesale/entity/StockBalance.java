package jp.co.tsubame.wholesale.entity;

import java.util.Date;

public class StockBalance extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Warehouse warehouse;
    private Product product;
    private int onHand;
    private int reserved;
    private boolean blocked;
    private Date lastMovementAt;

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getOnHand() {
        return onHand;
    }

    public void setOnHand(int onHand) {
        this.onHand = onHand;
    }

    public int getReserved() {
        return reserved;
    }

    public void setReserved(int reserved) {
        this.reserved = reserved;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public Date getLastMovementAt() {
        return lastMovementAt;
    }

    public void setLastMovementAt(Date lastMovementAt) {
        this.lastMovementAt = lastMovementAt;
    }

    public int getAvailable() {
        return blocked ? 0 : onHand - reserved;
    }
}
