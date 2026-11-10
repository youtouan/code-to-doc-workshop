package jp.co.tsubame.wholesale.web;

import java.math.BigDecimal;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.OrderAmendmentCommand;
import jp.co.tsubame.wholesale.common.QuotationCommand;
import jp.co.tsubame.wholesale.web.form.OrderAmendmentForm;
import jp.co.tsubame.wholesale.web.form.QuotationForm;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuotationInputsTest {
    @Test public void negotiatedProposalIsOptionalAndRequiresReasonWhenPresent() {
        final QuotationForm form = quotation();
        assertNull(QuotationInputs.quotation(form).getLines().get(0).getNegotiatedUnitPrice());
        form.getNegotiatedUnitPrice()[0] = "40.00";
        invalid(new Runnable() { public void run() { QuotationInputs.quotation(form); } });
        form.getNegotiationReason()[0] = "数量契約";
        QuotationCommand command = QuotationInputs.quotation(form);
        assertEquals(new BigDecimal("40.00"), command.getLines().get(0).getNegotiatedUnitPrice());
        assertEquals("数量契約", command.getLines().get(0).getNegotiationReason());
    }
    @Test public void duplicateProductsAndQuantitiesBeyondOneMillionAreRejected() {
        final QuotationForm form = quotation(); form.getProductId()[1] = "4"; form.getQuantity()[1] = "1";
        invalid(new Runnable() { public void run() { QuotationInputs.quotation(form); } });
        form.getProductId()[1] = ""; form.getQuantity()[1] = ""; form.getQuantity()[0] = "1000001";
        invalid(new Runnable() { public void run() { QuotationInputs.quotation(form); } });
    }
    @Test public void proposalSupports200RowsAndRetainsBlankOptionalColumns() {
        QuotationForm form = quotation(); form.rows(200);
        for (int i = 0; i < 200; i++) { form.getProductId()[i] = String.valueOf(i + 1); form.getQuantity()[i] = "1"; }
        assertEquals(200, QuotationInputs.quotation(form).getLines().size());
    }
    @Test public void dateOnlyAmendmentHasNoQuantityOrPricingCommands() {
        OrderAmendmentForm form = amendment(); form.setRequestedDate("2020-02-01");
        OrderAmendmentCommand command = QuotationInputs.amendment(form);
        assertTrue(command.getLines().isEmpty()); assertNotNull(command.getRequestedDate());
        assertEquals(7, command.getExpectedOrderVersion());
    }
    @Test public void unchangedQuantityRowsAreOmittedAndTargetsAreTotalsNotDeltas() {
        OrderAmendmentForm form = amendment(); form.setOrderLineId(new String[] {"10", "11"});
        form.setTargetQuantity(new String[] {"24", ""});
        OrderAmendmentCommand command = QuotationInputs.amendment(form);
        assertNull(command.getRequestedDate()); assertEquals(1, command.getLines().size());
        assertEquals(Long.valueOf(10), command.getLines().get(0).getOrderLineId());
        assertEquals(24, command.getLines().get(0).getTargetQuantity());
    }
    @Test public void amendmentRejectsZeroNegativeDuplicateAndEmptyTargets() {
        for (final String value : new String[] {"0", "-1", "1000001"}) {
            final OrderAmendmentForm form = amendment(); form.setOrderLineId(new String[] {"10"}); form.setTargetQuantity(new String[] {value});
            invalid(new Runnable() { public void run() { QuotationInputs.amendment(form); } });
        }
        final OrderAmendmentForm duplicate = amendment(); duplicate.setOrderLineId(new String[] {"10", "10"});
        duplicate.setTargetQuantity(new String[] {"12", "24"});
        invalid(new Runnable() { public void run() { QuotationInputs.amendment(duplicate); } });
        final OrderAmendmentForm empty = amendment();
        invalid(new Runnable() { public void run() { QuotationInputs.amendment(empty); } });
    }
    private QuotationForm quotation() {
        QuotationForm form = new QuotationForm(); form.rows(2); form.setCustomerId("1"); form.setWarehouseId("2");
        form.setQuoteDate("2020-01-01"); form.setValidUntil("2020-01-31"); form.setRequestedDate("2020-01-15");
        form.getProductId()[0] = "4"; form.getQuantity()[0] = "12"; return form;
    }
    private OrderAmendmentForm amendment() {
        OrderAmendmentForm form = new OrderAmendmentForm(); form.setOrderId("5"); form.setExpectedOrderVersion("7");
        form.setReason("顧客からの数量変更依頼"); return form;
    }
    private void invalid(Runnable action) {
        try { action.run(); fail("Expected validation error"); }
        catch (BusinessException expected) { assertNotNull(expected.getField()); }
    }
}
