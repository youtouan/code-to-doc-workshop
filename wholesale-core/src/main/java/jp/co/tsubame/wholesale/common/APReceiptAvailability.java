package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Date;

public class APReceiptAvailability {
    private final Long receiptLineId;
    private final String receiptNumber;
    private final String purchaseOrderNumber;
    private final Long productId;
    private final String productCode;
    private final String productName;
    private final Date receiptDate;
    private final int acceptedQuantity;
    private final int rejectedQuantity;
    private final long matchedQuantity;
    private final BigDecimal unitCost;

    public APReceiptAvailability(Long receiptLineId, String receiptNumber, String purchaseOrderNumber,
            Long productId, String productCode, String productName, Date receiptDate, int acceptedQuantity,
            int rejectedQuantity, long matchedQuantity, BigDecimal unitCost) {
        this.receiptLineId = receiptLineId;
        this.receiptNumber = receiptNumber;
        this.purchaseOrderNumber = purchaseOrderNumber;
        this.productId = productId;
        this.productCode = productCode;
        this.productName = productName;
        this.receiptDate = receiptDate;
        this.acceptedQuantity = acceptedQuantity;
        this.rejectedQuantity = rejectedQuantity;
        this.matchedQuantity = matchedQuantity;
        this.unitCost = unitCost;
    }
    public Long getReceiptLineId() { return receiptLineId; }
    public String getReceiptNumber() { return receiptNumber; }
    public String getPurchaseOrderNumber() { return purchaseOrderNumber; }
    public Long getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public Date getReceiptDate() { return receiptDate; }
    public int getAcceptedQuantity() { return acceptedQuantity; }
    public int getRejectedQuantity() { return rejectedQuantity; }
    public long getMatchedQuantity() { return matchedQuantity; }
    public long getAvailableQuantity() { return acceptedQuantity - matchedQuantity; }
    public BigDecimal getUnitCost() { return unitCost; }
}
