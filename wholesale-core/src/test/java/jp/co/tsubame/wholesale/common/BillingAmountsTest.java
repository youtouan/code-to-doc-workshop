package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import jp.co.tsubame.wholesale.dao.BillingAmounts;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BillingAmountsTest {
    @Test
    public void groupsAllLinesAtEqualNumericRatesBeforeRounding() {
        BillingAmounts amounts = new BillingAmounts("DOWN");
        amounts.add(new BigDecimal("5.00"), new BigDecimal("0.1"));
        amounts.add(new BigDecimal("5.00"), new BigDecimal("0.1000"));
        amounts.add(new BigDecimal("12.50"), new BigDecimal("0.0800"));
        amounts.add(new BigDecimal("100.00"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("122.50"), amounts.net());
        assertEquals(new BigDecimal("2.00"), amounts.tax());
        assertEquals(new BigDecimal("124.50"), amounts.gross());
    }

    @Test
    public void cumulativeCreditsExactlyReverseOriginalTaxForEveryRoundingMode() {
        for (String mode : new String[] {"DOWN", "UP", "HALF_UP"}) {
            BillingAmounts original = new BillingAmounts(mode);
            original.add(new BigDecimal("10.02"), new BigDecimal("0.1"));
            BillingAmounts returned = new BillingAmounts(mode);
            BigDecimal previous = Money.ZERO;
            BigDecimal sum = Money.ZERO;
            for (int index = 0; index < 3; index++) {
                returned.add(new BigDecimal("3.34"), new BigDecimal("0.1000"));
                BigDecimal cumulative = original.cumulativeCreditTax(returned);
                BigDecimal increment = cumulative.subtract(previous);
                org.junit.Assert.assertTrue(increment.signum() >= 0);
                sum = sum.add(increment);
                previous = cumulative;
            }
            assertEquals(original.tax(), sum);
        }
    }

    @Test
    public void differenceMethodNeverCreditsMoreThanClaimOnTinyReturns() {
        BillingAmounts original = new BillingAmounts("UP");
        original.add(new BigDecimal("0.03"), new BigDecimal("0.1"));
        BillingAmounts returns = new BillingAmounts("UP");
        returns.add(new BigDecimal("0.01"), new BigDecimal("0.1"));
        assertEquals(Money.ZERO, original.cumulativeCreditTax(returns));
        returns.add(new BigDecimal("0.02"), new BigDecimal("0.1"));
        assertEquals(new BigDecimal("1.00"), original.cumulativeCreditTax(returns));
    }

    @Test(expected = BusinessException.class)
    public void refusesCreditForUnknownRate() {
        BillingAmounts original = new BillingAmounts("DOWN");
        original.add(new BigDecimal("10"), new BigDecimal("0.1"));
        BillingAmounts returned = new BillingAmounts("DOWN");
        returned.add(new BigDecimal("1"), new BigDecimal("0.08"));
        original.cumulativeCreditTax(returned);
    }

    @Test(expected = BusinessException.class)
    public void refusesOvercreditInOneBucketEvenIfOtherBucketHasCapacity() {
        BillingAmounts original = new BillingAmounts("DOWN");
        original.add(new BigDecimal("10"), new BigDecimal("0.1"));
        original.add(new BigDecimal("1000"), new BigDecimal("0.08"));
        BillingAmounts returned = new BillingAmounts("DOWN");
        returned.add(new BigDecimal("11"), new BigDecimal("0.1"));
        original.cumulativeCreditTax(returned);
    }

    @Test(expected = BusinessException.class)
    public void refusesUnknownRoundingEvenWithNoLines() {
        new BillingAmounts("CEILING");
    }

    @Test(expected = BusinessException.class)
    public void refusesNegativeClaims() {
        BillingAmounts original = new BillingAmounts("DOWN");
        original.add(new BigDecimal("-1"), new BigDecimal("0.1"));
    }
}
