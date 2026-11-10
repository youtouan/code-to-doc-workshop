package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jp.co.tsubame.wholesale.common.APInvoiceInput;
import jp.co.tsubame.wholesale.common.APMatchInput;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.PurchaseReceiptLine;
import org.junit.Test;
import static org.junit.Assert.*;

public class APMatchingTest extends APFixtureTest {
    @Test
    public void partialReceiptsAndPartialInvoicesReserveOnlyAcceptedQuantities() {
        APFixture fixture = apFixture(10);
        PurchaseReceiptLine first = fixture.receive(4, 1);
        PurchaseReceiptLine second = fixture.receive(6, 0);
        APInvoice invoice = invoices().saveDraft(null, 0, fixture.invoiceInput(7, "60"), billingActor);
        invoice = invoices().replaceMatches(invoice.getId(), invoice.getVersion(),
                Arrays.asList(match(invoice, first, 4), match(invoice, second, 3)), billingActor);
        assertTrue(invoice.isFullyMatched());
        assertEquals(2, invoice.getLines().get(0).getMatches().size());
        invoice = invoices().postInvoice(invoice.getId(), invoice.getVersion(), billingActor);
        money("462", invoice.getTotalAmount());
        assertEquals(1, reports().searchAvailableReceipts(search(fixture.supplier), billingActor).getTotal());
        assertEquals(3, reports().searchAvailableReceipts(search(fixture.supplier), billingActor).getItems().get(0).getAvailableQuantity());
        APInvoice remainder = posted(fixture, second, 3);
        money("198", remainder.getOutstandingAmount());
        assertEquals(0, reports().searchAvailableReceipts(search(fixture.supplier), billingActor).getTotal());
    }

    @Test
    public void rejectedOnlyReceiptsCannotBeInvoiced() {
        final APFixture fixture = apFixture(3);
        final PurchaseReceiptLine rejected = fixture.receive(0, 3);
        final APInvoice invoice = invoices().saveDraft(null, 0, fixture.invoiceInput(1, "60"), billingActor);
        expect("ap.notAccepted", new Runnable() {
            public void run() { invoices().replaceMatches(invoice.getId(), invoice.getVersion(),
                    Arrays.asList(match(invoice, rejected, 1)), billingActor); }
        });
        assertEquals(0, reports().searchAvailableReceipts(search(fixture.supplier), billingActor).getTotal());
        assertFalse(invoices().getInvoice(invoice.getId(), billingActor).isFullyMatched());
    }

    @Test
    public void orderedButUnreceivedInvoiceCannotPost() {
        APFixture fixture = apFixture(2);
        final APInvoice invoice = invoices().saveDraft(null, 0, fixture.invoiceInput(2, "60"), billingActor);
        expect("ap.incompleteMatch", new Runnable() {
            public void run() { invoices().postInvoice(invoice.getId(), invoice.getVersion(), billingActor); }
        });
        assertEquals("DRAFT", invoices().getInvoice(invoice.getId(), billingActor).getStatus());
    }

    @Test
    public void otherDraftClaimsAndPostedClaimsBothPreventDoubleMatching() {
        final APFixture fixture = apFixture(5);
        final PurchaseReceiptLine receipt = fixture.receive(5, 0);
        APInvoice first = matched(fixture, receipt, 4, "60");
        final APInvoice other = invoices().saveDraft(null, 0, fixture.invoiceInput(2, "60"), billingActor);
        expect("ap.receiptOvermatch", new Runnable() {
            public void run() { invoices().replaceMatches(other.getId(), other.getVersion(),
                    Arrays.asList(match(other, receipt, 2)), billingActor); }
        });
        invoices().postInvoice(first.getId(), first.getVersion(), billingActor);
        expect("ap.receiptOvermatch", new Runnable() {
            public void run() { invoices().replaceMatches(other.getId(), other.getVersion(),
                    Arrays.asList(match(other, receipt, 2)), billingActor); }
        });
    }

    @Test
    public void cancelledDraftReleasesClaimsButRetainsItsMatchingHistory() {
        APFixture fixture = apFixture(2);
        PurchaseReceiptLine receipt = fixture.receive(2, 0);
        APInvoice original = matched(fixture, receipt, 2, "60");
        invoices().cancelDraft(original.getId(), original.getVersion(), "仕入先訂正請求待ち", billingActor);
        assertEquals(1, invoices().getInvoice(original.getId(), billingActor).getLines().get(0).getMatches().size());
        APInvoice replacement = posted(fixture, receipt, 2);
        assertNotEquals(original.getId(), replacement.getId());
    }

    @Test
    public void unmatchAndRevisionReleaseClaimsAndInvalidateReview() {
        APFixture fixture = apFixture(2);
        PurchaseReceiptLine receipt = fixture.receive(2, 0);
        APInvoice invoice = matched(fixture, receipt, 2, "62");
        invoice = invoices().approveVariance(invoice.getId(), invoice.getVersion(), "追加加工賃確認済み", manager);
        invoice = invoices().replaceMatches(invoice.getId(), invoice.getVersion(), Collections.<APMatchInput>emptyList(), billingActor);
        assertEquals("NONE", invoice.getVarianceStatus());
        assertNull(invoice.getVarianceApprovedBy());
        assertEquals(2, invoice.getLines().get(0).getUnmatchedQuantity());
        APInvoiceInput revised = fixture.invoiceInput(1, "60");
        revised.setSupplierInvoiceNumber(invoice.getSupplierInvoiceNumber());
        invoice = invoices().saveDraft(invoice.getId(), invoice.getVersion(), revised, billingActor);
        assertEquals(0, invoice.getLines().get(0).getMatches().size());
        assertEquals(2, reports().searchAvailableReceipts(search(fixture.supplier), billingActor).getItems().get(0).getAvailableQuantity());
    }

