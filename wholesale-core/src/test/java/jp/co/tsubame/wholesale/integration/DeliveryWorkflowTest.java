package jp.co.tsubame.wholesale.integration;

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.DeliveryAttemptCommand;
import jp.co.tsubame.wholesale.common.DeliverySearch;
import jp.co.tsubame.wholesale.common.DeliverySummary;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.ReturnLineInput;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.ShipmentLineInput;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.DeliveryAttempt;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesReturn;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.service.DeliveryAttemptService;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.ShippingService;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.Assert.*;

public class DeliveryWorkflowTest extends PostgresTestSupport {
    @Test public void manualOutcomesLeavePhysicalStockPricesAndInvoiceEligibilityUnchanged() {
        final Fixture fixture = fixture(20);
        Shipment shipment = shipped(fixture, 10, 4);
        int sourceVersion = shipment.getVersion();
        StockBalance before = balance(fixture);
        long movements = movementCount(fixture.product.getId());
        DeliveryAttempt failed = deliveries().record(warehouseActor, command(shipment, null, "FAILED", 1));
        DeliveryAttempt rescheduled = deliveries().record(sales, command(shipment, failed.getId(), "RESCHEDULED", 2));
        DeliveryAttempt delivered = deliveries().record(warehouseActor, command(shipment, rescheduled.getId(), "DELIVERED", 3));
        assertTrue(AopUtils.isAopProxy(deliveries()));
        DeliverySummary summary = deliveries().getSummary(warehouseActor, shipment.getId(), null);
        assertEquals("DELIVERED", summary.getOutcome());
        assertEquals(delivered.getId(), summary.getLatestEventId());
        assertEquals(4L, summary.getShippedQuantity());
        assertEquals(0L, summary.getCurrentReturnedQuantity());
        assertEquals("MANUAL_UNVERIFIED_REPORT", summary.getOutcomeBasis());
        assertEquals(before.getOnHand(), balance(fixture).getOnHand());
        assertEquals(before.getReserved(), balance(fixture).getReserved());
        assertEquals(movements, movementCount(fixture.product.getId()));
        Shipment unchanged = shipping().getShipment(shipment.getId(), billingActor);
        assertEquals(sourceVersion, unchanged.getVersion());
        assertEquals("CONFIRMED", unchanged.getStatus());
        assertNull(unchanged.getInvoice());
        assertEquals(shipment.getTotalAmount(), unchanged.getTotalAmount());
        assertEquals(3L, deliveries().listHistory(warehouseActor, shipment.getId(), new DeliverySearch()).getTotal());
    }

    @Test public void correctionAndReversalAreAppendOnlyAndAsOfKnowledgeIsPreserved() {
        Fixture fixture = fixture(10);
        Shipment shipment = shipped(fixture, 6, 4);
        DeliveryAttempt failed = deliveries().record(warehouseActor, command(shipment, null, "FAILED", 1));
        DeliveryAttempt delivered = deliveries().record(warehouseActor, command(shipment, failed.getId(), "DELIVERED", 2));
        DeliveryAttemptCommand corrected = command(shipment, delivered.getId(), "RESCHEDULED", 2);
        DeliveryAttempt correction = deliveries().correct(manager, delivered.getId(), corrected, "Wrong outcome selected");
        assertEquals("CORRECTION", correction.getEventType());
        assertEquals(delivered.getId(), correction.getSupersedesId());
        assertEquals("RESCHEDULED", deliveries().getSummary(manager, shipment.getId(), null).getOutcome());
        DeliveryAttempt reversal = deliveries().reverse(manager, correction.getId(), correction.getId(),
                uniqueCode("REV"), "Evidence withdrawn");
        assertEquals("REVERSAL", reversal.getEventType());
        assertEquals("REVERSED", reversal.getOutcome());
        assertEquals("FAILED", deliveries().getSummary(manager, shipment.getId(), null).getOutcome());
        assertEquals("DELIVERED", deliveries().getSummary(manager, shipment.getId(), delivered.getRecordedAt()).getOutcome());
        assertEquals("RESCHEDULED", deliveries().getSummary(manager, shipment.getId(), correction.getRecordedAt()).getOutcome());
        assertEquals("FAILED", deliveries().getSummary(manager, shipment.getId(), failed.getRecordedAt()).getOutcome());
        Page<DeliveryAttempt> history = deliveries().listHistory(manager, shipment.getId(), new DeliverySearch());
        assertEquals(4L, history.getTotal());
        assertEquals("DELIVERED", history.getItems().get(2).getOutcome());
        assertEquals(0, history.getItems().get(2).getVersion());
        assertEquals("CURRENT_RECEIVED_RETURNS_NOT_UNDELIVERED_QUANTITY",
                deliveries().getSummary(manager, shipment.getId(), delivered.getRecordedAt()).getReturnQuantityBasis());
    }

