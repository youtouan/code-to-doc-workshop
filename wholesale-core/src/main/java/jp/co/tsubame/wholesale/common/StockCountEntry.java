package jp.co.tsubame.wholesale.common;

public class StockCountEntry {
    private Long lineId;
    private Integer countedQuantity;
    private String note = "";

    public Long getLineId() { return lineId; }
    public void setLineId(Long id) { lineId = id; }
    public Integer getCountedQuantity() { return countedQuantity; }
    public void setCountedQuantity(Integer value) { countedQuantity = value; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
