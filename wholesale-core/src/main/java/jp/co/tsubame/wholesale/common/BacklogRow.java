package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Date;

public final class BacklogRow {
    private final Long orderId;
    private final String orderNumber;
    private final int lineNumber;
    private final String customerCode;
    private final String customerName;
    private final String warehouseCode;
    private final String productCode;
    private final String productName;
    private final Date requestedDate;
    private final String status;
    private final int openQuantity;
    private final int allocatedQuantity;
    private final BigDecimal unitPrice;
    private final boolean customerOnHold;
    private final boolean masterInactive;

    public BacklogRow(Long orderId, String orderNumber, int lineNumber, String customerCode,
                      String customerName, String warehouseCode, String productCode, String productName,
                      Date requestedDate, String status, int openQuantity, int allocatedQuantity,
                      BigDecimal unitPrice, boolean customerOnHold, boolean masterInactive) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.lineNumber = lineNumber;
        this.customerCode = customerCode;
        this.customerName = customerName;
        this.warehouseCode = warehouseCode;
        this.productCode = productCode;
        this.productName = productName;
        this.requestedDate = requestedDate;
        this.status = status;
        this.openQuantity = openQuantity;
        this.allocatedQuantity = allocatedQuantity;
        this.unitPrice = unitPrice;
        this.customerOnHold = customerOnHold;
        this.masterInactive = masterInactive;
    }

    public Long getOrderId() { return orderId; }
    public String getOrderNumber() { return orderNumber; }
    public int getLineNumber() { return lineNumber; }
    public String getCustomerCode() { return customerCode; }
    public String getCustomerName() { return customerName; }
    public String getWarehouseCode() { return warehouseCode; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public Date getRequestedDate() { return requestedDate; }
    public String getStatus() { return status; }
    public int getOpenQuantity() { return openQuantity; }
    public int getAllocatedQuantity() { return allocatedQuantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public boolean isCustomerOnHold() { return customerOnHold; }
    public boolean isMasterInactive() { return masterInactive; }

    public int getShortageQuantity() {
        return openQuantity - allocatedQuantity;
    }

    public BigDecimal getOpenAmount() {
        return Money.amount(unitPrice, openQuantity);
    }

    public int getDaysLate() {
        long days = (Dates.today().getTime() - Dates.day(requestedDate).getTime()) / 86400000L;
        return (int) Math.max(0L, days);
    }

    public boolean isException() {
        return customerOnHold || masterInactive || getDaysLate() > 0 || getShortageQuantity() > 0;
    }
}
