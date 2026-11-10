package jp.co.tsubame.wholesale.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StockTransferReceipt extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private StockTransfer transfer;
    private String number;
    private String kind;
    private String requestKey;
    private String fingerprint;
    private String note;
    private String createdBy;
    private Date createdAt;
    private List<StockTransferReceiptLine> lines = new ArrayList<StockTransferReceiptLine>();

    public StockTransfer getTransfer() { return transfer; }
    public void setTransfer(StockTransfer value) { transfer = value; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String value) { fingerprint = value; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date value) { createdAt = value; }
    public List<StockTransferReceiptLine> getLines() { return lines; }
    public void setLines(List<StockTransferReceiptLine> value) { lines = value; }
    public BigDecimal getValue() {
        BigDecimal value = new BigDecimal("0.00");
        for (StockTransferReceiptLine line : lines) { value = value.add(line.getValue()); }
        return value;
    }
}
