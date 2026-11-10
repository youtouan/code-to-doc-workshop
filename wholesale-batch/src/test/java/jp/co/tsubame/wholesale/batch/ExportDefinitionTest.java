package jp.co.tsubame.wholesale.batch;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jp.co.tsubame.wholesale.common.Dates;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExportDefinitionTest {
    @Test public void dateStatusAndKeysetPredicatesAreParameterized() {
        Map<String, Object> parameters = new HashMap<String, Object>();
        String query = ExportDefinition.get("export-shipments").query(Dates.parse("2026-09-01"),
                Dates.parse("2026-09-08"), "CONFIRMED", parameters);
        assertTrue(query.contains("where l.id>:after"));
        assertTrue(query.contains("coalesce(s.shippedDate,s.plannedDate)>=:from"));
        assertTrue(query.contains("s.status=:status"));
        assertTrue(query.endsWith("order by l.id"));
        assertEquals("CONFIRMED", parameters.get("status"));
        assertFalse(query.contains("2026-09-01"));
    }

    @Test public void receiptCostRemainsNumericAndHeaderMatchesProjection() {
        List<Object> row = new ArrayList<Object>(Arrays.<Object>asList(1L, "RC-1", "key", Dates.parse("2026-09-01"),
                "EAST", "P001", "=formula", 3, new BigDecimal("12.50"), "ref", "note", "batch"));
        ExportDefinition definition = ExportDefinition.get("export-receipts");
        definition.complete(row);
        assertEquals(definition.getHeaders().size(), row.size());
        assertEquals(new BigDecimal("37.50"), row.get(12));
        assertEquals("2026-09-01", row.get(3));
        assertEquals("=formula", row.get(6)); // CSV writer, not query projection, owns formula escaping.
    }

    @Test public void cliRejectsIncoherentFiltersBeforeAnyDatabaseStartup() throws Exception {
        String[][] bad = {
            {"export-stock", "--from", "2026-09-01"},
            {"export-receipts", "--status", "CONFIRMED"},
            {"export-shipments", "--status", "APPROVED"},
            {"export-order-lines", "--from", "2026-09-08", "--to", "2026-09-01"},
            {"export-shipments", "--to", "2026-02-30"}
        };
        for (String[] request : bad) {
            List<String> args = new ArrayList<String>(Arrays.asList(request));
            args.addAll(Arrays.asList("--output", "target\\export-not-created-" + java.util.UUID.randomUUID() + ".csv",
                    "--limit", "10", "--user", "batch", "--password-env", "BATCH_TEST_SECRET"));
            try { Arguments.parse(args.toArray(new String[args.size()])); fail("Invalid filters accepted"); }
            catch (UsageException expected) { }
        }
    }
}
