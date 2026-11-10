package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.DispatchConfirmationCommand;
import jp.co.tsubame.wholesale.common.DispatchManifestCommand;
import jp.co.tsubame.wholesale.common.DispatchPrintView;
import jp.co.tsubame.wholesale.common.DispatchReadiness;
import jp.co.tsubame.wholesale.common.DispatchStopCommand;
import jp.co.tsubame.wholesale.common.DispatchTrackingCommand;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.ShipmentLineInput;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.DispatchManifest;
import jp.co.tsubame.wholesale.entity.DispatchStop;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.ShipmentLine;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.DispatchManifestService;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.ShippingService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.Assert.*;

public class DispatchWorkflowTest extends PostgresTestSupport {
    @Test public void planningReordersRealAddressStopsWithoutReservingOrConfirmingAnything() {
        Fixture first = fixture(20);
        Fixture second = fixture(20);
        Shipment a = instructed(first, 10, 3, "OWN");
        Shipment b = instructed(second, 10, 4, "OWN");
        assertTrue(AopUtils.isAopProxy(manifests()));
        DispatchManifest manifest = manifests().saveDraft(warehouseActor, command(a, b));
        assertEquals("INSTRUCTED", shipping().getShipment(a.getId(), warehouseActor).getStatus());
        assertEquals(20, balance(first).getOnHand());
        assertEquals(10, balance(first).getReserved());
        DispatchManifestCommand edit = command(b, a);
        edit.setId(manifest.getId());
        edit.setExpectedVersion(manifest.getVersion());
        manifest = manifests().saveDraft(warehouseActor, edit);
        assertEquals(b.getId(), manifest.getStops().get(0).getShipment().getId());
        assertEquals(2, manifest.getStops().get(1).getStopSequence());
        assertEquals(b.getOrder().getDeliveryAddress(), manifest.getStops().get(0).getDeliveryAddress());
        DispatchPrintView print = manifests().getPrintView(warehouseActor, manifest.getId());
        assertEquals("MANUAL_STOP_ORDER_EXISTING_ORDER_ADDRESS", print.getRouteBasis());
        assertEquals(4L, print.getStops().get(0).getQuantity());
        assertEquals(b.getLines().get(0).getProductCode(), print.getStops().get(0).getItems().get(0).getProductCode());
        assertEquals(0L, print.getStops().get(0).getCurrentReturnedQuantity());
        assertTrue(print.getReadiness().isReady());
    }

    @Test public void mixedCarriersAndWarehousesCannotBePlannedTogether() {
        Fixture first = fixture(10);
        final Shipment own = instructed(first, 5, 2, "OWN");
        Fixture other = fixture(10);
        final Shipment parcel = instructed(other, 5, 2, "PARCEL");
        expect("dispatch.mixedCarrier", new Runnable() {
            @Override public void run() { manifests().saveDraft(warehouseActor, command(own, parcel)); }
        });
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(uniqueCode("DSPW"));
        warehouse.setName("Isolated dispatch warehouse");
        warehouse = catalog.saveWarehouse(warehouse, 0, manager);
        inventory().receive(uniqueCode("DSPI"), warehouse.getId(), other.product.getId(), 10,
                other.product.getStandardCost(), Dates.today(), "TEST", "", warehouseActor);
        OrderInput input = other.input(5);
        input.setWarehouseId(warehouse.getId());
        SalesOrder order = allocated(orders().saveDraft(null, 0, input, sales));
        final Shipment remote = instruct(order, 2, "OWN");
        expect("dispatch.mixedWarehouse", new Runnable() {
            @Override public void run() { manifests().saveDraft(warehouseActor, command(own, remote)); }
        });
    }

