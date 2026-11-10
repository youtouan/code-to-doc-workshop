package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.entity.Customer;

public class ReceivablesAgeing {
    private final Customer customer;
    private final Date asOf;
    private BigDecimal current = Money.ZERO;
    private BigDecimal days1To30 = Money.ZERO;
    private BigDecimal days31To60 = Money.ZERO;
    private BigDecimal days61To90 = Money.ZERO;
    private BigDecimal daysOver90 = Money.ZERO;
    private BigDecimal unallocatedReceipts = Money.ZERO;
    private BigDecimal unappliedCredits = Money.ZERO;
    private final List<ReceivablesAgeingLine> lines = new ArrayList<ReceivablesAgeingLine>();

    public ReceivablesAgeing(Customer customer, Date asOf) {
        this.customer = customer;
        this.asOf = asOf;
    }

    public void add(ReceivablesAgeingLine line) {
        int days = line.getDaysOverdue();
        BigDecimal amount = line.getOutstandingAmount();
        if (days <= 0) {
            current = current.add(amount);
        } else if (days <= 30) {
            days1To30 = days1To30.add(amount);
        } else if (days <= 60) {
            days31To60 = days31To60.add(amount);
        } else if (days <= 90) {
            days61To90 = days61To90.add(amount);
        } else {
            daysOver90 = daysOver90.add(amount);
        }
        lines.add(line);
    }

    public Customer getCustomer() { return customer; }
    public Date getAsOf() { return asOf; }
    public BigDecimal getCurrent() { return current; }
    public BigDecimal getDays1To30() { return days1To30; }
    public BigDecimal getDays31To60() { return days31To60; }
    public BigDecimal getDays61To90() { return days61To90; }
    public BigDecimal getDaysOver90() { return daysOver90; }
    public BigDecimal getTotalOutstanding() { return current.add(days1To30).add(days31To60).add(days61To90).add(daysOver90); }
    public BigDecimal getUnallocatedReceipts() { return unallocatedReceipts; }
    public void setUnallocatedReceipts(BigDecimal value) { unallocatedReceipts = value; }
    public BigDecimal getUnappliedCredits() { return unappliedCredits; }
    public void setUnappliedCredits(BigDecimal value) { unappliedCredits = value; }
    public BigDecimal getNetBalance() { return getTotalOutstanding().subtract(unallocatedReceipts).subtract(unappliedCredits); }
    public List<ReceivablesAgeingLine> getLines() { return Collections.unmodifiableList(lines); }
}
