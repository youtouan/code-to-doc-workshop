package jp.co.tsubame.wholesale.batch.csv;

import java.io.IOException;

public class CsvException extends IOException {
    private static final long serialVersionUID = 1L;
    private final long line;

    public CsvException(long line, String message) {
        super("CSV line " + line + ": " + message);
        this.line = line;
    }

    public long getLine() { return line; }
}