    @Test public void activeAssignmentsAreExclusiveAndCancellationReleasesOnlyPlanningClaim() {
        Fixture fixture = fixture(10);
        final Shipment source = instructed(fixture, 8, 3, "OWN");
        DispatchManifest manifest = manifests().saveDraft(warehouseActor, command(source));
        expect("dispatch.claimed", new Runnable() {
            @Override public void run() { manifests().saveDraft(warehouseActor, command(source)); }
        });
        manifest = manifests().cancel(warehouseActor, manifest.getId(), manifest.getVersion(), "Plan replaced");
        assertFalse(manifest.getStops().get(0).isActive());
        assertEquals("INSTRUCTED", shipping().getShipment(source.getId(), warehouseActor).getStatus());
        assertEquals(8, balance(fixture).getReserved());
        DispatchManifest replacement = manifests().saveDraft(warehouseActor, command(source));
        assertNotEquals(manifest.getId(), replacement.getId());
    }

    @Test public void emptyDraftsPermitRemovingStopsButCannotBeReleased() {
        Fixture fixture = fixture(10);
        Shipment source = instructed(fixture, 5, 2, "OWN");
        DispatchManifest manifest = manifests().saveDraft(warehouseActor, command(source));
        DispatchManifestCommand empty = command(source);
        empty.setId(manifest.getId());
        empty.setExpectedVersion(manifest.getVersion());
        empty.setStops(Collections.<DispatchStopCommand>emptyList());
        final DispatchManifest draft = manifests().saveDraft(warehouseActor, empty);
        assertTrue(draft.getStops().isEmpty());
        assertFalse(manifests().getReadiness(warehouseActor, draft.getId()).isReady());
        expect("dispatch.notReady", new Runnable() {
            @Override public void run() { manifests().release(warehouseActor, draft.getId(), draft.getVersion()); }
        });
        assertNotNull(manifests().saveDraft(warehouseActor, command(source)).getId());
    }

    @Test public void salesCancellationRemainsPossibleAndInvalidatesThePlanExplicitly() {
        Fixture fixture = fixture(10);
        Shipment source = instructed(fixture, 6, 3, "OWN");
        DispatchManifest manifest = manifests().saveDraft(warehouseActor, command(source));
        manifest = manifests().release(warehouseActor, manifest.getId(), manifest.getVersion());
        SalesOrder order = orders().getOrder(source.getOrder().getId(), sales);
        orders().cancel(order.getId(), order.getVersion(), "Customer cancelled", sales);
        DispatchReadiness readiness = manifests().getReadiness(warehouseActor, manifest.getId());
        assertFalse(readiness.isReady());
        assertTrue(readiness.getIssues().size() > 0);
        final DispatchManifest released = manifest;
        expect("dispatch.notReady", new Runnable() {
            @Override public void run() {
                manifests().confirm(warehouseActor, released.getId(), released.getVersion(), confirmation(released));
            }
        });
        assertEquals(10, balance(fixture).getOnHand());
        assertEquals(0, balance(fixture).getReserved());
        manifest = manifests().replan(warehouseActor, manifest.getId(), manifest.getVersion(), "Remove cancelled order");
        manifest = manifests().cancel(warehouseActor, manifest.getId(), manifest.getVersion(), "No remaining stops");
        assertEquals("CANCELLED", manifest.getStatus());
    }

    @Test public void outsideSharedConfirmationDoesNotPretendTheManifestDispatched() {
        Fixture fixture = fixture(10);
        Shipment source = instructed(fixture, 6, 3, "OWN");
        final DispatchManifest draft = manifests().saveDraft(warehouseActor, command(source));
        shipping().confirm(source.getId(), source.getVersion(), Dates.today(), "MANUAL-INTERNAL", warehouseActor);
        DispatchReadiness readiness = manifests().getReadiness(warehouseActor, draft.getId());
        boolean confirmedIssue = false;
        for (DispatchReadiness.Issue issue : readiness.getIssues()) {
            if ("dispatch.source.CONFIRMED".equals(issue.getCode())) { confirmedIssue = true; }
        }
        assertTrue(confirmedIssue);
        expect("dispatch.notReady", new Runnable() {
            @Override public void run() { manifests().release(warehouseActor, draft.getId(), draft.getVersion()); }
        });
        assertEquals("DRAFT", manifests().get(warehouseActor, draft.getId()).getStatus());
    }

