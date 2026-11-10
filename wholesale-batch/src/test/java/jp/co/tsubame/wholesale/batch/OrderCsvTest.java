package jp.co.tsubame.wholesale.batch;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import jp.co.tsubame.wholesale.batch.csv.CsvException;
import jp.co.tsubame.wholesale.batch.csv.CsvReader;
import jp.co.tsubame.wholesale.batch.csv.InputSnapshot;
import jp.co.tsubame.wholesale.common.BusinessException;
import org.junit.Test;
import static org.junit.Assert.*;

public class OrderCsvTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String HEADER = "external_key,customer_code,warehouse_code,order_date,requested_date,"
            + "external_reference,delivery_address,product_code,quantity,notes\n";

    @Test public void consecutiveGroupsRetainRecordAndPhysicalLineSpans() throws Exception {
        String lines = line("A", "P001", "2", "\"notes\nsecond line\"")
                + line("A", "P002", "3", "\"notes\nsecond line\"") + line("B", "P003", "1", "");
        try (OrderCsv reader = reader(lines)) {
            OrderGroup first = reader.read();
            first.validate();
            assertEquals(1, first.getNumber());
            assertEquals(1, first.getFirstRow());
            assertEquals(2, first.getLastRow());
            assertEquals(2, first.getFirstLine());
            assertEquals(5, first.getLastLine());
            assertEquals("notes\nsecond line", first.getRecords().get(0).get(9));
            OrderGroup second = reader.read();
            assertEquals("B", second.getKey());
            assertEquals(6, second.getFirstLine());
            assertNull(reader.read());
        }
    }

    @Test public void canonicalPayloadNormalizesQuantityWhitespaceAndProductOrder() throws Exception {
        OrderGroup first = group(line(" A ", " P001 ", "002", " note ") + line(" A ", "P002", "3", "note"));
        OrderGroup second = group(line("A", "P002", "3", "note") + line("A", "P001", "2", "note"));
        assertEquals(first.canonicalHash(), second.canonicalHash());
        assertFalse(first.getRawHash().equals(second.getRawHash()));
        assertFalse(first.canonicalHash().equals(group(line("A", "P001", "4", "note")).canonicalHash()));
    }

    @Test public void groupHeaderDuplicateProductQuantityAndDatesRejectWholeGroup() throws Exception {
        expect("orderImport.headerConflict", line("A", "P001", "1", "same")
                + line("A", "P002", "1", "different"));
        expect("orderImport.headerConflict", line("A", "P001", "1", "")
                + line("A", "P002", "1", "").replace("C001", "C002"));
        expect("orderImport.duplicateProduct", line("A", "P001", "1", "") + line("A", "P001", "2", ""));
        expect("orderImport.quantity", line("A", "P001", "0", ""));
        expect("csv.integer", line("A", "P001", "1e2", ""));
        expect("validation.date", line("A", "P001", "1", "").replace("2026-09-01", "2026-02-30"));
        expect("csv.columns", "A,C001,EAST\n");
        expect("orderImport.key", line("bad key", "P001", "1", ""));
    }

    @Test public void moreThanTwoHundredLinesRejectsOneGroupWithoutSplitting() throws Exception {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 201; i++) { lines.append(line("A", "P" + i, "1", "")); }
        lines.append(line("B", "P001", "1", ""));
        try (OrderCsv reader = reader(lines.toString())) {
            OrderGroup first = reader.read();
            assertEquals(201, first.getLastRow());
            assertEquals(200, first.getRecords().size());
            try { first.validate(); fail("Oversized group accepted"); }
            catch (BusinessException expected) { assertEquals("orderImport.groupLimit", expected.getCode()); }
            assertEquals("B", reader.read().getKey());
        }
    }

    @Test public void groupCharacterLimitIsBoundedIndependentlyOfFieldLimit() throws Exception {
        StringBuilder note = new StringBuilder();
        for (int i = 0; i < 60000; i++) { note.append('x'); }
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 40; i++) { lines.append(line("A", "P" + i, "1", note.toString())); }
        expect("orderImport.groupLimit", lines.toString());
    }

    @Test public void preflightFindsAllNoncontiguousFragmentsBeforeProcessing() throws Exception {
        Path path = Paths.get("target", "order-preflight-" + UUID.randomUUID().toString() + ".csv");
        Files.write(path, (HEADER + line("A", "P001", "1", "") + line("B", "P001", "1", "")
                + line("A", "P002", "1", "")).getBytes(UTF8));
        try (InputSnapshot input = new InputSnapshot(path)) {
            Set<String> repeated = OrderCsv.repeatedKeys(input);
            assertEquals(1, repeated.size());
            assertTrue(repeated.contains(group(line("A", "P001", "1", "")).keyToken()));
        } finally { Files.delete(path); }
    }

    @Test public void exactHeaderAndMalformedQuotedRecordsUseExistingParser() throws Exception {
        try (OrderCsv ignored = new OrderCsv(new CsvReader(new ByteArrayInputStream("wrong,header\n".getBytes(UTF8))))) {
            fail("Wrong header accepted");
        } catch (CsvException expected) { assertEquals(1, expected.getLine()); }
        try (OrderCsv reader = reader(line("A", "P001", "1", "") + "\"unterminated")) {
            reader.read();
            fail("Malformed later record accepted");
        } catch (CsvException expected) { assertTrue(expected.getMessage().contains("unterminated")); }
    }

    private static void expect(String code, String lines) throws Exception {
        try { group(lines).validate(); fail("Expected " + code); }
        catch (BusinessException expected) { assertEquals(code, expected.getCode()); }
    }
    private static OrderGroup group(String lines) throws Exception {
        try (OrderCsv reader = reader(lines)) { return reader.read(); }
    }
    private static OrderCsv reader(String lines) throws Exception {
        return new OrderCsv(new CsvReader(new ByteArrayInputStream((HEADER + lines).getBytes(UTF8))));
    }
    private static String line(String key, String product, String quantity, String note) {
        return key + ",C001,EAST,2026-09-01,2026-09-08,PO-001,," + product + "," + quantity + "," + note + "\n";
    }
}
