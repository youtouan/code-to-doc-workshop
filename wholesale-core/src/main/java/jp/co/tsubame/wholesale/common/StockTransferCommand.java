package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.List;

public class StockTransferCommand {
    private Long id;
    private int expectedVersion;
    private Long sourceWarehouseId;
    private Long destinationWarehouseId;
    private String note = "";
    private List<StockControlLineCommand> lines = new ArrayList<StockControlLineCommand>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(int expectedVersion) { this.expectedVersion = expectedVersion; }
    public Long getSourceWarehouseId() { return sourceWarehouseId; }
    public void setSourceWarehouseId(Long id) { sourceWarehouseId = id; }
    public Long getDestinationWarehouseId() { return destinationWarehouseId; }
    public void setDestinationWarehouseId(Long id) { destinationWarehouseId = id; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<StockControlLineCommand> getLines() { return lines; }
    public void setLines(List<StockControlLineCommand> lines) { this.lines = lines; }
}
