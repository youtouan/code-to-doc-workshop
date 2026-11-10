package jp.co.tsubame.wholesale.common;

public class PurchasingReceiptLineCommand {
    private Long orderLineId;
    private int acceptedQuantity;
    private int rejectedQuantity;
    private String rejectionReason = "";
    private String notes = "";

    public Long getOrderLineId() { return orderLineId; }
    public void setOrderLineId(Long orderLineId) { this.orderLineId = orderLineId; }
    public int getAcceptedQuantity() { return acceptedQuantity; }
    public void setAcceptedQuantity(int acceptedQuantity) { this.acceptedQuantity = acceptedQuantity; }
    public int getRejectedQuantity() { return rejectedQuantity; }
    public void setRejectedQuantity(int rejectedQuantity) { this.rejectedQuantity = rejectedQuantity; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
