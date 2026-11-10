package jp.co.tsubame.wholesale.web.form;

public class PaymentForm extends BaseForm {
    private static final long serialVersionUID = 1L;
    private String requestKey = "";
    private String receivedDate = "";
    private String amount = "";
    private String method = "BANK_TRANSFER";
    private String reference = "";
    private String notes = "";
    private String invoiceId = "";
    private String allocationId = "";
    private String allocationVersion = "0";
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getReceivedDate() { return receivedDate; }
    public void setReceivedDate(String value) { receivedDate = value; }
    public String getAmount() { return amount; }
    public void setAmount(String value) { amount = value; }
    public String getMethod() { return method; }
    public void setMethod(String value) { method = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String value) { invoiceId = value; }
    public String getAllocationId() { return allocationId; }
    public void setAllocationId(String value) { allocationId = value; }
    public String getAllocationVersion() { return allocationVersion; }
    public void setAllocationVersion(String value) { allocationVersion = value; }
}
