package jp.co.tsubame.wholesale.web.form;

public class APCreditForm extends APForm {
    private static final long serialVersionUID = 1L;
    private String invoiceId = "";
    private String supplierCreditNumber = "";
    private String creditDate = "";
    private String[] invoiceLineId = new String[0];
    private String[] netAmount = new String[0];
    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String value) { invoiceId = value; }
    public String getSupplierCreditNumber() { return supplierCreditNumber; }
    public void setSupplierCreditNumber(String value) { supplierCreditNumber = value; }
    public String getCreditDate() { return creditDate; }
    public void setCreditDate(String value) { creditDate = value; }
    public String[] getInvoiceLineId() { return invoiceLineId; }
    public void setInvoiceLineId(String[] value) { invoiceLineId = value; }
    public String[] getNetAmount() { return netAmount; }
    public void setNetAmount(String[] value) { netAmount = value; }
}
