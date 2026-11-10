package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Date;

public class APStatementEntry {
    private final Date date;
    private final String type;
    private final Long documentId;
    private final String number;
    private final String reference;
    private final BigDecimal charge;
    private final BigDecimal reduction;
    private final BigDecimal balance;

    public APStatementEntry(Date date, String type, Long documentId, String number, String reference,
            BigDecimal charge, BigDecimal reduction, BigDecimal balance) {
        this.date = date;
        this.type = type;
        this.documentId = documentId;
        this.number = number;
        this.reference = reference;
        this.charge = charge;
        this.reduction = reduction;
        this.balance = balance;
    }
    public Date getDate() { return date; }
    public String getType() { return type; }
    public Long getDocumentId() { return documentId; }
    public String getNumber() { return number; }
    public String getReference() { return reference; }
    public BigDecimal getCharge() { return charge; }
    public BigDecimal getReduction() { return reduction; }
    public BigDecimal getBalance() { return balance; }
}
