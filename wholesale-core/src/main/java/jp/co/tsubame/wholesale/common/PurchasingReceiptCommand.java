package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PurchasingReceiptCommand {
    private String requestKey;
    private Long orderId;
    private int expectedVersion;
    private Date receiptDate;
    private String supplierDeliveryNumber = "";
    private String notes = "";
    private List<PurchasingReceiptLineCommand> lines = new ArrayList<PurchasingReceiptLineCommand>();

    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public int getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(int expectedVersion) { this.expectedVersion = expectedVersion; }
    public Date getReceiptDate() { return receiptDate; }
    public void setReceiptDate(Date receiptDate) { this.receiptDate = receiptDate; }
    public String getSupplierDeliveryNumber() { return supplierDeliveryNumber; }
    public void setSupplierDeliveryNumber(String supplierDeliveryNumber) { this.supplierDeliveryNumber = supplierDeliveryNumber; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<PurchasingReceiptLineCommand> getLines() { return lines; }
    public void setLines(List<PurchasingReceiptLineCommand> lines) { this.lines = lines; }
}
