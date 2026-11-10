package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import jp.co.tsubame.wholesale.dao.APLedger;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.APInvoiceLine;
import jp.co.tsubame.wholesale.entity.APMatch;
import jp.co.tsubame.wholesale.service.APSettlementService;
import org.junit.Test;
import static org.junit.Assert.*;

public class APRulesTest {
    @Test
    public void financialCreditsTelescopeForAllRoundingModesAndNormalizedRates() {
        for (String mode : new String[] {"DOWN", "UP", "HALF_UP"}) {
            TaxAmounts original = new TaxAmounts(mode);
            original.add(new BigDecimal("10.02"), new BigDecimal("0.1"));
            TaxAmounts cumulative = new TaxAmounts(mode);
            BigDecimal previous = Money.ZERO;
            BigDecimal sum = Money.ZERO;
            for (int index = 0; index < 3; index++) {
                cumulative.add(new BigDecimal("3.34"), new BigDecimal("0.1000"));
                BigDecimal tax = APLedger.creditTax(original, cumulative, mode);
                assertTrue(tax.compareTo(previous) >= 0);
                sum = sum.add(tax.subtract(previous));
                previous = tax;
            }
            assertEquals(original.getTaxAmount(), sum);
        }
    }

    @Test(expected = BusinessException.class)
    public void creditCannotExceedTaxBucketEvenWhenAnotherRateHasCapacity() {
        TaxAmounts original = new TaxAmounts("DOWN");
        original.add(BigDecimal.TEN, new BigDecimal("0.1"));
        original.add(new BigDecimal("1000"), new BigDecimal("0.08"));
        TaxAmounts credit = new TaxAmounts("DOWN");
        credit.add(new BigDecimal("11"), new BigDecimal("0.1"));
        APLedger.creditTax(original, credit, "DOWN");
    }

    @Test
    public void oppositeCostVariancesDoNotCancelReviewRequirement() {
        APInvoice invoice = new APInvoice();
        invoice.setTaxRounding("DOWN");
        APInvoiceLine first = line(invoice, "61.00");
        APInvoiceLine second = line(invoice, "59.00");
        invoice.getLines().add(first);
        invoice.getLines().add(second);
        APLedger.invalidateReview(invoice);
        assertEquals(Money.ZERO, invoice.getVarianceAmount());
        assertEquals(new BigDecimal("2.00"), invoice.getAbsoluteVarianceAmount());
        assertEquals("REQUIRED", invoice.getVarianceStatus());
    }

    @Test
    public void priceAgreementVarianceIsVisibleEvenWhenReceiptAndInvoiceAgree() {
        APInvoice invoice = new APInvoice();
        invoice.setTaxRounding("DOWN");
        APInvoiceLine line = line(invoice, "60.00");
        line.getMatches().get(0).setOrderedUnitCost(new BigDecimal("55.00"));
        invoice.getLines().add(line);
        APLedger.invalidateReview(invoice);
        assertEquals(Money.ZERO, invoice.getVarianceAmount());
        assertEquals(new BigDecimal("5.00"), invoice.getAbsoluteVarianceAmount());
        assertEquals("REQUIRED", invoice.getVarianceStatus());
    }

    @Test
    public void editsRemovePreviousApproval() {
        APInvoice invoice = new APInvoice();
        invoice.setTaxRounding("DOWN");
        invoice.setVarianceStatus("APPROVED");
        invoice.setVarianceApprovedBy("manager");
        invoice.setReviewFingerprint("previous");
        invoice.getLines().add(line(invoice, "61.00"));
        APLedger.invalidateReview(invoice);
        assertNull(invoice.getVarianceApprovedBy());
        assertNull(invoice.getReviewFingerprint());
        assertEquals("REQUIRED", invoice.getVarianceStatus());
    }

    @Test
    public void voucherNormalizationIsDeterministic() {
        APPaymentLineInput first = payment(5L, "1");
        APPaymentLineInput second = payment(2L, "2.00");
        Map<Long, BigDecimal> normalized = APSettlementService.normalize(Arrays.asList(first, second), new BigDecimal("3"));
        assertEquals(Long.valueOf(2), normalized.keySet().iterator().next());
        assertEquals(new BigDecimal("1.00"), normalized.get(5L));
    }

    @Test(expected = BusinessException.class)
    public void voucherCannotLeaveExcessUnallocated() {
        APSettlementService.normalize(Arrays.asList(payment(1L, "10")), new BigDecimal("11"));
    }

    @Test(expected = BusinessException.class)
    public void voucherCannotDuplicateAnInvoice() {
        APSettlementService.normalize(Arrays.asList(payment(1L, "10"), payment(1L, "10")), new BigDecimal("20"));
    }

    @Test(expected = BusinessException.class)
    public void voucherRejectsFractionalMinorCurrencyUnits() {
        APSettlementService.normalize(Arrays.asList(payment(1L, "1.001")), new BigDecimal("1.001"));
    }

    @Test(expected = BusinessException.class)
    public void taxRateCannotSilentlyRoundPrecision() { APLedger.rate(new BigDecimal("0.12345")); }

    @Test
    public void onlyPostedInvoicesRepresentOutstandingLiabilities() {
        APInvoice invoice = new APInvoice();
        invoice.setTotalAmount(new BigDecimal("110.00"));
        assertEquals(Money.ZERO, invoice.getOutstandingAmount());
        invoice.setStatus("POSTED");
        invoice.setPaidAmount(new BigDecimal("30.00"));
        invoice.setCreditedAmount(new BigDecimal("20.00"));
        assertEquals(new BigDecimal("60.00"), invoice.getOutstandingAmount());
        invoice.setStatus("CANCELLED");
        assertEquals(Money.ZERO, invoice.getOutstandingAmount());
    }

    private APPaymentLineInput payment(Long id, String amount) {
        APPaymentLineInput result = new APPaymentLineInput();
        result.setInvoiceId(id);
        result.setAmount(new BigDecimal(amount));
        return result;
    }

    private APInvoiceLine line(APInvoice invoice, String price) {
        APInvoiceLine result = new APInvoiceLine();
        result.setInvoice(invoice);
        result.setQuantity(1);
        result.setUnitPrice(new BigDecimal(price));
        result.setNetAmount(new BigDecimal(price));
        result.setTaxRate(new BigDecimal("0.1"));
        APMatch match = new APMatch();
        match.setInvoiceLine(result);
        match.setQuantity(1);
        match.setReceiptUnitCost(new BigDecimal("60.00"));
        match.setOrderedUnitCost(new BigDecimal("60.00"));
        result.getMatches().add(match);
        return result;
    }
}