    @Test public void sourceFingerprintDetectsChangedLinesEvenWhenShipmentHeaderVersionIsUnchanged() {
        Fixture fixture = fixture(10);
        final Shipment source = instructed(fixture, 6, 3, "OWN");
        final DispatchManifest draft = manifests().saveDraft(warehouseActor, command(source));
        transaction(new TransactionCallback<Void>() {
            @Override public Void doInTransaction(TransactionStatus status) {
                dao().get(ShipmentLine.class, source.getLines().get(0).getId()).setQuantity(2);
                return null;
            }
        });
        assertEquals(source.getVersion(), shipping().getShipment(source.getId(), warehouseActor).getVersion());
        assertFalse(manifests().getReadiness(warehouseActor, draft.getId()).isReady());
        expect("dispatch.notReady", new Runnable() {
            @Override public void run() { manifests().release(warehouseActor, draft.getId(), draft.getVersion()); }
        });
    }

    @Test public void confirmedManifestUsesSharedPartialOrderStockAndLeavesPriceAndInvoiceEligibilityIntact() {
        Fixture fixture = fixture(20);
        SalesOrder order = allocated(fixture.order(10));
        Shipment first = instruct(order, 3, "OWN");
        order = orders().getOrder(order.getId(), sales);
        Shipment second = instruct(order, 2, "OWN");
        DispatchManifest manifest = manifests().saveDraft(warehouseActor, command(second, first));
        manifest = manifests().release(warehouseActor, manifest.getId(), manifest.getVersion());
        manifest = manifests().confirm(warehouseActor, manifest.getId(), manifest.getVersion(), confirmation(manifest));
        assertEquals("DISPATCHED", manifest.getStatus());
        assertEquals(15, balance(fixture).getOnHand());
        assertEquals(5, balance(fixture).getReserved());
        order = orders().getOrder(order.getId(), sales);
        assertEquals("PART_SHIPPED", order.getStatus());
        assertEquals(new BigDecimal("100.00"), order.getLines().get(0).getUnitPrice());
        for (DispatchStop stop : manifest.getStops()) {
            Shipment confirmed = shipping().getShipment(stop.getShipment().getId(), billingActor);
            assertEquals("CONFIRMED", confirmed.getStatus());
            assertNull(confirmed.getInvoice());
            assertEquals("MANUAL-" + confirmed.getId(), confirmed.getTrackingNumber());
            assertFalse(stop.isActive());
        }
        final DispatchManifest dispatched = manifest;
        expect("dispatch.state", new Runnable() {
            @Override public void run() { manifests().cancel(warehouseActor, dispatched.getId(), dispatched.getVersion(), "Too late"); }
        });
    }

    @Test public void missingManualTrackingFutureDatesAndWrongRolesFailBeforeAnyConfirmation() {
        Fixture fixture = fixture(10);
        Shipment source = instructed(fixture, 5, 2, "PARCEL");
        DispatchManifest manifest = manifests().saveDraft(warehouseActor, command(source));
        final DispatchManifest released = manifests().release(warehouseActor, manifest.getId(), manifest.getVersion());
        final DispatchConfirmationCommand missing = confirmation(released);
        missing.getTracking().get(0).setTrackingReference(" ");
        expect("validation.required", new Runnable() {
            @Override public void run() { manifests().confirm(warehouseActor, released.getId(), released.getVersion(), missing); }
        });
        final DispatchConfirmationCommand future = confirmation(released);
        future.setDispatchDate(Dates.addDays(Dates.today(), 1));
        expect("dispatch.date", new Runnable() {
            @Override public void run() { manifests().confirm(warehouseActor, released.getId(), released.getVersion(), future); }
        });
        expect("permission.denied", new Runnable() {
            @Override public void run() { manifests().confirm(sales, released.getId(), released.getVersion(), confirmation(released)); }
        });
        expect("concurrent.update", new Runnable() {
            @Override public void run() { manifests().cancel(warehouseActor, released.getId(), -1, "Stale"); }
        });
        assertEquals("INSTRUCTED", shipping().getShipment(source.getId(), warehouseActor).getStatus());
        assertEquals(10, balance(fixture).getOnHand());
    }

