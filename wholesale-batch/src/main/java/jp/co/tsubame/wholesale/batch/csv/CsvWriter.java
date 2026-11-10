package jp.co.tsubame.wholesale.batch.csv;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.List;

public final class CsvWriter implements Closeable {
    private final Writer writer;

    public CsvWriter(Writer writer) { this.writer = writer; }

    /** Numbers remain numeric. Text is escaped for spreadsheets as well as CSV syntax. */
    public void write(List<?> fields) throws IOException {
        boolean first = true;
        for (Object value : fields) {
            if (!first) { writer.write(','); }
            first = false;
            String text;
            if (value == null) { text = ""; }
            else if (value instanceof BigDecimal) { text = ((BigDecimal) value).toPlainString(); }
            else { text = value.toString(); }
            if (!(value instanceof Number)) { text = spreadsheetSafe(text); }
            boolean quote = text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                    || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
            if (quote) { writer.write('"'); }
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '"') { writer.write('"'); }
                writer.write(ch);
            }
            if (quote) { writer.write('"'); }
        }
        writer.write("\r\n");
    }

    public static String spreadsheetSafe(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\t' || ch == '\r' || ch == '\n') { return "'" + text; }
            if (Character.isWhitespace(ch) || ch == 0xfeff) { continue; }
            return ch == '=' || ch == '+' || ch == '-' || ch == '@' ? "'" + text : text;
        }
        return text;
    }

    public void flush() throws IOException { writer.flush(); }
    public void close() throws IOException { writer.close(); }
}