    @Test
    public void failedReplacementRollsBackDeletedMatchesAndNewReservations() {
        APFixture fixture = apFixture(2);
        PurchaseReceiptLine receipt = fixture.receive(2, 0);
        final APInvoice invoice = matched(fixture, receipt, 2, "60");
        final APMatchInput valid = match(invoice, receipt, 1);
        APFixture other = apFixture(1);
        final APMatchInput invalid = match(invoice, other.receive(1, 0), 1);
        final Long originalMatch = invoice.getLines().get(0).getMatches().get(0).getId();
        expect("ap.matchSupplier", new Runnable() {
            public void run() { invoices().replaceMatches(invoice.getId(), invoice.getVersion(), Arrays.asList(valid, invalid), billingActor); }
        });
        APInvoice unchanged = invoices().getInvoice(invoice.getId(), billingActor);
        assertEquals(invoice.getVersion(), unchanged.getVersion());
        assertEquals(originalMatch, unchanged.getLines().get(0).getMatches().get(0).getId());
        assertEquals(2, unchanged.getLines().get(0).getMatchedQuantity());
    }

    @Test
    public void varianceNeedsReasonAndIndependentManagerApproval() {
        APFixture fixture = apFixture(2);
        PurchaseReceiptLine receipt = fixture.receive(2, 0);
        final APInvoice invoice = matched(fixture, receipt, 2, "65");
        assertEquals("REQUIRED", invoice.getVarianceStatus());
        money("10", invoice.getVarianceAmount());
        expect("ap.varianceApproval", new Runnable() {
            public void run() { invoices().postInvoice(invoice.getId(), invoice.getVersion(), billingActor); }
        });
        expect("permission.denied", new Runnable() {
            public void run() { invoices().approveVariance(invoice.getId(), invoice.getVersion(), "確認", billingActor); }
        });
        expect("validation.required", new Runnable() {
            public void run() { invoices().approveVariance(invoice.getId(), invoice.getVersion(), "", manager); }
        });
        assertEquals(1, reports().searchVarianceQueue(search(fixture.supplier), manager).getTotal());
        APInvoice approved = invoices().approveVariance(invoice.getId(), invoice.getVersion(), "仕入先追加運賃の明細確認", manager);
        APInvoice posted = invoices().postInvoice(approved.getId(), approved.getVersion(), billingActor);
        assertEquals("POSTED", posted.getStatus());
        money("143", posted.getTotalAmount());
        assertEquals(0, reports().searchVarianceQueue(search(fixture.supplier), manager).getTotal());
    }

    @Test
    public void administratorCannotApproveOwnMaterialDiscrepancy() {
        APFixture fixture = apFixture(1);
        PurchaseReceiptLine receipt = fixture.receive(1, 0);
        APInvoice draft = invoices().saveDraft(null, 0, fixture.invoiceInput(1, "65"), admin);
        final APInvoice invoice = invoices().replaceMatches(draft.getId(), draft.getVersion(),
                Arrays.asList(match(draft, receipt, 1)), admin);
        expect("ap.selfApproval", new Runnable() {
            public void run() { invoices().approveVariance(invoice.getId(), invoice.getVersion(), "自己承認不可", admin); }
        });
        APInvoice approved = invoices().approveVariance(invoice.getId(), invoice.getVersion(), "別担当確認", manager);
        assertEquals("APPROVED", approved.getVarianceStatus());
    }

    @Test
    public void duplicateSupplierNumberAndCurrencyScaleAreValidated() {
        final APFixture fixture = apFixture(1);
        APInvoiceInput input = fixture.invoiceInput(1, "60");
        input.setSupplierInvoiceNumber(" inv-duplicate ");
        invoices().saveDraft(null, 0, input, billingActor);
        final APInvoiceInput duplicate = fixture.invoiceInput(1, "60");
        duplicate.setSupplierInvoiceNumber("INV-DUPLICATE");
        expect("ap.duplicateInvoice", new Runnable() {
            public void run() { invoices().saveDraft(null, 0, duplicate, billingActor); }
        });
        final APInvoiceInput fractional = fixture.invoiceInput(1, "60.001");
        expect("validation.money.scale", new Runnable() {
            public void run() { invoices().saveDraft(null, 0, fractional, billingActor); }
        });
    }

    @Test
    public void concurrentInvoicesCannotBothClaimTheSameAcceptedQuantity() throws Exception {
        APFixture fixture = apFixture(4);
        final PurchaseReceiptLine receipt = fixture.receive(4, 0);
        final APInvoice first = invoices().saveDraft(null, 0, fixture.invoiceInput(4, "60"), billingActor);
        final APInvoice second = invoices().saveDraft(null, 0, fixture.invoiceInput(4, "60"), billingActor);
        final CountDownLatch gate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = executor.submit(matchTask(first, receipt, gate));
            Future<String> b = executor.submit(matchTask(second, receipt, gate));
            gate.countDown();
            List<String> results = Arrays.asList(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertTrue(results.contains("MATCHED"));
            assertTrue(results.contains("ap.receiptOvermatch"));
            assertEquals(0, reports().searchAvailableReceipts(search(fixture.supplier), billingActor).getTotal());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Callable<String> matchTask(final APInvoice invoice, final PurchaseReceiptLine receipt, final CountDownLatch gate) {
        return new Callable<String>() {
            public String call() throws Exception {
                assertTrue(gate.await(10, TimeUnit.SECONDS));
                try {
                    invoices().replaceMatches(invoice.getId(), invoice.getVersion(),
                            Arrays.asList(match(invoice, receipt, 4)), billingActor);
                    return "MATCHED";
                } catch (BusinessException ex) {
                    return ex.getCode();
                }
            }
        };
    }
}