    @Test public void laterConfirmationFailureRollsBackAnEarlierActualSharedConfirmation() {
        final Fixture first = fixture(10);
        final Fixture second = fixture(10);
        Shipment a = instructed(first, 5, 2, "OWN");
        Shipment b = instructed(second, 5, 2, "OWN");
        DispatchManifest draft = manifests().saveDraft(warehouseActor, command(a, b));
        final DispatchManifest released = manifests().release(warehouseActor, draft.getId(), draft.getVersion());
        final ShippingService actual = shipping();
        final AtomicInteger confirmations = new AtomicInteger();
        ShippingService failSecond = new ShippingService() {
            @Override public Shipment confirm(Long id, int version, Date date, String reference, Actor actor) {
                if (confirmations.get() == 1) { throw new BusinessException("dispatch.testLaterFailure", "Second shipment failed"); }
                Shipment result = actual.confirm(id, version, date, reference, actor);
                assertEquals("CONFIRMED", dao().session().createSQLQuery("select status from shipping_shipment where id=:id")
                        .setLong("id", id).uniqueResult());
                confirmations.incrementAndGet();
                return result;
            }
        };
        manifests().setShippingService(failSecond);
        try {
            expect("dispatch.testLaterFailure", new Runnable() {
                @Override public void run() {
                    manifests().confirm(warehouseActor, released.getId(), released.getVersion(), confirmation(released));
                }
            });
        } finally { manifests().setShippingService(actual); }
        assertEquals(1, confirmations.get());
        assertEquals("RELEASED", manifests().get(warehouseActor, released.getId()).getStatus());
        assertEquals("INSTRUCTED", shipping().getShipment(a.getId(), warehouseActor).getStatus());
        assertEquals("INSTRUCTED", shipping().getShipment(b.getId(), warehouseActor).getStatus());
        assertEquals(10, balance(first).getOnHand());
        assertEquals(10, balance(second).getOnHand());
        assertEquals(5, balance(first).getReserved());
        assertEquals(5, balance(second).getReserved());
    }

