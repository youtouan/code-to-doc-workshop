package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Date;

public class AccountStatementLine {
    private final Date date;
    private final String type;
    private final Long documentId;
    private final String documentNumber;
    private final String description;
    private final BigDecimal debit;
    private final BigDecimal credit;
    private BigDecimal balance;

    public AccountStatementLine(Date date, String type, Long documentId, String documentNumber,
            String description, BigDecimal debit, BigDecimal credit) {
        this.date = Dates.day(date);
        this.type = type;
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.description = description;
        this.debit = debit;
        this.credit = credit;
    }

    public Date getDate() { return date; }
    public String getType() { return type; }
    public Long getDocumentId() { return documentId; }
    public String getDocumentNumber() { return documentNumber; }
    public String getDescription() { return description; }
    public BigDecimal getDebit() { return debit; }
    public BigDecimal getCredit() { return credit; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
