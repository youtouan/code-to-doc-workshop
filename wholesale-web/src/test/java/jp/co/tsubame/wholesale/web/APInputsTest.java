package jp.co.tsubame.wholesale.web;

import java.math.BigDecimal;
import jp.co.tsubame.wholesale.common.APInvoiceInput;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.web.form.APCreditForm;
import jp.co.tsubame.wholesale.web.form.APForm;
import jp.co.tsubame.wholesale.web.form.APInvoiceForm;
import jp.co.tsubame.wholesale.web.form.APMatchForm;
import jp.co.tsubame.wholesale.web.form.APPaymentForm;
import org.junit.Test;
import static org.junit.Assert.*;

public class APInputsTest {
    @Test public void searchUsesSupplierAndCommonStrictPagination() {
        APForm form = new APForm(); form.setSupplierId("19"); form.setPageNumber("2"); form.setSize("50");
        assertEquals(Long.valueOf(19), APInputs.search(form).getSupplierId());
        assertEquals(50, APInputs.search(form).getOffset());
    }
    @Test public void invoiceInputIsTypedAndEmptyRowsAreNotSubmitted() {
        APInvoiceForm form = invoice();
        APInvoiceInput input = APInputs.invoice(form);
        assertEquals(1, input.getLines().size()); assertEquals(Long.valueOf(4), input.getLines().get(0).getProductId());
        assertEquals(new BigDecimal("0.10"), input.getLines().get(0).getTaxRate()); assertNull(input.getDueDate());
    }
    @Test public void invoiceRequiresProductAndConsistentColumns() {
        final APInvoiceForm form = invoice(); form.getProductId()[0] = "";
        invalid(new Runnable() { public void run() { APInputs.invoice(form); } });
        final APInvoiceForm mismatch = invoice(); mismatch.setTaxRate(new String[] {"0.10"});
        invalid(new Runnable() { public void run() { APInputs.invoice(mismatch); } });
    }
    @Test public void taxRateIsPlainBoundedDecimalWithAtMostFourPlaces() {
        assertEquals(new BigDecimal("0.1234"), APInputs.rate("0.1234"));
        assertEquals(new BigDecimal("1.0000"), APInputs.rate("1.0000"));
        for (final String value : new String[] {"10%", "10", "-0.1", "1.0001", "0.00001", "1e-1", " 0.1", ""}) {
            invalid(new Runnable() { public void run() { APInputs.rate(value); } });
        }
    }
    @Test public void matchingOmitsZeroRowsAndRejectsDuplicatePairs() {
        final APMatchForm form = new APMatchForm(); form.rows(3);
        form.setInvoiceLineId(new String[] {"1", "2", ""}); form.setReceiptLineId(new String[] {"11", "12", ""});
        form.setQuantity(new String[] {"5", "0", ""});
        assertEquals(1, APInputs.matches(form).size());
        form.setInvoiceLineId(new String[] {"1", "1", ""}); form.setReceiptLineId(new String[] {"11", "11", ""});
        form.setQuantity(new String[] {"5", "1", ""});
        invalid(new Runnable() { public void run() { APInputs.matches(form); } });
    }
    @Test public void matchingSupportsFiveHundredButRequiresExplicitClearForEmptyReplacement() {
        APMatchForm form = new APMatchForm(); form.rows(500);
        for (int i = 0; i < 500; i++) {
            form.getInvoiceLineId()[i] = "1"; form.getReceiptLineId()[i] = String.valueOf(i + 1); form.getQuantity()[i] = "1";
        }
        assertEquals(500, APInputs.matches(form).size());
        final APMatchForm empty = new APMatchForm(); empty.rows(5);
        invalid(new Runnable() { public void run() { APInputs.matches(empty); } });
    }
    @Test public void creditsOmitUntouchedAmountsAndRejectDuplicateInvoiceLines() {
        final APCreditForm form = new APCreditForm(); form.setSupplierCreditNumber("CREDIT-TEST");
        form.setCreditDate("2020-01-31"); form.setReason("契約上の値引");
        form.setInvoiceLineId(new String[] {"1", "2"}); form.setNetAmount(new String[] {"10.50", ""});
        assertEquals(1, APInputs.credit(form).getLines().size());
        form.setInvoiceLineId(new String[] {"1", "1"});
        invalid(new Runnable() { public void run() { APInputs.credit(form); } });
    }
    @Test public void paymentRequiresExactPositiveAllocationTotalAndUniqueInvoices() {
        final APPaymentForm form = payment();
        assertEquals(2, APInputs.payment(form).getLines().size());
        form.setAmount("31");
        invalid(new Runnable() { public void run() { APInputs.payment(form); } });
        form.setAmount("30"); form.setInvoiceId(new String[] {"7", "7"});
        invalid(new Runnable() { public void run() { APInputs.payment(form); } });
        form.setInvoiceId(new String[] {"7", "8"}); form.setAllocationAmount(new String[] {"30", "0"});
        invalid(new Runnable() { public void run() { APInputs.payment(form); } });
    }
    @Test public void paymentRetainsRequestKeyAndRejectsUnknownPaymentMethods() {
        final APPaymentForm form = payment();
        assertEquals("stable-retry-key", APInputs.payment(form).getRequestKey());
        form.setMethod("CRYPTO");
        invalid(new Runnable() { public void run() { APInputs.payment(form); } });
        assertEquals("stable-retry-key", form.getRequestKey());
    }
    @Test public void invoiceCreditAndPaymentSupportTheCore200LineLimit() {
        APInvoiceForm invoice = invoice(); invoice.rows(200);
        for (int i = 0; i < 200; i++) {
            invoice.getProductId()[i] = String.valueOf(i + 1); invoice.getQuantity()[i] = "1";
            invoice.getUnitPrice()[i] = "1"; invoice.getTaxRate()[i] = "0.10";
        }
        assertEquals(200, APInputs.invoice(invoice).getLines().size());
        APCreditForm credit = new APCreditForm(); credit.setSupplierCreditNumber("LIMIT-200");
        credit.setCreditDate("2020-01-31"); credit.setReason("検証"); credit.setInvoiceLineId(new String[200]); credit.setNetAmount(new String[200]);
        APPaymentForm payment = payment(); payment.rows(200); payment.setAmount("200");
        for (int i = 0; i < 200; i++) {
            credit.getInvoiceLineId()[i] = String.valueOf(i + 1); credit.getNetAmount()[i] = "1";
            payment.getInvoiceId()[i] = String.valueOf(i + 1); payment.getAllocationAmount()[i] = "1";
        }
        assertEquals(200, APInputs.credit(credit).getLines().size());
        assertEquals(200, APInputs.payment(payment).getLines().size());
        final APInvoiceForm oversized = invoice;
        invalid(new Runnable() { public void run() { oversized.rows(201); } });
    }
    @Test public void apQuantitiesMatchTheCoreMillionUnitCeiling() {
        final APInvoiceForm invoice = invoice(); invoice.getQuantity()[0] = "1000001";
        invalid(new Runnable() { public void run() { APInputs.invoice(invoice); } });
        final APMatchForm matching = new APMatchForm(); matching.rows(1);
        matching.getInvoiceLineId()[0] = "1"; matching.getReceiptLineId()[0] = "2"; matching.getQuantity()[0] = "1000001";
        invalid(new Runnable() { public void run() { APInputs.matches(matching); } });
    }
    private APInvoiceForm invoice() {
        APInvoiceForm form = new APInvoiceForm(); form.rows(2); form.setSupplierId("1");
        form.setSupplierInvoiceNumber("SUPPLIER-TEST"); form.setInvoiceDate("2020-01-31");
        form.getProductId()[0] = "4"; form.getQuantity()[0] = "5"; form.getUnitPrice()[0] = "10.00"; form.getTaxRate()[0] = "0.10";
        return form;
    }
    private APPaymentForm payment() {
        APPaymentForm form = new APPaymentForm(); form.setRequestKey("stable-retry-key"); form.setSupplierId("1");
        form.setPaymentDate("2020-01-31"); form.setAmount("30"); form.setMethod("BANK_TRANSFER");
        form.setInvoiceId(new String[] {"7", "8"}); form.setAllocationAmount(new String[] {"10", "20"}); return form;
    }
    private void invalid(Runnable action) {
        try { action.run(); fail("Expected typed input rejection"); }
        catch (BusinessException expected) { assertNotNull(expected.getField()); }
    }
}
