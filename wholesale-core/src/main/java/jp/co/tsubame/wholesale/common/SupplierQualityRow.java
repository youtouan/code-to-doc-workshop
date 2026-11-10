package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SupplierQualityRow {
    private final Long supplierId;
    private final String supplierCode;
    private final String supplierName;
    private final long receiptCount;
    private final long acceptedQuantity;
    private final long rejectedQuantity;
    private final long lateAcceptedQuantity;
    private final BigDecimal acceptedAmount;

    public SupplierQualityRow(Long supplierId, String supplierCode, String supplierName, long receiptCount,
                              long acceptedQuantity, long rejectedQuantity, long lateAcceptedQuantity,
                              BigDecimal acceptedAmount) {
        this.supplierId = supplierId;
        this.supplierCode = supplierCode;
        this.supplierName = supplierName;
        this.receiptCount = receiptCount;
        this.acceptedQuantity = acceptedQuantity;
        this.rejectedQuantity = rejectedQuantity;
        this.lateAcceptedQuantity = lateAcceptedQuantity;
        this.acceptedAmount = acceptedAmount;
    }

    public Long getSupplierId() { return supplierId; }
    public String getSupplierCode() { return supplierCode; }
    public String getSupplierName() { return supplierName; }
    public long getReceiptCount() { return receiptCount; }
    public long getAcceptedQuantity() { return acceptedQuantity; }
    public long getRejectedQuantity() { return rejectedQuantity; }
    public long getLateAcceptedQuantity() { return lateAcceptedQuantity; }
    public BigDecimal getAcceptedAmount() { return acceptedAmount; }

    public BigDecimal getRejectionPercent() {
        long denominator = acceptedQuantity + rejectedQuantity;
        return denominator == 0 ? Money.ZERO : BigDecimal.valueOf(rejectedQuantity)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getLateAcceptedPercent() {
        return acceptedQuantity == 0 ? Money.ZERO : BigDecimal.valueOf(lateAcceptedQuantity)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(acceptedQuantity), 2, RoundingMode.HALF_UP);
    }
}
