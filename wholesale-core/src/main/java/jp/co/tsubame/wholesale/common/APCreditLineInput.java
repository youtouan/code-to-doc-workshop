package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;

public class APCreditLineInput {
    private Long invoiceLineId;
    private BigDecimal netAmount;

    public Long getInvoiceLineId() { return invoiceLineId; }
    public void setInvoiceLineId(Long value) { invoiceLineId = value; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal value) { netAmount = value; }
}
