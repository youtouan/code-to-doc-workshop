package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Date;

/** Cash movement, not an invoice allocation; a cancellation is a separate outflow. */
public class BillingCashEntry {
    private final Long receiptId;
    private final String receiptNumber;
    private final Long customerId;
    private final String customerName;
    private final Date date;
    private final String type;
    private final String method;
    private final String reference;
    private final BigDecimal inflow;
    private final BigDecimal outflow;
    private final String actor;
    private final String reason;

    public BillingCashEntry(Long receiptId, String receiptNumber, Long customerId, String customerName,
            Date date, String type, String method, String reference, BigDecimal inflow,
            BigDecimal outflow, String actor, String reason) {
        this.receiptId = receiptId;
        this.receiptNumber = receiptNumber;
        this.customerId = customerId;
        this.customerName = customerName;
        this.date = date;
        this.type = type;
        this.method = method;
        this.reference = reference;
        this.inflow = inflow;
        this.outflow = outflow;
        this.actor = actor;
        this.reason = reason;
    }

    public Long getReceiptId() { return receiptId; }
    public String getReceiptNumber() { return receiptNumber; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public Date getDate() { return date; }
    public String getType() { return type; }
    public String getMethod() { return method; }
    public String getReference() { return reference; }
    public BigDecimal getInflow() { return inflow; }
    public BigDecimal getOutflow() { return outflow; }
    public BigDecimal getNetAmount() { return inflow.subtract(outflow); }
    public String getActor() { return actor; }
    public String getReason() { return reason; }
}
