package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardSnapshot {
    private final Map<String, Long> counts = new LinkedHashMap<String, Long>();
    private final List<WorkItem> orders = new ArrayList<WorkItem>();
    private final List<WorkItem> shipments = new ArrayList<WorkItem>();
    private final Date asOf = new Date();

    public void addCount(String key, long value) {
        counts.put(key, value);
    }

    public void addOrder(WorkItem item) {
        orders.add(item);
    }

    public void addShipment(WorkItem item) {
        shipments.add(item);
    }

    public Map<String, Long> getCounts() {
        return Collections.unmodifiableMap(counts);
    }

    public List<WorkItem> getOrders() {
        return Collections.unmodifiableList(orders);
    }

    public List<WorkItem> getShipments() {
        return Collections.unmodifiableList(shipments);
    }

    public Date getAsOf() {
        return asOf;
    }
}
