package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.List;

public class StockTransferReceiptCommand {
    private String requestKey;
    private String note = "";
    private List<StockControlLineCommand> lines = new ArrayList<StockControlLineCommand>();

    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String key) { requestKey = key; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<StockControlLineCommand> getLines() { return lines; }
    public void setLines(List<StockControlLineCommand> lines) { this.lines = lines; }
}
