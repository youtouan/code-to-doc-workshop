package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jp.co.tsubame.wholesale.common.APCreditInput;
import jp.co.tsubame.wholesale.common.APPaymentInput;
import jp.co.tsubame.wholesale.common.APPaymentLineInput;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.entity.APCredit;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.APPaymentVoucher;
import jp.co.tsubame.wholesale.entity.PurchaseReceiptLine;
import jp.co.tsubame.wholesale.entity.Supplier;
import org.junit.Test;
import static org.junit.Assert.*;

public class APSettlementTest extends APFixtureTest {
    @Test
    public void financialAllowancesRoundCumulativelyAndNeverMoveStock() {
        APFixture fixture = apFixture(3, "3.34");
        PurchaseReceiptLine receipt = fixture.receive(3, 0);
        APInvoice invoice = posted(fixture, receipt, 3);
        money("11.02", invoice.getTotalAmount());
        BigDecimal creditedTax = BigDecimal.ZERO;
        for (int count = 0; count < 3; count++) {
            invoice = invoices().getInvoice(invoice.getId(), billingActor);
            APCredit credit = invoices().proposeCredit(invoice.getId(), invoice.getVersion(), creditInput(invoice, "3.34"), billingActor);
            credit = invoices().approveCredit(credit.getId(), credit.getVersion(), manager);
            creditedTax = creditedTax.add(credit.getTaxAmount());
            assertEquals("POSTED", credit.getStatus());
        }
        money("1", creditedTax);
        money("0", invoices().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
        assertEquals(3, purchasing().getReceipt(receipt.getReceipt().getId(), warehouseActor).getAcceptedQuantity());
        javax.sql.DataSource source = context.getBean("dataSource", javax.sql.DataSource.class);
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        assertEquals(Integer.valueOf(3), jdbc.queryForObject("select on_hand from stock_balance where product_id=? and warehouse_id=21",
                new Object[] {fixture.product.getId()}, Integer.class));
    }

    @Test
    public void pendingAllowancesReserveLineCapacityAndCancellationReleasesIt() {
        APFixture fixture = apFixture(1);
        final APInvoice invoice = posted(fixture, fixture.receive(1, 0), 1);
        APCredit first = invoices().proposeCredit(invoice.getId(), invoice.getVersion(), creditInput(invoice, "40"), billingActor);
        expect("ap.overCredit", new Runnable() {
            public void run() { invoices().proposeCredit(invoice.getId(), invoice.getVersion(), creditInput(invoice, "21"), billingActor); }
        });
        invoices().cancelCredit(first.getId(), first.getVersion(), "訂正値引書に差替え", billingActor);
        APCredit replacement = invoices().proposeCredit(invoice.getId(), invoice.getVersion(), creditInput(invoice, "60"), billingActor);
        invoices().approveCredit(replacement.getId(), replacement.getVersion(), manager);
        money("0", invoices().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
    }

    @Test
    public void creditApprovalSeparatesRequesterAndReviewerEvenForAdministrator() {
        APFixture fixture = apFixture(1);
        APInvoice invoice = posted(fixture, fixture.receive(1, 0), 1);
        final APCredit credit = invoices().proposeCredit(invoice.getId(), invoice.getVersion(), creditInput(invoice, "10"), admin);
        expect("ap.selfApproval", new Runnable() {
            public void run() { invoices().approveCredit(credit.getId(), credit.getVersion(), admin); }
        });
        final APCredit approved = invoices().approveCredit(credit.getId(), credit.getVersion(), manager);
        expect("ap.creditNotDraft", new Runnable() {
            public void run() { invoices().cancelCredit(approved.getId(), approved.getVersion(), "計上後取消不可", billingActor); }
        });
    }

    @Test
    public void creditCannotCreateNegativeLiabilityAfterPayment() {
        APFixture fixture = apFixture(1);
        APInvoice invoice = posted(fixture, fixture.receive(1, 0), 1);
        final APCredit proposed = invoices().proposeCredit(invoice.getId(), invoice.getVersion(), creditInput(invoice, "10"), billingActor);
        APPaymentVoucher payment = settlements().pay(paymentInput(invoice, "66"), billingActor);
        expect("ap.creditOutstanding", new Runnable() {
            public void run() { invoices().approveCredit(proposed.getId(), proposed.getVersion(), manager); }
        });
        assertEquals("DRAFT", invoices().getCredit(proposed.getId(), billingActor).getStatus());
        settlements().cancelPayment(payment.getId(), payment.getVersion(), "振込照合の取消訂正", billingActor);
        invoices().approveCredit(proposed.getId(), proposed.getVersion(), manager);
        money("55", invoices().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
    }

    @Test
    public void oneVoucherPartiallySettlesSeveralInvoicesAndCancellationReversesAll() {
        APFixture fixture = apFixture(4);
        PurchaseReceiptLine receipt = fixture.receive(4, 0);
        APInvoice first = posted(fixture, receipt, 2);
        APInvoice second = posted(fixture, receipt, 2);
        APPaymentInput input = paymentInput(first, "100");
        input.setAmount(new BigDecimal("200"));
        APPaymentLineInput line = new APPaymentLineInput();
        line.setInvoiceId(second.getId());
        line.setAmount(new BigDecimal("100"));
        input.getLines().add(line);
        APPaymentVoucher voucher = settlements().pay(input, billingActor);
        assertEquals(2, voucher.getLines().size());
        money("32", invoices().getInvoice(first.getId(), billingActor).getOutstandingAmount());
        money("32", invoices().getInvoice(second.getId(), billingActor).getOutstandingAmount());
        settlements().cancelPayment(voucher.getId(), voucher.getVersion(), "二重振込取消", billingActor);
        money("132", invoices().getInvoice(first.getId(), billingActor).getOutstandingAmount());
        money("132", invoices().getInvoice(second.getId(), billingActor).getOutstandingAmount());
        assertEquals("REVERSED", settlements().getPayment(voucher.getId(), billingActor).getLines().get(0).getStatus());
    }

    @Test
    public void mixedSupplierVoucherRollsBackWithoutAnySettlement() {
        APFixture firstFixture = apFixture(2);
        final APInvoice first = posted(firstFixture, firstFixture.receive(2, 0), 2);
        APFixture otherFixture = apFixture(2);
        APInvoice other = posted(otherFixture, otherFixture.receive(2, 0), 2);
        final APPaymentInput input = paymentInput(first, "10");
        input.setAmount(new BigDecimal("20"));
        APPaymentLineInput line = new APPaymentLineInput();
        line.setInvoiceId(other.getId());
        line.setAmount(new BigDecimal("10"));
        input.getLines().add(line);
        expect("ap.paymentSupplier", new Runnable() {
            public void run() { settlements().pay(input, billingActor); }
        });
        money("132", invoices().getInvoice(first.getId(), billingActor).getOutstandingAmount());
        assertEquals(0, settlements().searchPayments(search(firstFixture.supplier), billingActor).getTotal());
    }

    @Test
    public void overpaymentAndUnallocatedExcessAreRejected() {
        APFixture fixture = apFixture(1);
        final APInvoice invoice = posted(fixture, fixture.receive(1, 0), 1);
        expect("ap.overpayment", new Runnable() {
            public void run() { settlements().pay(paymentInput(invoice, "67"), billingActor); }
        });
        final APPaymentInput extra = paymentInput(invoice, "60");
        extra.setAmount(new BigDecimal("66"));
        expect("ap.paymentTotal", new Runnable() {
            public void run() { settlements().pay(extra, billingActor); }
        });
    }

    @Test
    public void requestKeysArePayloadSensitiveAndDoNotResurrectCancelledPayments() {
        APFixture fixture = apFixture(1);
        APInvoice invoice = posted(fixture, fixture.receive(1, 0), 1);
        final APPaymentInput input = paymentInput(invoice, "10");
        APPaymentVoucher voucher = settlements().pay(input, billingActor);
        assertEquals(voucher.getId(), settlements().pay(input, billingActor).getId());
        input.setReference("異なる銀行照合番号");
        expect("ap.paymentKeyConflict", new Runnable() {
            public void run() { settlements().pay(input, billingActor); }
        });
        input.setReference("試験照合");
        settlements().cancelPayment(voucher.getId(), voucher.getVersion(), "取消後のリトライ", billingActor);
        assertEquals("CANCELLED", settlements().pay(input, billingActor).getStatus());
    }

    @Test
    public void inactiveAndHeldSupplierCanStillSettleHistoricalInvoices() {
        APFixture fixture = apFixture(1);
        APInvoice invoice = posted(fixture, fixture.receive(1, 0), 1);
        Supplier supplier = purchasing().getSupplier(fixture.supplier.getId(), manager);
        supplier.setActive(false);
        supplier.setOnHold(true);
        purchasing().saveSupplier(supplier, supplier.getVersion(), manager);
        settlements().pay(paymentInput(invoice, "66"), billingActor);
        money("0", invoices().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
    }

    @Test
    public void concurrentPaymentsDoNotOverspendOneInvoice() throws Exception {
        APFixture fixture = apFixture(2);
        final APInvoice invoice = posted(fixture, fixture.receive(2, 0), 2);
        final CountDownLatch gate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> call = new Callable<String>() {
                public String call() throws Exception {
                    assertTrue(gate.await(10, TimeUnit.SECONDS));
                    try {
                        settlements().pay(paymentInput(invoice, "100"), billingActor);
                        return "PAID";
                    } catch (BusinessException ex) { return ex.getCode(); }
                }
            };
            Future<String> first = executor.submit(call);
            Future<String> second = executor.submit(call);
            gate.countDown();
            assertTrue(Arrays.asList(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))
                    .containsAll(Arrays.asList("PAID", "ap.overpayment")));
            money("32", invoices().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
