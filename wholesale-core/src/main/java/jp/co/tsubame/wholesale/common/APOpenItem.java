package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Date;

public class APOpenItem {
    private final Long invoiceId;
    private final String number;
    private final String supplierInvoiceNumber;
    private final String supplierName;
    private final Date invoiceDate;
    private final Date dueDate;
    private final BigDecimal totalAmount;
    private final BigDecimal paidAmount;
    private final BigDecimal creditedAmount;
    private final Date asOf;

    public APOpenItem(Long invoiceId, String number, String supplierInvoiceNumber, String supplierName,
            Date invoiceDate, Date dueDate, BigDecimal totalAmount, BigDecimal paidAmount,
            BigDecimal creditedAmount, Date asOf) {
        this.invoiceId = invoiceId;
        this.number = number;
        this.supplierInvoiceNumber = supplierInvoiceNumber;
        this.supplierName = supplierName;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.totalAmount = totalAmount;
        this.paidAmount = paidAmount;
        this.creditedAmount = creditedAmount;
        this.asOf = asOf;
    }
    public Long getInvoiceId() { return invoiceId; }
    public String getNumber() { return number; }
    public String getSupplierInvoiceNumber() { return supplierInvoiceNumber; }
    public String getSupplierName() { return supplierName; }
    public Date getInvoiceDate() { return invoiceDate; }
    public Date getDueDate() { return dueDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public BigDecimal getCreditedAmount() { return creditedAmount; }
    public BigDecimal getOutstandingAmount() { return totalAmount.subtract(paidAmount).subtract(creditedAmount); }
    public int getDaysOverdue() { return Math.max(0, (int) ((Dates.day(asOf).getTime() - Dates.day(dueDate).getTime()) / 86400000L)); }
}
