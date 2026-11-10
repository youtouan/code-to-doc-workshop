package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;

public class CommonRulesTest {
    @Test
    public void taxIsRoundedPerRateRatherThanPerLine() {
        TaxAmounts amounts = new TaxAmounts("DOWN");
        amounts.add(new BigDecimal("6.00"), new BigDecimal("0.10"));
        amounts.add(new BigDecimal("6.00"), new BigDecimal("0.1000"));
        amounts.add(new BigDecimal("10.00"), new BigDecimal("0.0800"));
        assertEquals(new BigDecimal("22.00"), amounts.getNetAmount());
        assertEquals(new BigDecimal("1.00"), amounts.getTaxAmount());
        assertEquals(2, amounts.getBases().size());
    }

    @Test
    public void roundingModesHaveDistinctBoundaries() {
        assertEquals(new BigDecimal("0.00"), Money.tax(new BigDecimal("5.00"), new BigDecimal("0.10"), "DOWN"));
        assertEquals(new BigDecimal("1.00"), Money.tax(new BigDecimal("5.00"), new BigDecimal("0.10"), "HALF_UP"));
        assertEquals(new BigDecimal("1.00"), Money.tax(new BigDecimal("1.00"), new BigDecimal("0.10"), "UP"));
        assertEquals(new BigDecimal("0.00"), Money.tax(new BigDecimal("100.00"), BigDecimal.ZERO, "UP"));
    }

    @Test
    public void decimalMoneyNeverUsesBinaryFloatingPoint() {
        assertEquals(new BigDecimal("0.30"), Money.amount(new BigDecimal("0.10"), 3));
        assertEquals(new BigDecimal("1.20"), Checks.money(new BigDecimal("1.2000"), "amount", true));
    }

    @Test
    public void fractionalCentsAreRejectedRatherThanRoundedAtInput() {
        try {
            Checks.money(new BigDecimal("1.001"), "amount", true);
            fail("Fractional cents must not be silently truncated");
        } catch (BusinessException ex) {
            assertEquals("validation.money.scale", ex.getCode());
        }
    }

    @Test
    public void calendarParsingIsStrictAndHandlesLeapDays() {
        assertEquals("2024-02-29", Dates.format(Dates.parse("2024-02-29")));
        assertEquals("2024-02-29", Dates.format(Dates.closingDate(Dates.parse("2024-02-01"), 31)));
        assertEquals("2025-02-28", Dates.format(Dates.closingDate(Dates.parse("2025-02-01"), 31)));
        assertEquals("2026-01-01", Dates.format(Dates.addDays(Dates.parse("2025-12-31"), 1)));
        for (String invalid : new String[] {"2025-02-29", "2026-13-01", "2026-01-32", "2026-1-01", "2026-01-01x"}) {
            try {
                Dates.parse(invalid);
                fail("Accepted invalid date " + invalid);
            } catch (BusinessException expected) {
                assertEquals("validation.date", expected.getCode());
            }
        }
    }

    @Test
    public void searchEscapesLikeMetacharactersAndBoundsPages() {
        Search search = new Search();
        search.setText("A_50%!");
        assertEquals("%A!_50!%!!%", search.getLikeText());
        search.setSize(25);
        search.setPage(3);
        assertEquals(50, search.getOffset());
        try {
            search.setPage(0);
            fail("Invalid page");
        } catch (BusinessException expected) {
            assertEquals("validation.page", expected.getCode());
        }
    }

    @Test
    public void fingerprintFieldBoundariesCannotCollide() {
        assertNotEquals(Fingerprints.of("ab", "c"), Fingerprints.of("a", "bc"));
        assertNotEquals(Fingerprints.of("a:b", "c"), Fingerprints.of("a", "b:c"));
        assertEquals(Fingerprints.of("日本語", "0"), Fingerprints.of("日本語", "0"));
    }

    @Test
    public void rolesAreExactNotSubstringMatches() {
        Actor actor = new Actor(1L, "test", "test", "SALES, BILLING");
        actor.require("BILLING");
        assertFalse(actor.hasRole("ADMIN"));
        try {
            actor.require("WAREHOUSE");
            fail("Wrong role");
        } catch (BusinessException ex) {
            assertEquals("permission.denied", ex.getCode());
        }
        new Actor(2L, "admin", "admin", "ADMIN").require("WAREHOUSE");
    }

    @Test
    public void passwordHashIsSaltedAndChecksActualPassword() {
        String first = Passwords.hash("Synthetic-Test-4839".toCharArray());
        String second = Passwords.hash("Synthetic-Test-4839".toCharArray());
        assertNotEquals(first, second);
        assertTrue(Passwords.matches("Synthetic-Test-4839".toCharArray(), first));
        assertFalse(Passwords.matches("Synthetic-Test-4838".toCharArray(), first));
    }
}
