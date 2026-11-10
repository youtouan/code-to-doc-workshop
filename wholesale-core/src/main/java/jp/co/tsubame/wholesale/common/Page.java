package jp.co.tsubame.wholesale.common;

import java.util.Collections;
import java.util.List;

public final class Page<T> {
    private final List<T> items;
    private final long total;
    private final int number;
    private final int size;

    public Page(List<T> items, long total, int number, int size) {
        this.items = Collections.unmodifiableList(items);
        this.total = total;
        this.number = number;
        this.size = size;
    }

    public List<T> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }

    public int getNumber() {
        return number;
    }

    public int getSize() {
        return size;
    }

    public int getPageCount() {
        return (int) Math.max(1L, (total + size - 1L) / size);
    }

    public boolean isHasPrevious() {
        return number > 1;
    }

    public boolean isHasNext() {
        return number < getPageCount();
    }
}