    @Test public void backdatedManualEntryDoesNotReplaceALaterActualAttempt() {
        Fixture fixture = fixture(10);
        Shipment shipment = shipped(fixture, 5, 3);
        DeliveryAttempt delivered = deliveries().record(warehouseActor, command(shipment, null, "DELIVERED", 20));
        DeliveryAttempt earlier = deliveries().record(warehouseActor, command(shipment, delivered.getId(), "FAILED", 10));
        DeliverySummary summary = deliveries().getSummary(warehouseActor, shipment.getId(), null);
        assertEquals(earlier.getId(), summary.getLatestEventId());
        assertEquals(delivered.getId(), summary.getEffectiveAttempt().getId());
        assertEquals("DELIVERED", summary.getOutcome());
        final DeliveryAttemptCommand newer = command(shipment, earlier.getId(), "FAILED", 30);
        expect("delivery.alreadyDelivered", new Runnable() {
            @Override public void run() { deliveries().record(warehouseActor, newer); }
        });
    }

    @Test public void requestKeysAreIdempotentButChangedPayloadsAndStaleCursorsAreRejected() {
        Fixture fixture = fixture(10);
        final Shipment shipment = shipped(fixture, 5, 3);
        final DeliveryAttemptCommand command = command(shipment, null, "FAILED", 1);
        DeliveryAttempt first = deliveries().record(warehouseActor, command);
        assertEquals(first.getId(), deliveries().record(warehouseActor, command).getId());
        command.setReason("Different evidence");
        expect("idempotency.conflict", new Runnable() {
            @Override public void run() { deliveries().record(warehouseActor, command); }
        });
        expect("delivery.concurrent", new Runnable() {
            @Override public void run() { deliveries().record(warehouseActor, command(shipment, null, "FAILED", 2)); }
        });
        assertEquals(1L, deliveries().listHistory(manager, shipment.getId(), new DeliverySearch()).getTotal());
    }

    @Test public void directEventReadReturnsInitializedShipmentAndPreservesHistory() {
        Fixture fixture = fixture(10);
        Shipment shipment = shipped(fixture, 5, 3);
        DeliveryAttempt recorded = deliveries().record(warehouseActor, command(shipment, null, "FAILED", 1));
        DeliveryAttempt loaded = deliveries().getAttempt(manager, recorded.getId());
        assertEquals(recorded.getId(), loaded.getId());
        assertEquals(recorded.getEvidenceReference(), loaded.getEvidenceReference());
        assertEquals(shipment.getId(), loaded.getShipment().getId());
        assertFalse(loaded.getShipment().getLines().isEmpty());
        assertEquals(1L, deliveries().listHistory(manager, shipment.getId(), new DeliverySearch()).getTotal());
    }

    @Test public void withdrawingTheOnlyCorrectionDoesNotResurrectSupersededEvidence() {
        Fixture fixture = fixture(10);
        Shipment shipment = shipped(fixture, 5, 3);
        DeliveryAttempt original = deliveries().record(warehouseActor, command(shipment, null, "FAILED", 1));
        DeliveryAttempt correction = deliveries().correct(manager, original.getId(),
                command(shipment, original.getId(), "DELIVERED", 1), "Original report was incorrect");
        DeliveryAttempt reversal = deliveries().reverse(manager, correction.getId(), correction.getId(),
                uniqueCode("REV"), "Replacement evidence withdrawn");
        assertEquals("NONE", deliveries().getSummary(manager, shipment.getId(), null).getOutcome());
        assertEquals("FAILED", deliveries().getSummary(manager, shipment.getId(), original.getRecordedAt()).getOutcome());
        assertEquals("DELIVERED", deliveries().getSummary(manager, shipment.getId(), correction.getRecordedAt()).getOutcome());
        assertEquals(reversal.getId(), deliveries().getSummary(manager, shipment.getId(), null).getLatestEventId());
        DeliverySearch search = new DeliverySearch();
        search.setShipmentId(shipment.getId());
        assertEquals(0L, deliveries().searchQueue(manager, search).getTotal());
        assertEquals(3L, deliveries().listHistory(manager, shipment.getId(), search).getTotal());
    }

