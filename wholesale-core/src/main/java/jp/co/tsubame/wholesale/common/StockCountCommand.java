package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.List;

public class StockCountCommand {
    private Long warehouseId;
    private List<Long> productIds = new ArrayList<Long>();
    private String note = "";

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long id) { warehouseId = id; }
    public List<Long> getProductIds() { return productIds; }
    public void setProductIds(List<Long> ids) { productIds = ids; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
