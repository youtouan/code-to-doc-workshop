package jp.co.tsubame.wholesale.batch;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jp.co.tsubame.wholesale.batch.csv.CsvRecord;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Fingerprints;

/** One bounded consecutive group. Invalid oversized groups are consumed, never fragmented. */
public final class OrderGroup {
    public static final int MAX_LINES = 200;
    public static final int MAX_CHARS = 2097152;
    private final int number;
    private final int firstRow;
    private final String key;
    private final long firstLine;
    private int lastRow;
    private long lastLine;
    private long characters;
    private String errorCode;
    private String error;
    private final List<List<String>> records = new ArrayList<List<String>>();
    private final MessageDigest digest = Fingerprints.sha256();
    private String rawHash;

    OrderGroup(int number, int row, CsvRecord first) {
        this.number = number;
        firstRow = row;
        firstLine = first.getLine();
        key = key(first);
    }

    void add(int row, CsvRecord record) {
        if (rawHash != null) { throw new IllegalStateException("Group already sealed"); }
        lastRow = row;
        lastLine = record.getLine();
        List<String> fields = record.getFields();
        for (String value : fields) {
            characters += value.length();
            for (int i = 0; i < value.length(); i++) { if (value.charAt(i) == '\n') { lastLine++; } }
        }
        digest.update(Fingerprints.of(fields.toArray(new String[fields.size()])).getBytes(Charset.forName("US-ASCII")));
        if (lastRow - firstRow + 1 > MAX_LINES) {
            reject("orderImport.groupLimit", "Order has more than " + MAX_LINES + " source rows");
        } else if (characters > MAX_CHARS) {
            reject("orderImport.groupLimit", "Order exceeds " + MAX_CHARS + " decoded characters");
        } else {
            List<String> normalized = new ArrayList<String>();
            for (String field : fields) { normalized.add(field.trim()); }
            records.add(Collections.unmodifiableList(normalized));
        }
    }

    void seal() { rawHash = Fingerprints.hex(digest.digest()); }

    public void reject(String code, String message) {
        if (error == null) { errorCode = code; error = message; }
    }

    public void validate() {
        if (error != null) { throw new BusinessException(errorCode, error); }
        if (!key.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,119}")) {
            throw new BusinessException("orderImport.key", "external_key must contain 1..120 identifier characters");
        }
        Set<String> products = new HashSet<String>();
        List<String> first = records.get(0);
        for (List<String> row : records) {
            ImportFields.count(row, ImportFields.ORDERS.length);
            for (int i = 0; i < row.size(); i++) {
                if (i != 7 && i != 8 && !first.get(i).equals(row.get(i))) {
                    throw new BusinessException("orderImport.headerConflict",
                            "Inconsistent " + ImportFields.ORDERS[i] + " within external_key=" + key);
                }
            }
            if (!products.add(row.get(7))) {
                throw new BusinessException("orderImport.duplicateProduct", "Product occurs more than once: " + row.get(7));
            }
            if (ImportFields.integer(row.get(8), "quantity") <= 0) {
                throw new BusinessException("orderImport.quantity", "quantity must be positive");
            }
        }
        Dates.parse(first.get(3));
        Dates.parse(first.get(4));
    }

    public String canonicalHash() {
        validate();
        List<String> payload = new ArrayList<String>();
        payload.add("order-import-v1");
        List<String> first = records.get(0);
        payload.addAll(first.subList(0, 7));
        payload.add(first.get(9));
        List<String> lines = new ArrayList<String>();
        for (List<String> row : records) {
            lines.add(Fingerprints.of(row.get(7), Integer.toString(ImportFields.integer(row.get(8), "quantity"))));
        }
        Collections.sort(lines);
        payload.addAll(lines);
        return Fingerprints.of(payload.toArray(new String[payload.size()]));
    }

    static String key(CsvRecord record) {
        return record.getFields().isEmpty() ? "" : record.getFields().get(0).trim();
    }

    public int getNumber() { return number; }
    public String getKey() { return key; }
    public String keyToken() { return Fingerprints.of(key); }
    public int getFirstRow() { return firstRow; }
    public int getLastRow() { return lastRow; }
    public long getFirstLine() { return firstLine; }
    public long getLastLine() { return lastLine; }
    public String getRawHash() { return rawHash; }
    public List<List<String>> getRecords() { return Collections.unmodifiableList(records); }
    public String describeSource() {
        String label = key.length() > 120 ? key.substring(0, 120) : key;
        return "external_key=" + label + "; source_rows=" + firstRow + ".." + lastRow
                + "; source_lines=" + firstLine + ".." + lastLine;
    }
}
