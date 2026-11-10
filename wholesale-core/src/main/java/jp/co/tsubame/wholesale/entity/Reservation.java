package jp.co.tsubame.wholesale.entity;

import java.util.Date;

public class Reservation extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private SalesOrderLine orderLine;
    private StockBalance balance;
    private int quantity;
    private Date updatedAt;

    public SalesOrderLine getOrderLine() {
        return orderLine;
    }

    public void setOrderLine(SalesOrderLine orderLine) {
        this.orderLine = orderLine;
    }

    public StockBalance getBalance() {
        return balance;
    }

    public void setBalance(StockBalance balance) {
        this.balance = balance;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