    @Test public void correctionsRequireManagerReasonSameShipmentAndUnsupersededTarget() {
        Fixture fixture = fixture(10);
        final Shipment shipment = shipped(fixture, 5, 3);
        final DeliveryAttempt original = deliveries().record(warehouseActor, command(shipment, null, "FAILED", 1));
        final DeliveryAttemptCommand replacement = command(shipment, original.getId(), "DELIVERED", 1);
        expect("permission.denied", new Runnable() {
            @Override public void run() { deliveries().correct(warehouseActor, original.getId(), replacement, "Cannot approve"); }
        });
        expect("validation.required", new Runnable() {
            @Override public void run() { deliveries().correct(manager, original.getId(), replacement, " "); }
        });
        Fixture other = fixture(10);
        Shipment another = shipped(other, 5, 3);
        final DeliveryAttemptCommand foreign = command(another, null, "FAILED", 2);
        expect("delivery.target", new Runnable() {
            @Override public void run() { deliveries().correct(manager, original.getId(), foreign, "Wrong shipment"); }
        });
        final DeliveryAttempt corrected = deliveries().correct(manager, original.getId(), replacement, "Evidence corrected");
        expect("delivery.superseded", new Runnable() {
            @Override public void run() {
                deliveries().reverse(manager, original.getId(), corrected.getId(), uniqueCode("DUP"), "Already superseded");
            }
        });
        assertEquals(2L, deliveries().listHistory(manager, shipment.getId(), new DeliverySearch()).getTotal());
    }

    @Test public void fullReturnsRejectNewDeliveryButPartialReturnsRemainSeparateManualInformation() {
        Fixture fixture = fixture(10);
        final Shipment shipment = shipped(fixture, 6, 4);
        returnQuantity(shipment, 1);
        DeliveryAttempt first = deliveries().record(warehouseActor, command(shipment, null, "FAILED", 1));
        DeliveryAttempt delivered = deliveries().record(warehouseActor, command(shipment, first.getId(), "DELIVERED", 2));
        DeliverySummary partial = deliveries().getSummary(manager, shipment.getId(), null);
        assertEquals(4L, partial.getShippedQuantity());
        assertEquals(1L, partial.getCurrentReturnedQuantity());
        assertEquals("DELIVERED", partial.getOutcome());
        returnQuantity(shipping().getShipment(shipment.getId(), warehouseActor), 3);
        final DeliveryAttemptCommand correction = command(shipment, delivered.getId(), "DELIVERED", 2);
        final Long target = delivered.getId();
        expect("delivery.fullyReturned", new Runnable() {
            @Override public void run() { deliveries().correct(manager, target, correction, "Cannot infer delivery"); }
        });
        assertEquals(4L, deliveries().getSummary(manager, shipment.getId(), null).getCurrentReturnedQuantity());
        assertEquals("DELIVERED", deliveries().getSummary(manager, shipment.getId(), null).getOutcome());
    }

    @Test public void fullyReturnedShipmentWithoutHistoryCannotBeNewlyMarkedDelivered() {
        Fixture fixture = fixture(10);
        final Shipment shipment = shipped(fixture, 5, 3);
        returnQuantity(shipment, 3);
        expect("delivery.fullyReturned", new Runnable() {
            @Override public void run() { deliveries().record(warehouseActor, command(shipment, null, "DELIVERED", 1)); }
        });
        assertEquals("NONE", deliveries().getSummary(manager, shipment.getId(), null).getOutcome());
        assertEquals(0L, deliveries().listHistory(manager, shipment.getId(), new DeliverySearch()).getTotal());
    }

