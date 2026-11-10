package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;

public class APPaymentLineInput {
    private Long invoiceId;
    private BigDecimal amount;

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long value) { invoiceId = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
}
