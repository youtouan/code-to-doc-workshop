package jp.co.tsubame.wholesale.batch;

import java.util.Arrays;
import jp.co.tsubame.wholesale.common.BusinessException;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImportFieldsTest {
    @Test public void numbersRejectScientificNotationGroupingAndOverflow() {
        for (String value : Arrays.asList("1e3", "1,000", "", " 1", "1.234", "12345678901234567")) {
            try { ImportFields.money(value, "cost"); fail("Invalid amount accepted"); }
            catch (BusinessException expected) { assertEquals("csv.money", expected.getCode()); }
        }
        for (String value : Arrays.asList("1.0", "2147483648", "", " 1", "+2")) {
            try { ImportFields.integer(value, "quantity"); fail("Invalid integer accepted"); }
            catch (BusinessException expected) { assertEquals("csv.integer", expected.getCode()); }
        }
        assertEquals("1.20", ImportFields.money("1.20", "cost").toPlainString());
        assertEquals(-3, ImportFields.integer("-3", "quantity"));
    }

    @Test public void booleanTyposNeverSilentlyBecomeFalse() {
        assertTrue(ImportFields.bool("true", "active"));
        assertFalse(ImportFields.bool("false", "active"));
        for (String value : Arrays.asList("TRUE", "yes", "1", "")) {
            try { ImportFields.bool(value, "active"); fail("Invalid boolean accepted"); }
            catch (BusinessException expected) { assertEquals("csv.boolean", expected.getCode()); }
        }
    }
}
