package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuoteRulesTest {
    @Test
    public void validityIncludesTheFinalDayAndZeroNegotiatedPricesAreExplicit() {
        QuotationCommand command = input();
        command.setValidUntil(command.getQuoteDate());
        command.getLines().get(0).setNegotiatedUnitPrice(BigDecimal.ZERO);
        command.getLines().get(0).setNegotiationReason("架空無償見本の提案");
        QuotationRules.command(command);
    }

    @Test(expected = BusinessException.class)
    public void validityCannotPrecedeTheQuoteDate() {
        QuotationCommand command = input();
        command.setValidUntil(Dates.addDays(command.getQuoteDate(), -1));
        QuotationRules.command(command);
    }

    @Test(expected = BusinessException.class)
    public void validityHasAFiniteCommercialHorizon() {
        QuotationCommand command = input();
        command.setValidUntil(Dates.addDays(command.getQuoteDate(), 181));
        QuotationRules.command(command);
    }

    @Test(expected = BusinessException.class)
    public void negotiatedPricesRequireAnExplanationEvenWhenZero() {
        QuotationCommand command = input();
        command.getLines().get(0).setNegotiatedUnitPrice(BigDecimal.ZERO);
        QuotationRules.command(command);
    }

    @Test(expected = BusinessException.class)
    public void fractionalCentsAreNotRoundedSilently() {
        QuotationCommand command = input();
        command.getLines().get(0).setNegotiatedUnitPrice(new BigDecimal("1.001"));
        command.getLines().get(0).setNegotiationReason("架空交渉");
        QuotationRules.command(command);
    }

    @Test(expected = BusinessException.class)
    public void duplicateProductsCannotSplitQuantityTiers() {
        QuotationCommand command = input();
        QuotationLineCommand second = new QuotationLineCommand();
        second.setProductId(11L);
        second.setQuantity(1);
        command.getLines().add(second);
        QuotationRules.command(command);
    }

    @Test
    public void fingerprintSeparatesNullsAndLengthDelimitedFields() {
        assertFalse(new QuotationFingerprint().add(null).finish()
                .equals(new QuotationFingerprint().add("<null>").finish()));
        assertFalse(new QuotationFingerprint().add("a;b").add("c").finish()
                .equals(new QuotationFingerprint().add("a").add("b;c").finish()));
        assertEquals(new QuotationFingerprint().add(new BigDecimal("10.00")).finish(),
                new QuotationFingerprint().add(new BigDecimal("10.0")).finish());
    }

    @Test(expected = BusinessException.class)
    public void grossAmountsCannotOverflowTheOrderStorageRange() {
        TaxAmounts amounts = new TaxAmounts("UP");
        amounts.add(new BigDecimal("9999999999999999.99"), new BigDecimal("0.10"));
        QuotationRules.amounts(amounts);
    }

    private QuotationCommand input() {
        QuotationCommand command = new QuotationCommand();
        command.setCustomerId(1L);
        command.setWarehouseId(21L);
        command.setQuoteDate(Dates.today());
        command.setValidUntil(Dates.addDays(Dates.today(), 30));
        command.setRequestedDate(Dates.addDays(Dates.today(), 3));
        QuotationLineCommand line = new QuotationLineCommand();
        line.setProductId(11L);
        line.setQuantity(10);
        command.getLines().add(line);
        return command;
    }
}
