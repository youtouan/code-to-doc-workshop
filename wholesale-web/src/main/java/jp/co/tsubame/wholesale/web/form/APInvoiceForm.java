package jp.co.tsubame.wholesale.web.form;

import jp.co.tsubame.wholesale.web.Inputs;

public class APInvoiceForm extends APForm {
    private static final long serialVersionUID = 1L;
    private String supplierInvoiceNumber = "";
    private String invoiceDate = "";
    private String dueDate = "";
    private String[] description = new String[0];
    private String[] unitPrice = new String[0];
    private String[] taxRate = new String[0];
    public String getSupplierInvoiceNumber() { return supplierInvoiceNumber; }
    public void setSupplierInvoiceNumber(String value) { supplierInvoiceNumber = value; }
    public String getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(String value) { invoiceDate = value; }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String value) { dueDate = value; }
    public String[] getDescription() { return description; }
    public void setDescription(String[] value) { description = value; }
    public String[] getUnitPrice() { return unitPrice; }
    public void setUnitPrice(String[] value) { unitPrice = value; }
    public String[] getTaxRate() { return taxRate; }
    public void setTaxRate(String[] value) { taxRate = value; }
    public void rows(int count) {
        if (count < 1 || count > 200) { throw Inputs.invalid("請求明細", "請求明細は200行以内です。"); }
        setProductId(grow(getProductId(), count)); setQuantity(grow(getQuantity(), count));
        description = grow(description, count); unitPrice = grow(unitPrice, count); taxRate = grow(taxRate, count);
    }
}