    @Test public void concurrentManifestClaimsHaveExactlyOneWinner() throws Exception {
        Fixture fixture = fixture(10);
        final Shipment source = instructed(fixture, 5, 2, "OWN");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<String> action = new Callable<String>() {
                @Override public String call() throws Exception {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try { manifests().saveDraft(warehouseActor, command(source)); return "CREATED"; }
                    catch (BusinessException failure) { return failure.getCode(); }
                }
            };
            Future<String> first = executor.submit(action);
            Future<String> second = executor.submit(action);
            start.countDown();
            List<String> outcomes = Arrays.asList(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(outcomes, "CREATED"));
            assertEquals(1, Collections.frequency(outcomes, "dispatch.claimed"));
            assertEquals(1L, transaction(new TransactionCallback<Long>() {
                @Override public Long doInTransaction(TransactionStatus status) {
                    return dao().count("select count(s.id) from DispatchStop s where s.shipment.id=:shipment and s.active=true",
                            WholesaleDao.params("shipment", source.getId()));
                }
            }).longValue());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test public void candidateAndManifestSearchesHaveActualScopedPagination() {
        Fixture fixture = fixture(20);
        Shipment source = instructed(fixture, 10, 2, "OWN");
        Search search = new Search();
        search.setCustomerId(fixture.customer.getId());
        search.setSize(1);
        assertEquals(1L, manifests().listCandidates(warehouseActor, search).getTotal());
        DispatchManifest manifest = manifests().saveDraft(warehouseActor, command(source));
        assertEquals(0L, manifests().listCandidates(warehouseActor, search).getTotal());
        assertEquals(1L, manifests().search(warehouseActor, search).getTotal());
        assertEquals(manifest.getId(), manifests().search(warehouseActor, search).getItems().get(0).getId());
    }

    @Test public void databaseUniquenessAlsoRejectsBypassedActiveShipmentClaims() {
        Fixture fixture = fixture(10);
        Shipment source = instructed(fixture, 5, 2, "OWN");
        final DispatchManifest first = manifests().saveDraft(warehouseActor, command(source));
        DispatchManifestCommand empty = command(source);
        empty.setStops(Collections.<DispatchStopCommand>emptyList());
        final DispatchManifest second = manifests().saveDraft(warehouseActor, empty);
        try {
            transaction(new TransactionCallback<Void>() {
                @Override public Void doInTransaction(TransactionStatus status) {
                    dao().session().createSQLQuery("insert into dispatch_stop "
                            + "(id,version,manifest_id,shipment_id,stop_sequence,source_shipment_version,source_order_version,"
                            + "source_fingerprint,customer_name,delivery_address,note,active) "
                            + "select nextval('wholesale_seq'),0,:second,shipment_id,1,source_shipment_version,source_order_version,"
                            + "source_fingerprint,customer_name,delivery_address,note,true from dispatch_stop where manifest_id=:first")
                            .setLong("first", first.getId()).setLong("second", second.getId()).executeUpdate();
                    return null;
                }
            });
            fail("The active shipment partial unique index must reject a bypassed claim");
        } catch (ConstraintViolationException failure) {
            assertEquals("dispatch_stop_active_shipment_idx", failure.getConstraintName());
        }
        assertTrue(manifests().get(warehouseActor, second.getId()).getStops().isEmpty());
    }

    private DispatchManifestService manifests() { return service("dispatchManifestService", DispatchManifestService.class); }
    private ShippingService shipping() { return service("shippingService", ShippingService.class); }
    private OrderService orders() { return service("orderService", OrderService.class); }
    private InventoryService inventory() { return service("inventoryService", InventoryService.class); }
    private WholesaleDao dao() { return context.getBean("wholesaleDao", WholesaleDao.class); }
    private SalesOrder allocated(SalesOrder draft) {
        SalesOrder order = orders().submit(draft.getId(), draft.getVersion(), sales);
        order = orders().approve(order.getId(), order.getVersion(), manager);
        return orders().allocate(order.getId(), order.getVersion(), warehouseActor);
    }
    private Shipment instructed(Fixture fixture, int ordered, int shipped, String carrier) {
        return instruct(allocated(fixture.order(ordered)), shipped, carrier);
    }
    private Shipment instruct(SalesOrder order, int quantity, String carrier) {
        ShipmentLineInput line = new ShipmentLineInput();
        line.setOrderLineId(order.getLines().get(0).getId());
        line.setQuantity(quantity);
        return shipping().instruct(order.getId(), order.getVersion(), Dates.today(), carrier, "", Arrays.asList(line), warehouseActor);
    }
    private DispatchManifestCommand command(Shipment... shipments) {
        DispatchManifestCommand command = new DispatchManifestCommand();
        command.setWarehouseId(shipments[0].getWarehouse().getId());
        command.setCarrier(shipments[0].getCarrier());
        command.setPlannedDispatchDate(Dates.today());
        for (Shipment shipment : shipments) {
            DispatchStopCommand stop = new DispatchStopCommand();
            stop.setShipmentId(shipment.getId());
            stop.setExpectedShipmentVersion(shipment.getVersion());
            command.getStops().add(stop);
        }
        return command;
    }
    private DispatchConfirmationCommand confirmation(DispatchManifest manifest) {
        DispatchConfirmationCommand command = new DispatchConfirmationCommand();
        command.setDispatchDate(Dates.today());
        for (DispatchStop stop : manifest.getStops()) {
            DispatchTrackingCommand tracking = new DispatchTrackingCommand();
            tracking.setShipmentId(stop.getShipment().getId());
            tracking.setTrackingReference("MANUAL-" + stop.getShipment().getId());
            command.getTracking().add(tracking);
        }
        return command;
    }
    private StockBalance balance(Fixture fixture) {
        Search search = new Search();
        search.setWarehouseId(21L);
        search.setText(fixture.product.getCode());
        return inventory().searchStock(search, warehouseActor).getItems().get(0);
    }
    private <T> T transaction(TransactionCallback<T> callback) {
        return new TransactionTemplate(context.getBean("transactionManager", PlatformTransactionManager.class)).execute(callback);
    }
    private void expect(String code, Runnable action) {
        try { action.run(); fail(code); } catch (BusinessException failure) { assertEquals(code, failure.getCode()); }
    }
}
