package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jp.co.tsubame.wholesale.common.AccountStatement;
import jp.co.tsubame.wholesale.common.BillingCashEntry;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.ReceivablesAgeing;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Invoice;
import jp.co.tsubame.wholesale.entity.PaymentAllocation;
import jp.co.tsubame.wholesale.entity.PaymentReceipt;
import jp.co.tsubame.wholesale.entity.Shipment;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReceivablesWorkflowTest extends BillingTest {
    @Test
    public void receiptRetryIsPayloadSensitiveAndCancellationDoesNotReuseTheKey() {
        final Fixture fixture = fixture(0);
        final String key = uniqueCode("RCT");
        PaymentReceipt receipt = receive(fixture, key, "300");
        assertEquals(receipt.getId(), receive(fixture, key, "300.00").getId());
        expect("receipt.keyConflict", new Runnable() {
            public void run() { receive(fixture, key, "301"); }
        });
        final Fixture other = fixture(0);
        expect("receipt.keyConflict", new Runnable() {
            public void run() { receive(other, key, "300"); }
        });
        receivables().cancelReceipt(receipt.getId(), receipt.getVersion(), "重複入金記録の訂正", billingActor);
        PaymentReceipt retried = receive(fixture, key, "300");
        assertEquals(receipt.getId(), retried.getId());
        assertEquals("CANCELLED", retried.getStatus());
        money("0", retried.getUnallocatedAmount());
    }

    @Test
    public void partialAllocationAndReversalRetainAuditLedgerAndDeposit() {
        Fixture fixture = fixture(10);
        ship(fixture, 5, closingDate());
        Invoice invoice = finalizeFor(fixture, closingDate());
        final PaymentReceipt receipt = receive(fixture, uniqueCode("RCT"), "600");
        final PaymentAllocation allocation = receivables().allocate(receipt.getId(), receipt.getVersion(),
                invoice.getId(), new BigDecimal("200"), billingActor);
        money("350", billing().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
        PaymentReceipt current = receivables().getReceipt(receipt.getId(), billingActor);
        money("400", current.getUnallocatedAmount());
        final int currentVersion = current.getVersion();
        expect("receipt.allocated", new Runnable() {
            public void run() { receivables().cancelReceipt(receipt.getId(), currentVersion, "不可", billingActor); }
        });
        receivables().reverseAllocation(allocation.getId(), allocation.getVersion(), "請求選択訂正", billingActor);
        money("550", billing().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
        current = receivables().getReceipt(receipt.getId(), billingActor);
        money("600", current.getUnallocatedAmount());
        assertEquals("REVERSED", current.getAllocations().get(0).getStatus());
        final PaymentAllocation reversed = current.getAllocations().get(0);
        expect("allocation.reversed", new Runnable() {
            public void run() { receivables().reverseAllocation(reversed.getId(), reversed.getVersion(), "二重取消", billingActor); }
        });
        AccountStatement statement = receivables().getStatement(fixture.customer.getId(),
                Dates.addDays(Dates.today(), -1), Dates.today(), billingActor);
        money("-50", statement.getClosingBalance());
        assertEquals(2, statement.getLines().size());
        ReceivablesAgeing ageing = receivables().getAgeing(fixture.customer.getId(), Dates.today(), billingActor);
        money("550", ageing.getTotalOutstanding());
        money("600", ageing.getUnallocatedReceipts());
        money("-50", ageing.getNetBalance());
    }

    @Test
    public void oneReceiptCanSettleTwoPeriodsWithoutOverAllocating() {
        final Fixture fixture = fixture(10);
        Date previous = Dates.monthEnd(Dates.addMonths(closingDate(), -1));
        ship(fixture, 2, previous);
        final Invoice first = finalizeFor(fixture, previous);
        ship(fixture, 3, closingDate());
        final Invoice second = finalizeFor(fixture, closingDate());
        PaymentReceipt receipt = receive(fixture, uniqueCode("RCT"), "600");
        receivables().allocate(receipt.getId(), receipt.getVersion(), first.getId(), new BigDecimal("220"), billingActor);
        receipt = receivables().getReceipt(receipt.getId(), billingActor);
        receivables().allocate(receipt.getId(), receipt.getVersion(), second.getId(), new BigDecimal("330"), billingActor);
        final PaymentReceipt deposit = receivables().getReceipt(receipt.getId(), billingActor);
        money("50", deposit.getUnallocatedAmount());
        money("0", billing().getInvoice(first.getId(), billingActor).getOutstandingAmount());
        money("0", billing().getInvoice(second.getId(), billingActor).getOutstandingAmount());
        expect("allocation.invoiceAmount", new Runnable() {
            public void run() { receivables().allocate(deposit.getId(), deposit.getVersion(),
                    first.getId(), new BigDecimal("1"), billingActor); }
        });
        assertEquals(2, receivables().getReceipt(receipt.getId(), billingActor).getAllocations().size());
    }

    @Test
    public void rejectsCrossCustomerAndDraftInvoiceAllocations() {
        Fixture fixture = fixture(2);
        ship(fixture, 2, closingDate());
        final Invoice draft = billing().prepare(fixture.customer.getId(), closingDate(), billingActor);
        final PaymentReceipt same = receive(fixture, uniqueCode("RCT"), "500");
        expect("allocation.invoiceState", new Runnable() {
            public void run() { receivables().allocate(same.getId(), same.getVersion(), draft.getId(),
                    new BigDecimal("1"), billingActor); }
        });
        Fixture other = fixture(0);
        final PaymentReceipt different = receive(other, uniqueCode("RCT"), "500");
        expect("allocation.customer", new Runnable() {
            public void run() { receivables().allocate(different.getId(), different.getVersion(), draft.getId(),
                    new BigDecimal("1"), billingActor); }
        });
    }

    @Test
    public void creditAfterPaymentCreatesCustomerCreditAndReversalReappliesIt() {
        Fixture fixture = fixture(4);
        Shipment shipment = ship(fixture, 4, closingDate());
        Invoice invoice = finalizeFor(fixture, closingDate());
        PaymentReceipt receipt = receive(fixture, uniqueCode("RCT"), "440");
        PaymentAllocation allocation = receivables().allocate(receipt.getId(), receipt.getVersion(),
                invoice.getId(), new BigDecimal("440"), billingActor);
        returnGoods(shipment, 2);
        money("0", billing().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
        money("220", billing().listCredits(fixture.customer.getId(), billingActor).get(0).getUnappliedAmount());
        ReceivablesAgeing ageing = receivables().getAgeing(fixture.customer.getId(), Dates.today(), billingActor);
        money("0", ageing.getTotalOutstanding());
        money("220", ageing.getUnappliedCredits());
        money("-220", ageing.getNetBalance());
        receivables().reverseAllocation(allocation.getId(), allocation.getVersion(), "振込照合訂正", billingActor);
        money("220", billing().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
        money("0", billing().listCredits(fixture.customer.getId(), billingActor).get(0).getUnappliedAmount());
        ageing = receivables().getAgeing(fixture.customer.getId(), Dates.today(), billingActor);
        money("440", ageing.getUnallocatedReceipts());
        money("-220", ageing.getNetBalance());
        money("-220", receivables().getStatement(fixture.customer.getId(), Dates.today(), Dates.today(), billingActor)
                .getClosingBalance());
    }

    @Test
    public void historicalReceiptBalanceSurvivesLaterCancellation() {
        Fixture fixture = fixture(0);
        Date yesterday = Dates.addDays(Dates.today(), -1);
        PaymentReceipt receipt = receivables().receive(uniqueCode("RCT"), fixture.customer.getId(), yesterday,
                new BigDecimal("700"), "BANK_TRANSFER", "HISTORICAL", "", billingActor);
        receivables().cancelReceipt(receipt.getId(), receipt.getVersion(), "照合により組戻し", billingActor);
        money("-700", receivables().getStatement(fixture.customer.getId(), yesterday, yesterday, billingActor).getClosingBalance());
        money("700", receivables().getAgeing(fixture.customer.getId(), yesterday, billingActor).getUnallocatedReceipts());
        AccountStatement today = receivables().getStatement(fixture.customer.getId(), Dates.today(), Dates.today(), billingActor);
        money("-700", today.getOpeningBalance());
        money("700", today.getDebitTotal());
        money("0", today.getClosingBalance());
        assertEquals("RECEIPT_CANCEL", today.getLines().get(0).getType());
        Search search = new Search();
        search.setCustomerId(fixture.customer.getId());
        search.setFrom(Dates.today());
        search.setTo(Dates.today());
        Page<BillingCashEntry> movements = receivables().searchCashMovements(search, billingActor);
        assertEquals(1, movements.getTotal());
        money("0", movements.getItems().get(0).getInflow());
        money("700", movements.getItems().get(0).getOutflow());
        search.setFrom(yesterday);
        movements = receivables().searchCashMovements(search, billingActor);
        assertEquals(2, movements.getTotal());
    }

    @Test
    public void historicalInvoiceCutoffDoesNotLeakTodaysFinalizationOrAllocation() {
        Fixture fixture = fixture(2);
        ship(fixture, 2, closingDate());
        PaymentReceipt receipt = receivables().receive(uniqueCode("RCT"), fixture.customer.getId(),
                Dates.addDays(Dates.today(), -1), new BigDecimal("100"), "CASH", "", "", billingActor);
        Invoice invoice = finalizeFor(fixture, closingDate());
        receivables().allocate(receipt.getId(), receipt.getVersion(), invoice.getId(), new BigDecimal("100"), billingActor);
        ReceivablesAgeing past = receivables().getAgeing(fixture.customer.getId(), Dates.addDays(Dates.today(), -1), billingActor);
        money("0", past.getTotalOutstanding());
        money("100", past.getUnallocatedReceipts());
        money("-100", past.getNetBalance());
        ReceivablesAgeing now = receivables().getAgeing(fixture.customer.getId(), Dates.today(), billingActor);
        money("120", now.getTotalOutstanding());
        money("0", now.getUnallocatedReceipts());
    }

    @Test
    public void overdueBucketsUseDueDateNotIssueDate() {
        Fixture fixture = fixture(1);
        Customer customer = catalog.getCustomer(fixture.customer.getId(), manager);
        customer.setPaymentTermDays(0);
        catalog.saveCustomer(customer, customer.getVersion(), manager);
        ship(fixture, 1, closingDate());
        Invoice invoice = finalizeFor(fixture, closingDate());
        ReceivablesAgeing ageing = receivables().getAgeing(fixture.customer.getId(), Dates.today(), billingActor);
        money("0", ageing.getCurrent());
        money("110", ageing.getTotalOutstanding());
        assertEquals(invoice.getDueDate(), ageing.getLines().get(0).getDueDate());
        assertTrue(ageing.getLines().get(0).getDaysOverdue() > 0);
    }

    @Test
    public void receiptConcurrencyPreservesOneLedgerDocument() throws Exception {
        final Fixture fixture = fixture(0);
        final String key = uniqueCode("RCT");
        final CountDownLatch gate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> call = new Callable<Long>() {
                public Long call() throws Exception {
                    gate.await(10, TimeUnit.SECONDS);
                    return receive(fixture, key, "100").getId();
                }
            };
            Future<Long> first = executor.submit(call);
            Future<Long> second = executor.submit(call);
            gate.countDown();
            assertEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            money("100", receivables().getAgeing(fixture.customer.getId(), Dates.today(), billingActor).getUnallocatedReceipts());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void receiptValidationRejectsNegativeFutureAndUnprivilegedWrites() {
        final Fixture fixture = fixture(0);
        expect("validation.money", new Runnable() {
            public void run() { receive(fixture, uniqueCode("RCT"), "-1"); }
        });
        expect("receivables.future", new Runnable() {
            public void run() { receivables().receive(uniqueCode("RCT"), fixture.customer.getId(),
                    Dates.addDays(Dates.today(), 1), BigDecimal.ONE, "CASH", "", "", billingActor); }
        });
        expect("permission.denied", new Runnable() {
            public void run() { receivables().receive(uniqueCode("RCT"), fixture.customer.getId(),
                    Dates.today(), BigDecimal.ONE, "CASH", "", "", sales); }
        });
    }

    @Test
    public void concurrentAllocationsCannotSpendReceiptTwice() throws Exception {
        final Fixture fixture = fixture(2);
        ship(fixture, 2, closingDate());
        final Invoice invoice = finalizeFor(fixture, closingDate());
        final PaymentReceipt receipt = receive(fixture, uniqueCode("RCT"), "100");
        final CountDownLatch gate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> call = new Callable<String>() {
                public String call() throws Exception {
                    gate.await(10, TimeUnit.SECONDS);
                    try {
                        receivables().allocate(receipt.getId(), receipt.getVersion(), invoice.getId(),
                                new BigDecimal("100"), billingActor);
                        return "ALLOCATED";
                    } catch (jp.co.tsubame.wholesale.common.BusinessException ex) {
                        return ex.getCode();
                    }
                }
            };
            Future<String> first = executor.submit(call);
            Future<String> second = executor.submit(call);
            gate.countDown();
            String left = first.get(30, TimeUnit.SECONDS);
            String right = second.get(30, TimeUnit.SECONDS);
            assertTrue(("ALLOCATED".equals(left) && "concurrent.update".equals(right))
                    || ("ALLOCATED".equals(right) && "concurrent.update".equals(left)));
            money("120", billing().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
            money("0", receivables().getReceipt(receipt.getId(), billingActor).getUnallocatedAmount());
        } finally {
            executor.shutdownNow();
        }
    }

    private PaymentReceipt receive(Fixture fixture, String key, String amount) {
        return receivables().receive(key, fixture.customer.getId(), Dates.today(),
                new BigDecimal(amount), "BANK_TRANSFER", "TEST", "", billingActor);
    }
}
