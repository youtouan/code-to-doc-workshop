package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.Date;
import jp.co.tsubame.wholesale.common.Money;

public class APMatch extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private APInvoiceLine invoiceLine;
    private PurchaseReceiptLine receiptLine;
    private int quantity;
    private String receiptNumber;
    private String purchaseOrderNumber;
    private Date receiptDate;
    private BigDecimal receiptUnitCost;
    private BigDecimal orderedUnitCost;

    public APInvoiceLine getInvoiceLine() { return invoiceLine; }
    public void setInvoiceLine(APInvoiceLine value) { invoiceLine = value; }
    public PurchaseReceiptLine getReceiptLine() { return receiptLine; }
    public void setReceiptLine(PurchaseReceiptLine value) { receiptLine = value; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String value) { receiptNumber = value; }
    public String getPurchaseOrderNumber() { return purchaseOrderNumber; }
    public void setPurchaseOrderNumber(String value) { purchaseOrderNumber = value; }
    public Date getReceiptDate() { return receiptDate; }
    public void setReceiptDate(Date value) { receiptDate = value; }
    public BigDecimal getReceiptUnitCost() { return receiptUnitCost; }
    public void setReceiptUnitCost(BigDecimal value) { receiptUnitCost = value; }
    public BigDecimal getOrderedUnitCost() { return orderedUnitCost; }
    public void setOrderedUnitCost(BigDecimal value) { orderedUnitCost = value; }
    public BigDecimal getVarianceAmount() { return Money.amount(invoiceLine.getUnitPrice().subtract(receiptUnitCost), quantity); }
    public BigDecimal getPurchaseVarianceAmount() { return Money.amount(receiptUnitCost.subtract(orderedUnitCost), quantity); }
    public boolean isVariance() { return getVarianceAmount().signum() != 0 || getPurchaseVarianceAmount().signum() != 0; }
}