    @Test public void queueUsesLatestSurvivingOutcomesAndBusinessDatesNotRecordingDates() {
        Fixture fixture = fixture(20);
        Shipment first = shipped(fixture, 6, 3);
        Shipment second = shipped(fixture, 6, 3);
        DeliveryAttempt failed = deliveries().record(warehouseActor, command(first, null, "FAILED", 1));
        DeliveryAttempt rescheduled = deliveries().record(warehouseActor, command(second, null, "RESCHEDULED", 2));
        DeliverySearch search = new DeliverySearch();
        search.setCustomerId(fixture.customer.getId());
        search.setFrom(Dates.addDays(Dates.today(), -1));
        search.setTo(Dates.addDays(Dates.today(), -1));
        search.setSize(1);
        Page<DeliverySummary> page = deliveries().searchQueue(warehouseActor, search);
        assertEquals(2L, page.getTotal());
        assertEquals(1, page.getItems().size());
        assertEquals(first.getId(), page.getItems().get(0).getShipmentId());
        search.setPage(2);
        assertEquals(second.getId(), deliveries().searchQueue(warehouseActor, search).getItems().get(0).getShipmentId());
        search.setPage(1);
        search.setDueOnOrBefore(Dates.addDays(Dates.today(), -1));
        assertEquals(1L, deliveries().searchQueue(warehouseActor, search).getTotal());
        search.setDueOnOrBefore(null);
        deliveries().record(warehouseActor, command(second, rescheduled.getId(), "DELIVERED", 3));
        assertEquals(1L, deliveries().searchQueue(warehouseActor, search).getTotal());
        search.setAsOfRecordedAt(rescheduled.getRecordedAt());
        assertEquals(2L, deliveries().searchQueue(warehouseActor, search).getTotal());
        search.setStatus("RESCHEDULED");
        assertEquals(1L, deliveries().searchQueue(warehouseActor, search).getTotal());
        search.setAsOfRecordedAt(failed.getRecordedAt());
        assertEquals(0L, deliveries().searchQueue(warehouseActor, search).getTotal());
    }

    @Test public void unconfirmedShipmentFutureTimestampAndBadRescheduleNeverCreateEvents() {
        Fixture fixture = fixture(10);
        final Shipment instruction = instructed(fixture, 5, 3);
        expect("delivery.notConfirmed", new Runnable() {
            @Override public void run() { deliveries().record(warehouseActor, command(instruction, null, "FAILED", 1)); }
        });
        final Shipment shipment = shipping().confirm(instruction.getId(), instruction.getVersion(),
                Dates.addDays(Dates.today(), -1), "MANUAL-REF", warehouseActor);
        final DeliveryAttemptCommand future = command(shipment, null, "FAILED", 1);
        future.setAttemptAt(new Date(System.currentTimeMillis() + 60000));
        expect("delivery.attemptAt", new Runnable() {
            @Override public void run() { deliveries().record(warehouseActor, future); }
        });
        final DeliveryAttemptCommand reschedule = command(shipment, null, "RESCHEDULED", 1);
        reschedule.setNextAttemptDate(Dates.addDays(Dates.today(), -1));
        expect("delivery.nextDate", new Runnable() {
            @Override public void run() { deliveries().record(warehouseActor, reschedule); }
        });
        final DeliveryAttemptCommand command = command(shipment, null, "FAILED", 1);
        expect("permission.denied", new Runnable() {
            @Override public void run() { deliveries().record(billingActor, command); }
        });
        assertEquals(0L, deliveries().listHistory(manager, shipment.getId(), new DeliverySearch()).getTotal());
    }

