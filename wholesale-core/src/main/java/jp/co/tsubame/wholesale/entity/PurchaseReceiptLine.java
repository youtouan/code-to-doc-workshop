package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;

public class PurchaseReceiptLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private PurchaseReceipt receipt;
    private int lineNumber;
    private PurchaseOrderLine orderLine;
    private int acceptedQuantity;
    private int rejectedQuantity;
    private String rejectionReason = "";
    private BigDecimal unitCost;
    private BigDecimal acceptedAmount;
    private StockReceipt stockReceipt;
    private String notes = "";

    public PurchaseReceipt getReceipt() { return receipt; }
    public void setReceipt(PurchaseReceipt receipt) { this.receipt = receipt; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public PurchaseOrderLine getOrderLine() { return orderLine; }
    public void setOrderLine(PurchaseOrderLine orderLine) { this.orderLine = orderLine; }
    public int getAcceptedQuantity() { return acceptedQuantity; }
    public void setAcceptedQuantity(int acceptedQuantity) { this.acceptedQuantity = acceptedQuantity; }
    public int getRejectedQuantity() { return rejectedQuantity; }
    public void setRejectedQuantity(int rejectedQuantity) { this.rejectedQuantity = rejectedQuantity; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public BigDecimal getAcceptedAmount() { return acceptedAmount; }
    public void setAcceptedAmount(BigDecimal acceptedAmount) { this.acceptedAmount = acceptedAmount; }
    public StockReceipt getStockReceipt() { return stockReceipt; }
    public void setStockReceipt(StockReceipt stockReceipt) { this.stockReceipt = stockReceipt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
