package jp.co.tsubame.wholesale.batch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Arrays;
import jp.co.tsubame.wholesale.batch.csv.CsvException;
import jp.co.tsubame.wholesale.batch.csv.CsvReader;
import jp.co.tsubame.wholesale.batch.csv.CsvRecord;
import jp.co.tsubame.wholesale.batch.csv.CsvWriter;
import org.junit.Test;
import static org.junit.Assert.*;

public class CsvTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private CsvReader csv(String text) { return new CsvReader(new ByteArrayInputStream(text.getBytes(UTF8))); }

    @Test public void quotesCommasBomAndPhysicalLines() throws Exception {
        try (CsvReader reader = csv("\ufeffcode,note\r\nP001,\"comma, quote \"\"x\"\"\r\nnext\"\r\nP002,end")) {
            reader.requireHeader(new String[] {"code", "note"});
            CsvRecord first = reader.read();
            assertEquals(2, first.getLine());
            assertEquals(Arrays.asList("P001", "comma, quote \"x\"\r\nnext"), first.getFields());
            CsvRecord second = reader.read();
            assertEquals(4, second.getLine());
            assertEquals(Arrays.asList("P002", "end"), second.getFields());
            assertNull(reader.read());
            assertNull(reader.read());
        }
    }

    @Test public void trailingEmptyAndQuotedEmptyFields() throws Exception {
        try (CsvReader reader = csv(",\"\",x,\n\nlast,")) {
            assertEquals(Arrays.asList("", "", "x", ""), reader.read().getFields());
            assertEquals(Arrays.asList(""), reader.read().getFields());
            assertEquals(Arrays.asList("last", ""), reader.read().getFields());
            assertNull(reader.read());
        }
    }

    @Test public void handlesUnicodeAndSupplementaryCharacters() throws Exception {
        try (CsvReader reader = csv("架空商品,🍵\n")) {
            assertEquals(Arrays.asList("架空商品", "\ud83c\udf75"), reader.read().getFields());
        }
    }

    @Test public void rejectsMalformedQuotesAndBareCr() throws Exception {
        for (String text : Arrays.asList("a\"b,c", "\"a\"x,c", "\"a\" ,c", "\"unclosed", "a\rb", "a,\u0000")) {
            try (CsvReader reader = csv(text)) {
                reader.read();
                fail("Accepted invalid CSV");
            } catch (CsvException expected) {
                assertTrue(expected.getMessage().contains("line 1"));
            }
        }
    }

    @Test public void rejectsWrongHeaderEvenWhenColumnsReordered() throws Exception {
        for (String text : Arrays.asList("", "b,a\n", "a,a\n", "a,b,\n", "a, b\n")) {
            try (CsvReader reader = csv(text)) {
                reader.requireHeader(new String[] {"a", "b"});
                fail("Accepted invalid header");
            } catch (CsvException expected) {
                assertEquals(1, expected.getLine());
            }
        }
    }

    @Test public void rejectsMalformedUtf8InsteadOfReplacingIt() throws Exception {
        byte[][] invalid = { {(byte) 0xc0, (byte) 0xaf}, {(byte) 0xff}, {(byte) 0xe3, (byte) 0x81},
                {(byte) 0xed, (byte) 0xa0, (byte) 0x80}, {'a', '\n', (byte) 0xff} };
        for (byte[] bytes : invalid) {
            try (CsvReader reader = new CsvReader(new ByteArrayInputStream(bytes))) {
                while (reader.read() != null) { }
                fail("Accepted invalid UTF-8");
            } catch (CsvException expected) {
                assertTrue(expected.getMessage().contains("malformed UTF-8"));
            }
        }
    }

    @Test public void utf8ErrorReportsNearbyPhysicalLine() throws Exception {
        byte[] bytes = {'a', '\n', 'b', '\n', 'c', ',', (byte) 0xff};
        try (CsvReader reader = new CsvReader(new ByteArrayInputStream(bytes))) {
            assertEquals(1, reader.read().getLine());
            assertEquals(2, reader.read().getLine());
            reader.read();
            fail("Accepted invalid UTF-8");
        } catch (CsvException expected) {
            assertEquals(3, expected.getLine());
        }
    }

    @Test public void enforcesFieldAndRecordLimits() throws Exception {
        try (CsvReader reader = new CsvReader(new ByteArrayInputStream("1234".getBytes(UTF8)), 3, 8)) {
            reader.read(); fail("Expected field limit");
        } catch (CsvException expected) { assertTrue(expected.getMessage().contains("field exceeds")); }
        try (CsvReader reader = new CsvReader(new ByteArrayInputStream("ab,cd,efg".getBytes(UTF8)), 3, 8)) {
            reader.read(); fail("Expected record limit");
        } catch (CsvException expected) { assertTrue(expected.getMessage().contains("record exceeds")); }
    }

    @Test public void enforcesColumnLimit() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i <= CsvReader.MAX_COLUMNS; i++) { text.append(','); }
        try (CsvReader reader = csv(text.toString())) { reader.read(); fail("Expected column limit"); }
        catch (CsvException expected) { assertTrue(expected.getMessage().contains("too many columns")); }
    }

    @Test public void writerQuotesAndPreservesNumbersButNeutralizesText() throws Exception {
        StringWriter text = new StringWriter();
        CsvWriter writer = new CsvWriter(text);
        writer.write(Arrays.<Object>asList("a,\"b\"", "line\nnext", "=SUM(A1:A2)", "  @cmd", "-10",
                new BigDecimal("-10.50"), 12, null));
        writer.flush();
        assertTrue(text.toString().endsWith("\r\n"));
        try (CsvReader reader = csv(text.toString())) {
            assertEquals(Arrays.asList("a,\"b\"", "line\nnext", "'=SUM(A1:A2)", "'  @cmd",
                    "'-10", "-10.50", "12", ""), reader.read().getFields());
        }
    }

    @Test public void spreadsheetProtectionHandlesControlPrefixes() {
        for (String text : Arrays.asList("=x", "+x", "-x", "@x", "\tx", "\rx", "\nx", " \t=x", "\ufeff=x")) {
            assertTrue(CsvWriter.spreadsheetSafe(text).startsWith("'"));
        }
        assertEquals("plain text", CsvWriter.spreadsheetSafe("plain text"));
        assertEquals("12.50", CsvWriter.spreadsheetSafe("12.50"));
    }
}
