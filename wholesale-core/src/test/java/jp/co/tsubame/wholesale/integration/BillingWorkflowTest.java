package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.entity.CreditMemo;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Invoice;
import jp.co.tsubame.wholesale.entity.SalesReturn;
import jp.co.tsubame.wholesale.entity.Shipment;
import org.junit.Test;
import static org.junit.Assert.*;

public class BillingWorkflowTest extends BillingTest {
    @Test
    public void draftClaimsOlderCarryoversAndCancellationAllowsCorrectedReprepare() {
        final Fixture fixture = fixture(10);
        Shipment old = ship(fixture, 2, Dates.addMonths(closingDate(), -1));
        Shipment current = ship(fixture, 3, closingDate());
        final Invoice invoice = billing().prepare(fixture.customer.getId(), closingDate(), billingActor);
        assertEquals(2, invoice.getLines().size());
        money("550", invoice.getTotalAmount());
        assertEquals(invoice.getId(), billing().prepare(fixture.customer.getId(), closingDate(), billingActor).getId());
        assertEquals(invoice.getId(), shipping().getShipment(old.getId(), warehouseActor).getInvoice().getId());
        assertEquals(invoice.getId(), shipping().getShipment(current.getId(), warehouseActor).getInvoice().getId());
        billing().cancelDraft(invoice.getId(), invoice.getVersion(), "住所訂正", billingActor);
        assertNull(shipping().getShipment(old.getId(), warehouseActor).getInvoice());
        Invoice corrected = billing().prepare(fixture.customer.getId(), closingDate(), billingActor);
        assertNotEquals(invoice.getId(), corrected.getId());
        assertEquals(2, corrected.getLines().size());
        assertEquals("VOID", billing().getInvoice(invoice.getId(), billingActor).getStatus());
    }

    @Test
    public void inactiveHeldCustomersStillBillConfirmedShipmentsAndKeepSnapshots() {
        Fixture fixture = fixture(5);
        ship(fixture, 5, closingDate());
        Customer customer = catalog.getCustomer(fixture.customer.getId(), manager);
        String name = customer.getName();
        String address = customer.getAddress();
        customer.setActive(false);
        customer.setOnHold(true);
        catalog.saveCustomer(customer, customer.getVersion(), manager);
        List<Customer> candidates = billing().listClosingCustomers(closingDate(), fixture.customer.getId() - 1, 1, batchActor);
        assertEquals(fixture.customer.getId(), candidates.get(0).getId());
        Invoice invoice = billing().prepare(fixture.customer.getId(), closingDate(), batchActor);
        customer = catalog.getCustomer(customer.getId(), manager);
        customer.setName("改称後の架空商店");
        customer.setAddress("変更後の架空住所");
        catalog.saveCustomer(customer, customer.getVersion(), manager);
        invoice = billing().finalizeInvoice(invoice.getId(), invoice.getVersion(), batchActor);
        assertEquals(name, invoice.getCustomerName());
        assertEquals(address, invoice.getBillingAddress());
        assertEquals(30, invoice.getPaymentTermDays());
        assertEquals("DOWN", invoice.getTaxRounding());
    }

    @Test
    public void rejectsFutureWrongAndRetrogradePeriods() {
        final Fixture fixture = fixture(10);
        ship(fixture, 3, closingDate());
        final Invoice invoice = finalizeFor(fixture, closingDate());
        expect("billing.future", new Runnable() {
            public void run() { billing().prepare(fixture.customer.getId(), Dates.monthEnd(Dates.addMonths(Dates.today(), 1)), billingActor); }
        });
        expect("billing.closingDate", new Runnable() {
            public void run() { billing().prepare(fixture.customer.getId(), Dates.addDays(closingDate(), -1), billingActor); }
        });
        expect("billing.retrograde", new Runnable() {
            public void run() { billing().prepare(fixture.customer.getId(), Dates.monthEnd(Dates.addMonths(closingDate(), -1)), billingActor); }
        });
        expect("billing.notDraft", new Runnable() {
            public void run() { billing().cancelDraft(invoice.getId(), invoice.getVersion(), "不可", billingActor); }
        });
        assertEquals(invoice.getId(), billing().prepare(fixture.customer.getId(), closingDate(), billingActor).getId());
    }

    @Test
    public void excludesUnconfirmedAndFutureShipments() {
        final Fixture fixture = fixture(10);
        ship(fixture, 3, Dates.addDays(closingDate(), 1));
        expect("billing.noShipments", new Runnable() {
            public void run() { billing().prepare(fixture.customer.getId(), closingDate(), billingActor); }
        });
        money("330", credit().previewExposure(fixture.customer.getId(), billingActor));
    }

