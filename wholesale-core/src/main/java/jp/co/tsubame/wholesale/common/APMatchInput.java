package jp.co.tsubame.wholesale.common;

public class APMatchInput {
    private Long invoiceLineId;
    private Long receiptLineId;
    private int quantity;

    public Long getInvoiceLineId() { return invoiceLineId; }
    public void setInvoiceLineId(Long value) { invoiceLineId = value; }
    public Long getReceiptLineId() { return receiptLineId; }
    public void setReceiptLineId(Long value) { receiptLineId = value; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }
}
