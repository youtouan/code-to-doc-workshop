package jp.co.tsubame.wholesale.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.Fingerprints;

public final class RowTask {
    private final String command;
    private final int number;
    private final long sourceLine;
    private final List<String> fields;
    private final Long entityId;
    private final Date date;
    private final boolean finalizeInvoice;
    private final String hash;
    private final OrderGroup orderGroup;

    public RowTask(String command, int number, long sourceLine, List<String> fields) {
        this(command, number, sourceLine, fields, null, null, false, null);
    }

    public RowTask(String command, int number, Long entityId, Date date, boolean finalizeInvoice) {
        this(command, number, number, Collections.<String>emptyList(), entityId, date, finalizeInvoice, null);
    }

    public RowTask(OrderGroup group) {
        this("import-orders", group.getNumber(), group.getFirstLine(), Collections.<String>emptyList(),
                null, null, false, group);
    }

    private RowTask(String command, int number, long sourceLine, List<String> fields,
                    Long entityId, Date date, boolean finalizeInvoice, OrderGroup orderGroup) {
        this.command = command;
        this.number = number;
        this.sourceLine = sourceLine;
        this.fields = Collections.unmodifiableList(new ArrayList<String>(fields));
        this.entityId = entityId;
        this.date = date == null ? null : new Date(date.getTime());
        this.finalizeInvoice = finalizeInvoice;
        this.orderGroup = orderGroup;
        List<String> payload = new ArrayList<String>(fields);
        payload.add(command);
        payload.add(entityId == null ? "" : entityId.toString());
        payload.add(date == null ? "" : Long.toString(date.getTime()));
        payload.add(Boolean.toString(finalizeInvoice));
        hash = orderGroup == null ? Fingerprints.of(payload.toArray(new String[payload.size()])) : orderGroup.getRawHash();
    }

    public String getCommand() { return command; }
    public int getNumber() { return number; }
    public long getSourceLine() { return sourceLine; }
    public List<String> getFields() { return fields; }
    public Long getEntityId() { return entityId; }
    public Date getDate() { return date == null ? null : new Date(date.getTime()); }
    public boolean isFinalizeInvoice() { return finalizeInvoice; }
    public String getHash() { return hash; }
    public OrderGroup getOrderGroup() { return orderGroup; }
    public String sourceDescription() { return orderGroup == null ? "" : orderGroup.describeSource() + "; "; }
}