    @Test public void concurrentIdenticalManualReportsAppendExactlyOnce() throws Exception {
        Fixture fixture = fixture(10);
        final Shipment shipment = shipped(fixture, 5, 3);
        final DeliveryAttemptCommand command = command(shipment, null, "FAILED", 1);
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<DeliveryAttempt> action = new Callable<DeliveryAttempt>() {
                @Override public DeliveryAttempt call() throws Exception {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return deliveries().record(warehouseActor, command);
                }
            };
            Future<DeliveryAttempt> first = executor.submit(action);
            Future<DeliveryAttempt> second = executor.submit(action);
            start.countDown();
            assertEquals(first.get(30, TimeUnit.SECONDS).getId(), second.get(30, TimeUnit.SECONDS).getId());
            assertEquals(1L, deliveries().listHistory(manager, shipment.getId(), new DeliverySearch()).getTotal());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private DeliveryAttemptService deliveries() { return service("deliveryAttemptService", DeliveryAttemptService.class); }
    private ShippingService shipping() { return service("shippingService", ShippingService.class); }
    private OrderService orders() { return service("orderService", OrderService.class); }
    private InventoryService inventory() { return service("inventoryService", InventoryService.class); }
    private Shipment instructed(Fixture fixture, int ordered, int quantity) {
        SalesOrder order = fixture.order(ordered);
        order = orders().submit(order.getId(), order.getVersion(), sales);
        order = orders().approve(order.getId(), order.getVersion(), manager);
        order = orders().allocate(order.getId(), order.getVersion(), warehouseActor);
        ShipmentLineInput line = new ShipmentLineInput();
        line.setOrderLineId(order.getLines().get(0).getId());
        line.setQuantity(quantity);
        return shipping().instruct(order.getId(), order.getVersion(), Dates.addDays(Dates.today(), -1),
                "OWN", "", Arrays.asList(line), warehouseActor);
    }
    private Shipment shipped(Fixture fixture, int ordered, int quantity) {
        Shipment shipment = instructed(fixture, ordered, quantity);
        return shipping().confirm(shipment.getId(), shipment.getVersion(), Dates.addDays(Dates.today(), -1),
                uniqueCode("MANUAL"), warehouseActor);
    }
    private DeliveryAttemptCommand command(Shipment shipment, Long cursor, String outcome, int minute) {
        DeliveryAttemptCommand command = new DeliveryAttemptCommand();
        command.setShipmentId(shipment.getId());
        command.setExpectedLatestEventId(cursor);
        command.setRequestKey(uniqueCode("DELE"));
        command.setAttemptAt(new Date(Dates.addDays(Dates.today(), -1).getTime() + 43200000L + minute * 60000L));
        command.setOutcome(outcome);
        command.setReportingCompany("SYNTHETIC-CARRIER-COMPANY");
        command.setEvidenceReference("SYNTHETIC-MANUAL-REPORT-" + minute);
        command.setReason("Synthetic manual outcome reason");
        if ("RESCHEDULED".equals(outcome)) { command.setNextAttemptDate(Dates.today()); }
        return command;
    }
    private void returnQuantity(Shipment shipment, int quantity) {
        ReturnLineInput line = new ReturnLineInput();
        line.setShipmentLineId(shipment.getLines().get(0).getId());
        line.setQuantity(quantity);
        line.setRestock(true);
        SalesReturn returned = shipping().requestReturn(shipment.getId(), "CUSTOMER_CHANGE", "", Arrays.asList(line), sales);
        returned = shipping().approveReturn(returned.getId(), returned.getVersion(), manager);
        shipping().receiveReturn(returned.getId(), returned.getVersion(), Dates.today(), warehouseActor);
    }
    private StockBalance balance(Fixture fixture) {
        Search search = new Search();
        search.setWarehouseId(21L);
        search.setText(fixture.product.getCode());
        return inventory().searchStock(search, warehouseActor).getItems().get(0);
    }
    private long movementCount(final Long productId) {
        return new TransactionTemplate(context.getBean("transactionManager", PlatformTransactionManager.class))
                .execute(new TransactionCallback<Long>() {
                    @Override public Long doInTransaction(TransactionStatus status) {
                        return context.getBean("wholesaleDao", WholesaleDao.class).count(
                                "select count(m.id) from StockMovement m where m.balance.product.id=:product",
                                WholesaleDao.params("product", productId));
                    }
                });
    }
    private void expect(String code, Runnable action) {
        try { action.run(); fail(code); } catch (BusinessException failure) { assertEquals(code, failure.getCode()); }
    }
}
