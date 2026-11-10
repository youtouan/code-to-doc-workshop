package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.entity.Customer;

public class AccountStatement {
    private final Customer customer;
    private final Date from;
    private final Date to;
    private final BigDecimal openingBalance;
    private BigDecimal debitTotal = Money.ZERO;
    private BigDecimal creditTotal = Money.ZERO;
    private BigDecimal closingBalance;
    private final List<AccountStatementLine> lines = new ArrayList<AccountStatementLine>();

    public AccountStatement(Customer customer, Date from, Date to, BigDecimal openingBalance) {
        this.customer = customer;
        this.from = from;
        this.to = to;
        this.openingBalance = openingBalance;
        this.closingBalance = openingBalance;
    }

    public void add(AccountStatementLine line) {
        debitTotal = debitTotal.add(line.getDebit());
        creditTotal = creditTotal.add(line.getCredit());
        closingBalance = closingBalance.add(line.getDebit()).subtract(line.getCredit());
        line.setBalance(closingBalance);
        lines.add(line);
    }

    public Customer getCustomer() { return customer; }
    public Date getFrom() { return from; }
    public Date getTo() { return to; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getDebitTotal() { return debitTotal; }
    public BigDecimal getCreditTotal() { return creditTotal; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public List<AccountStatementLine> getLines() { return Collections.unmodifiableList(lines); }
}
