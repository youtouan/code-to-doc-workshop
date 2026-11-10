package jp.co.tsubame.wholesale.common;

import java.util.Date;

public class Search {
    private String text = "";
    private String status = "";
    private Long customerId;
    private Long warehouseId;
    private Date from;
    private Date to;
    private int page = 1;
    private int size = 25;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : Checks.optionalText(text, "検索語", 100);
    }

    public String getLikeText() {
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? "" : status;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Date getFrom() {
        return from;
    }

    public void setFrom(Date from) {
        this.from = Dates.day(from);
    }

    public Date getTo() {
        return to;
    }

    public void setTo(Date to) {
        this.to = Dates.day(to);
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        Checks.state(page >= 1 && page <= 100000, "validation.page", "ページ番号が不正です。");
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        Checks.state(size >= 1 && size <= 100, "validation.pageSize", "表示件数は1から100です。");
        this.size = size;
    }

    public int getOffset() {
        return (page - 1) * size;
    }
}
