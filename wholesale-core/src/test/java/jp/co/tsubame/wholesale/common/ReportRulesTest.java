package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReportRulesTest {
    @Test
    public void supplierPercentagesUseTheCorrectDenominators() {
        SupplierQualityRow row = new SupplierQualityRow(1L, "S", "Supplier", 2, 80, 20, 10, new BigDecimal("800.00"));
        assertEquals(new BigDecimal("20.00"), row.getRejectionPercent());
        assertEquals(new BigDecimal("12.50"), row.getLateAcceptedPercent());
        SupplierQualityRow empty = new SupplierQualityRow(1L, "S", "Supplier", 0, 0, 0, 0, Money.ZERO);
        assertEquals(Money.ZERO, empty.getRejectionPercent());
        assertEquals(Money.ZERO, empty.getLateAcceptedPercent());
    }

    @Test
    public void historicalReportRejectsFutureButBacklogAllowsFuturePromises() {
        ReportFilter filter = new ReportFilter();
        filter.setTo(Dates.addDays(Dates.today(), 2));
        filter.validateBacklogPeriod();
        try {
            filter.validatePeriod();
            fail("Future actuals");
        } catch (BusinessException expected) {
            assertEquals("report.future", expected.getCode());
        }
    }

    @Test
    public void reportPeriodIsBoundedAndOrdered() {
        ReportFilter filter = new ReportFilter();
        filter.setFrom(Dates.addDays(Dates.today(), -367));
        try {
            filter.validatePeriod();
            fail("Unbounded range");
        } catch (BusinessException expected) {
            assertEquals("report.periodLimit", expected.getCode());
        }
        filter.setFrom(Dates.today());
        filter.setTo(Dates.addDays(Dates.today(), -1));
        try {
            filter.validatePeriod();
            fail("Reversed range");
        } catch (BusinessException expected) {
            assertEquals("report.period", expected.getCode());
        }
    }
}
