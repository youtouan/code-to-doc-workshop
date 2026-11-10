package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.Date;

public class APPaymentLine extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private APPaymentVoucher voucher;
    private APInvoice invoice;
    private BigDecimal amount;
    private String status = "ACTIVE";
    private Date reversalDate;

    public APPaymentVoucher getVoucher() { return voucher; }
    public void setVoucher(APPaymentVoucher value) { voucher = value; }
    public APInvoice getInvoice() { return invoice; }
    public void setInvoice(APInvoice value) { invoice = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Date getReversalDate() { return reversalDate; }
    public void setReversalDate(Date value) { reversalDate = value; }
}