    @Test
    public void receivedReturnsAttachToDraftAndCreditExactlyOnce() {
        Fixture fixture = fixture(10);
        Shipment shipment = ship(fixture, 5, closingDate());
        SalesReturn salesReturn = returnGoods(shipment, 2);
        CreditMemo pending = billing().listCredits(fixture.customer.getId(), billingActor).get(0);
        assertEquals("PENDING", pending.getStatus());
        assertNull(pending.getInvoice());
        money("220", pending.getTotalAmount());
        Invoice draft = billing().prepare(fixture.customer.getId(), closingDate(), billingActor);
        money("330", draft.getOutstandingAmount());
        billing().cancelDraft(draft.getId(), draft.getVersion(), "返品確認後再作成", billingActor);
        assertNull(billing().listCredits(fixture.customer.getId(), billingActor).get(0).getInvoice());
        Invoice invoice = finalizeFor(fixture, closingDate());
        CreditMemo applied = billing().issueReturnCredit(salesReturn, warehouseActor);
        assertEquals(pending.getId(), applied.getId());
        assertEquals("APPLIED", applied.getStatus());
        assertEquals(invoice.getId(), applied.getInvoice().getId());
        money("330", invoice.getOutstandingAmount());
        assertEquals(1, billing().listCredits(fixture.customer.getId(), billingActor).size());
        returnGoods(shipment, 3);
        money("0", billing().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
        money("550", billing().getInvoice(invoice.getId(), billingActor).getCreditedAmount());
    }

    @Test
    public void invoiceUsesRateBucketRoundingAcrossShipmentsAndReturnsTelescope() {
        Fixture fixture = fixture(5);
        fixture.product.setListPrice(new BigDecimal("5.00"));
        catalog.saveProduct(fixture.product, fixture.product.getVersion(), manager);
        Shipment first = ship(fixture, 1, closingDate());
        Shipment second = ship(fixture, 1, closingDate());
        Invoice invoice = finalizeFor(fixture, closingDate());
        money("10", invoice.getNetAmount());
        money("1", invoice.getTaxAmount());
        returnGoods(first, 1);
        money("5", billing().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
        returnGoods(second, 1);
        money("0", billing().getInvoice(invoice.getId(), billingActor).getOutstandingAmount());
        BigDecimal total = BigDecimal.ZERO;
        for (CreditMemo credit : billing().listCredits(fixture.customer.getId(), billingActor)) {
            total = total.add(credit.getTaxAmount());
        }
        money("1", total);
    }

    @Test
    public void prepareSerializesConcurrentRetries() throws Exception {
        final Fixture fixture = fixture(4);
        ship(fixture, 4, closingDate());
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> prepare = new Callable<Long>() {
                public Long call() throws Exception {
                    start.await(10, TimeUnit.SECONDS);
                    return billing().prepare(fixture.customer.getId(), closingDate(), billingActor).getId();
                }
            };
            Future<Long> first = workers.submit(prepare);
            Future<Long> second = workers.submit(prepare);
            start.countDown();
            assertEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            Search search = new Search();
            search.setCustomerId(fixture.customer.getId());
            assertEquals(1, billing().searchInvoices(search, billingActor).getTotal());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    public void financePermissionsAndOptimisticVersionsAreEnforced() {
        final Fixture fixture = fixture(2);
        ship(fixture, 2, closingDate());
        expect("permission.denied", new Runnable() {
            public void run() { billing().prepare(fixture.customer.getId(), closingDate(), sales); }
        });
        final Invoice invoice = billing().prepare(fixture.customer.getId(), closingDate(), billingActor);
        expect("concurrent.update", new Runnable() {
            public void run() { billing().finalizeInvoice(invoice.getId(), invoice.getVersion() + 1, billingActor); }
        });
        expect("permission.denied", new Runnable() {
            public void run() { billing().cancelDraft(invoice.getId(), invoice.getVersion(), "権限なし", batchActor); }
        });
        assertEquals("DRAFT", billing().getInvoice(invoice.getId(), billingActor).getStatus());
    }

    @Test
    public void closingDaysTenTwentyAndMonthEndAcceptOnlyTheirOwnSchedule() {
        Date previousMonth = Dates.addMonths(Dates.today(), -1);
        for (int closingDay : new int[] {10, 20, 31}) {
            Fixture fixture = fixture(1);
            Customer customer = catalog.getCustomer(fixture.customer.getId(), manager);
            customer.setClosingDay(closingDay);
            catalog.saveCustomer(customer, customer.getVersion(), manager);
            Date close = Dates.closingDate(previousMonth, closingDay);
            ship(fixture, 1, close);
            Invoice invoice = finalizeFor(fixture, close);
            assertEquals(close, invoice.getPeriodEnd());
            assertEquals(Dates.addDays(close, 30), invoice.getDueDate());
            money("110", invoice.getTotalAmount());
        }
    }

    @Test
    public void databaseRejectsMutationOfFinalizedInvoiceSnapshots() throws Exception {
        Fixture fixture = fixture(1);
        ship(fixture, 1, closingDate());
        Invoice invoice = finalizeFor(fixture, closingDate());
        javax.sql.DataSource source = context.getBean("dataSource", javax.sql.DataSource.class);
        try (java.sql.Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "update billing_invoice set customer_name=? where id=?")) {
                statement.setString(1, "不正な確定後書換え");
                statement.setLong(2, invoice.getId());
                try {
                    statement.executeUpdate();
                    fail("Finalized snapshot must be immutable");
                } catch (java.sql.SQLException expected) {
                    assertEquals("P0001", expected.getSQLState());
                } finally {
                    connection.rollback();
                }
            }
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "update billing_invoice_line set description=? where invoice_id=?")) {
                statement.setString(1, "不正な明細書換え");
                statement.setLong(2, invoice.getId());
                try {
                    statement.executeUpdate();
                    fail("Finalized line must be immutable");
                } catch (java.sql.SQLException expected) {
                    assertEquals("P0001", expected.getSQLState());
                } finally {
                    connection.rollback();
                }
            }
        }
        assertEquals(invoice.getCustomerName(), billing().getInvoice(invoice.getId(), billingActor).getCustomerName());
    }
}
