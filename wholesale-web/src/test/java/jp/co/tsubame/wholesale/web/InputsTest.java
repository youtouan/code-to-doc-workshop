package jp.co.tsubame.wholesale.web;

import java.math.BigDecimal;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.junit.Test;
import static org.junit.Assert.*;

public class InputsTest {
    @Test public void acceptsStrictIdsAndOptionalEmpty() {
        assertEquals(Long.valueOf(12), Inputs.id("12", "ID"));
        assertEquals(Long.valueOf(Long.MAX_VALUE), Inputs.id(String.valueOf(Long.MAX_VALUE), "ID"));
        assertNull(Inputs.optionalId("", "ID"));
        assertNull(Inputs.optionalId(null, "ID"));
    }
    @Test public void rejectsMalformedAndOverflowIds() {
        for (final String value : new String[] {"0", "-1", "+1", "01", " 1", "1 ", "1.0", "1e2", "９", "", "9223372036854775808"}) {
            invalid(new Runnable() { public void run() { Inputs.id(value, "ID"); } });
        }
    }
    @Test public void moneyDoesNotAcceptScientificNotationOrGrouping() {
        assertEquals(new BigDecimal("123.45"), Inputs.money("123.45", "金額", false));
        assertEquals(new BigDecimal("0"), Inputs.money("0", "金額", false));
        assertNull(Inputs.money("", "金額", true));
        for (final String value : new String[] {"1e3", "1,000", "-1", "+1", "01", ".5", "1.", "1.234", "NaN", "", "1000000000000"}) {
            invalid(new Runnable() { public void run() { Inputs.money(value, "金額", false); } });
        }
    }
    @Test public void quantitiesAndSignedAdjustmentsAreBounded() {
        assertEquals(0, Inputs.quantity("0", "数", true));
        assertEquals(100000000, Inputs.quantity("100000000", "数", false));
        assertEquals(-10, Inputs.integer("-10", "調整", -100, 100));
        for (final String value : new String[] {"0", "-1", "1.5", "1e3", "+4", " 2", "2147483648", "100000001"}) {
            invalid(new Runnable() { public void run() { Inputs.quantity(value, "数", false); } });
        }
    }
    @Test public void datesAreActualCalendarDates() {
        assertEquals("2024-02-29", Dates.format(Inputs.date("2024-02-29", "日付", false)));
        assertNull(Inputs.date("", "日付", true));
        for (final String value : new String[] {"2023-02-29", "2026-04-31", "2026-13-01", "2026-1-01", "2026-01-01x", ""}) {
            invalid(new Runnable() { public void run() { Inputs.date(value, "日付", false); } });
        }
    }
    @Test public void lineColumnsMustHaveSameLengthAndBoundedRows() {
        Inputs.arrays(new String[] {"1"}, new String[] {"2"}, new String[] {""});
        invalid(new Runnable() { public void run() { Inputs.arrays(new String[0], new String[0]); } });
        invalid(new Runnable() { public void run() { Inputs.arrays(new String[] {"1"}, new String[] {"1", "2"}); } });
        invalid(new Runnable() { public void run() { Inputs.arrays(new String[101], new String[101]); } });
        invalid(new Runnable() { public void run() { Inputs.arrays(null, new String[0]); } });
        invalid(new Runnable() { public void run() { Inputs.uniqueIds(new String[] {"1", "1"}, "明細"); } });
    }
    @Test public void searchDateRangeAndPaginationAreStrict() {
        BaseForm form = new BaseForm();
        assertEquals(1, Inputs.search(form).getPage());
        assertEquals(25, Inputs.search(form).getSize());
        form.setPageNumber("2");
        assertEquals(25, Inputs.search(form).getOffset());
        final BaseForm reversed = new BaseForm(); reversed.setFrom("2026-02-02"); reversed.setTo("2026-01-01");
        invalid(new Runnable() { public void run() { Inputs.search(reversed); } });
        final BaseForm overflow = new BaseForm(); overflow.setSize("101");
        invalid(new Runnable() { public void run() { Inputs.search(overflow); } });
    }
    @Test public void paginationIsIndependentOfValidatorWizardPage() {
        BaseForm form = new BaseForm();
        form.setPageNumber("3");
        assertEquals(3, Inputs.search(form).getPage());
        assertEquals(50, Inputs.search(form).getOffset());
        assertEquals(0, form.getPage());
        form.setPage(7);
        assertEquals("3", form.getPageNumber());
        assertEquals(3, Inputs.search(form).getPage());
        form.setPageNumber("4");
        assertEquals(7, form.getPage());
        assertEquals(4, Inputs.search(form).getPage());
    }
    @Test public void booleansAndChoicesDoNotCoerceArbitraryValues() {
        assertTrue(Inputs.bool("true", "状態"));
        assertFalse(Inputs.bool("false", "状態"));
        assertEquals("DRAFT", Inputs.choice("DRAFT", "状態", "DRAFT", "POSTED"));
        invalid(new Runnable() { public void run() { Inputs.bool("on", "状態"); } });
        invalid(new Runnable() { public void run() { Inputs.choice("x", "状態", "DRAFT"); } });
    }
    @Test public void textDoesNotDiscardMeaningfulUnicode() {
        assertEquals("つばめ卸", Inputs.text(" つばめ卸 ", "名称", 10, true));
        assertEquals("<script>", Inputs.text("<script>", "名称", 20, false));
        invalid(new Runnable() { public void run() { Inputs.text(" ", "名称", 20, true); } });
        invalid(new Runnable() { public void run() { Inputs.text("a\u0000b", "名称", 20, true); } });
    }
    private static void invalid(Runnable action) {
        try { action.run(); fail("Expected BusinessException"); }
        catch (BusinessException expected) {
            assertNotNull(expected.getField());
            assertNotNull(expected.getMessage());
        }
    }
}
