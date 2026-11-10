package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class APInvoiceInput {
    private Long supplierId;
    private String supplierInvoiceNumber;
    private Date invoiceDate;
    private Date dueDate;
    private String notes;
    private List<APInvoiceLineInput> lines = new ArrayList<APInvoiceLineInput>();

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long value) { supplierId = value; }
    public String getSupplierInvoiceNumber() { return supplierInvoiceNumber; }
    public void setSupplierInvoiceNumber(String value) { supplierInvoiceNumber = value; }
    public Date getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(Date value) { invoiceDate = value; }
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date value) { dueDate = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public List<APInvoiceLineInput> getLines() { return lines; }
    public void setLines(List<APInvoiceLineInput> value) { lines = value; }
}
