package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PurchaseReceipt extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private String number;
    private String requestKey;
    private String payloadHash;
    private PurchaseOrder order;
    private Date receiptDate;
    private String supplierDeliveryNumber = "";
    private String receivedBy;
    private Date receivedAt;
    private BigDecimal acceptedAmount = new BigDecimal("0.00");
    private String notes = "";
    private List<PurchaseReceiptLine> lines = new ArrayList<PurchaseReceiptLine>();

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public PurchaseOrder getOrder() { return order; }
    public void setOrder(PurchaseOrder order) { this.order = order; }
    public Date getReceiptDate() { return receiptDate; }
    public void setReceiptDate(Date receiptDate) { this.receiptDate = receiptDate; }
    public String getSupplierDeliveryNumber() { return supplierDeliveryNumber; }
    public void setSupplierDeliveryNumber(String supplierDeliveryNumber) { this.supplierDeliveryNumber = supplierDeliveryNumber; }
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
    public Date getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Date receivedAt) { this.receivedAt = receivedAt; }
    public BigDecimal getAcceptedAmount() { return acceptedAmount; }
    public void setAcceptedAmount(BigDecimal acceptedAmount) { this.acceptedAmount = acceptedAmount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<PurchaseReceiptLine> getLines() { return lines; }
    public void setLines(List<PurchaseReceiptLine> lines) { this.lines = lines; }
    public int getAcceptedQuantity() {
        int result = 0;
        for (PurchaseReceiptLine line : lines) { result += line.getAcceptedQuantity(); }
        return result;
    }
    public int getRejectedQuantity() {
        int result = 0;
        for (PurchaseReceiptLine line : lines) { result += line.getRejectedQuantity(); }
        return result;
    }
}
