package jp.co.tsubame.wholesale.batch.csv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CsvRecord {
    private final long line;
    private final List<String> fields;

    public CsvRecord(long line, List<String> fields) {
        this.line = line;
        this.fields = Collections.unmodifiableList(new ArrayList<String>(fields));
    }

    public long getLine() { return line; }
    public List<String> getFields() { return fields; }
}
