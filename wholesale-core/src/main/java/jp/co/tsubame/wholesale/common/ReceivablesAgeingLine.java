package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Date;
import jp.co.tsubame.wholesale.entity.Invoice;

public class ReceivablesAgeingLine {
    private final Long invoiceId;
    private final String invoiceNumber;
    private final Date dueDate;
    private final BigDecimal outstandingAmount;
    private final int daysOverdue;

    public ReceivablesAgeingLine(Invoice invoice, Date asOf, BigDecimal outstandingAmount) {
        this.invoiceId = invoice.getId();
        this.invoiceNumber = invoice.getNumber();
        this.dueDate = invoice.getDueDate();
        this.outstandingAmount = outstandingAmount;
        this.daysOverdue = Math.max(0, (int) ((Dates.day(asOf).getTime()
                - Dates.day(dueDate).getTime()) / 86400000L));
    }

    public Long getInvoiceId() { return invoiceId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public Date getDueDate() { return dueDate; }
    public BigDecimal getOutstandingAmount() { return outstandingAmount; }
    public int getDaysOverdue() { return daysOverdue; }
}
