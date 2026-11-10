package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Date;
import jp.co.tsubame.wholesale.entity.Supplier;

public class APStatement {
    private final Supplier supplier;
    private final Date from;
    private final Date to;
    private final BigDecimal openingBalance;
    private final BigDecimal charges;
    private final BigDecimal reductions;
    private final Page<APStatementEntry> entries;

    public APStatement(Supplier supplier, Date from, Date to, BigDecimal openingBalance,
            BigDecimal charges, BigDecimal reductions, Page<APStatementEntry> entries) {
        this.supplier = supplier;
        this.from = from;
        this.to = to;
        this.openingBalance = openingBalance;
        this.charges = charges;
        this.reductions = reductions;
        this.entries = entries;
    }
    public Supplier getSupplier() { return supplier; }
    public Date getFrom() { return from; }
    public Date getTo() { return to; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getCharges() { return charges; }
    public BigDecimal getReductions() { return reductions; }
    public BigDecimal getClosingBalance() { return openingBalance.add(charges).subtract(reductions); }
    public Page<APStatementEntry> getEntries() { return entries; }
}
