package jp.co.tsubame.wholesale.batch;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import jp.co.tsubame.wholesale.batch.csv.CsvException;
import jp.co.tsubame.wholesale.batch.csv.CsvReader;
import jp.co.tsubame.wholesale.batch.csv.CsvRecord;
import jp.co.tsubame.wholesale.batch.csv.InputSnapshot;

public final class OrderCsv implements Closeable {
    public static final int MAX_GROUPS = 100000;
    private final CsvReader reader;
    private CsvRecord pending;
    private int row;
    private int group;

    public OrderCsv(CsvReader reader) throws IOException {
        this.reader = reader;
        try { reader.requireHeader(ImportFields.ORDERS); }
        catch (IOException ex) { reader.close(); throw ex; }
    }

    /** Preflight the immutable file before any group commits, including later fragmented keys. */
    public static Set<String> repeatedKeys(InputSnapshot input) throws IOException {
        Set<String> seen = new HashSet<String>();
        Set<String> repeated = new HashSet<String>();
        try (OrderCsv groups = new OrderCsv(input.reader())) {
            OrderGroup group;
            while ((group = groups.read()) != null) {
                if (!seen.add(group.keyToken())) { repeated.add(group.keyToken()); }
            }
        }
        return repeated;
    }

    public OrderGroup read() throws IOException {
        CsvRecord first = pending == null ? reader.read() : pending;
        pending = null;
        if (first == null) { return null; }
        if (++group > MAX_GROUPS) { throw new CsvException(first.getLine(), "Order group limit exceeded: " + MAX_GROUPS); }
        OrderGroup result = new OrderGroup(group, row + 1, first);
        CsvRecord next = first;
        do {
            if (++row > BatchOrchestrator.MAX_DATA_ROWS) {
                throw new CsvException(next.getLine(), "Data row limit exceeded: " + BatchOrchestrator.MAX_DATA_ROWS);
            }
            result.add(row, next);
            next = reader.read();
        } while (next != null && result.getKey().equals(OrderGroup.key(next)));
        pending = next;
        result.seal();
        return result;
    }

    public void close() throws IOException { reader.close(); }
}
