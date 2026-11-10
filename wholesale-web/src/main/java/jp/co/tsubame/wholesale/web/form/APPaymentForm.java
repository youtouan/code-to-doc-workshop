package jp.co.tsubame.wholesale.web.form;

import jp.co.tsubame.wholesale.web.Inputs;

public class APPaymentForm extends APForm {
    private static final long serialVersionUID = 1L;
    private String paymentDate = "";
    private String amount = "";
    private String method = "BANK_TRANSFER";
    private String reference = "";
    private String[] invoiceId = new String[0];
    private String[] allocationAmount = new String[0];
    public String getPaymentDate() { return paymentDate; }
    public void setPaymentDate(String value) { paymentDate = value; }
    public String getAmount() { return amount; }
    public void setAmount(String value) { amount = value; }
    public String getMethod() { return method; }
    public void setMethod(String value) { method = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String[] getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String[] value) { invoiceId = value; }
    public String[] getAllocationAmount() { return allocationAmount; }
    public void setAllocationAmount(String[] value) { allocationAmount = value; }
    public void rows(int count) {
        if (count < 1 || count > 200) { throw Inputs.invalid("支払明細", "支払明細は200行以内です。"); }
        invoiceId = grow(invoiceId, count); allocationAmount = grow(allocationAmount, count);
    }
}
