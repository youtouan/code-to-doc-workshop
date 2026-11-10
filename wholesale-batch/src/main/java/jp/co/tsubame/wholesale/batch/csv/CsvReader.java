package jp.co.tsubame.wholesale.batch.csv;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Streaming RFC 4180 records; LF files are accepted, bare CR outside quotes is not. */
public final class CsvReader implements Closeable {
    public static final int MAX_FIELD_CHARS = 65536;
    public static final int MAX_RECORD_CHARS = 1048576;
    public static final int MAX_COLUMNS = 256;
    private final Reader reader;
    private final int fieldLimit;
    private final int recordLimit;
    private long line = 1;
    private boolean first = true;
    private boolean eof;

    public CsvReader(InputStream input) {
        this(input, MAX_FIELD_CHARS, MAX_RECORD_CHARS);
    }

    public CsvReader(InputStream input, int fieldLimit, int recordLimit) {
        if (fieldLimit < 1 || recordLimit < fieldLimit) {
            throw new IllegalArgumentException("Invalid CSV limits");
        }
        this.fieldLimit = fieldLimit;
        this.recordLimit = recordLimit;
        // Buffer bytes, not characters: decoder errors must not jump ahead of physical-line tracking.
        reader = new InputStreamReader(new BufferedInputStream(input),
                Charset.forName("UTF-8").newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT));
    }

    public CsvRecord read() throws IOException {
        if (eof) { return null; }
        long start = line;
        int count = 0;
        boolean quoted = false;
        boolean closed = false;
        boolean started = false;
        List<String> fields = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        for (;;) {
            int ch = next();
            if (first) {
                first = false;
                if (ch == 0xfeff) { ch = next(); }
            }
            if (ch == -1) {
                eof = true;
                if (quoted) { throw error(start, "unterminated quoted field (EOF at line " + line + ")"); }
                if (!started && fields.isEmpty() && field.length() == 0) { return null; }
                add(fields, field, start);
                return new CsvRecord(start, fields);
            }
            started = true;
            if (++count > recordLimit) { throw error(start, "record exceeds " + recordLimit + " characters"); }
            if (ch == 0) { throw error(line, "NUL is not permitted"); }
            if (quoted) {
                if (ch == '"') {
                    quoted = false;
                    closed = true;
                } else {
                    append(field, ch, start);
                    if (ch == '\n') { line++; }
                }
            } else if (closed && ch == '"') {
                append(field, ch, start);
                quoted = true;
                closed = false;
            } else if (ch == ',') {
                add(fields, field, start);
                field.setLength(0);
                closed = false;
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r') {
                    if (next() != '\n') { throw error(line, "bare CR record separator; use CRLF or LF"); }
                }
                line++;
                add(fields, field, start);
                return new CsvRecord(start, fields);
            } else if (closed) {
                throw error(line, "unexpected character after closing quote");
            } else if (ch == '"') {
                if (field.length() != 0) { throw error(line, "quote inside an unquoted field"); }
                quoted = true;
            } else {
                append(field, ch, start);
            }
        }
    }

    public void requireHeader(String[] expected) throws IOException {
        CsvRecord header = read();
        if (header == null) { throw error(1, "missing header"); }
        if (!header.getFields().equals(Arrays.asList(expected))) {
            throw error(header.getLine(), "header must exactly match: " + join(expected));
        }
    }

    private int next() throws IOException {
        try {
            return reader.read();
        } catch (CharacterCodingException ex) {
            CsvException failure = error(line, "malformed UTF-8 near this physical line");
            failure.initCause(ex);
            throw failure;
        }
    }

    private void append(StringBuilder field, int ch, long start) throws IOException {
        if (field.length() >= fieldLimit) { throw error(start, "field exceeds " + fieldLimit + " characters"); }
        field.append((char) ch);
    }

    private void add(List<String> fields, StringBuilder field, long start) throws IOException {
        if (fields.size() >= MAX_COLUMNS) { throw error(start, "too many columns (maximum " + MAX_COLUMNS + ")"); }
        fields.add(field.toString());
    }

    private static String join(String[] values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() != 0) { result.append(','); }
            result.append(value);
        }
        return result.toString();
    }

    private static CsvException error(long line, String message) { return new CsvException(line, message); }
    public void close() throws IOException { reader.close(); }
}
