package jp.co.tsubame.wholesale.web.form;

import jp.co.tsubame.wholesale.web.Inputs;

public class APMatchForm extends APForm {
    private static final long serialVersionUID = 1L;
    private String[] invoiceLineId = new String[0];
    private String[] receiptLineId = new String[0];
    public String[] getInvoiceLineId() { return invoiceLineId; }
    public void setInvoiceLineId(String[] value) { invoiceLineId = value; }
    public String[] getReceiptLineId() { return receiptLineId; }
    public void setReceiptLineId(String[] value) { receiptLineId = value; }
    public void rows(int count) {
        if (count < 1 || count > 500) { throw Inputs.invalid("照合明細", "照合明細は500行以内です。"); }
        invoiceLineId = grow(invoiceLineId, count); receiptLineId = grow(receiptLineId, count);
        setQuantity(grow(getQuantity(), count));
    }
}
