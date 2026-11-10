package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class APPaymentInput {
    private String requestKey;
    private Long supplierId;
    private Date paymentDate;
    private BigDecimal amount;
    private String method;
    private String reference;
    private String notes;
    private List<APPaymentLineInput> lines = new ArrayList<APPaymentLineInput>();

    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long value) { supplierId = value; }
    public Date getPaymentDate() { return paymentDate; }
    public void setPaymentDate(Date value) { paymentDate = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getMethod() { return method; }
    public void setMethod(String value) { method = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public List<APPaymentLineInput> getLines() { return lines; }
    public void setLines(List<APPaymentLineInput> value) { lines = value; }
}
