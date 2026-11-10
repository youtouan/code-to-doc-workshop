package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.OrderAmendmentCommand;
import jp.co.tsubame.wholesale.common.OrderAmendmentLineCommand;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.common.OrderLineInput;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.ShipmentLineInput;
import jp.co.tsubame.wholesale.common.StockCountCommand;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.OrderAmendment;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.entity.StockCount;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.OrderAmendmentService;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.ShippingService;
import jp.co.tsubame.wholesale.service.StockCountService;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import static org.junit.Assert.*;

public class OrderAmendmentWorkflowTest extends PostgresTestSupport {
    @Test
    public void requestsAreAuditedProposalsAndDoNotMutateOrdersOrReservations() {
        Fixture fixture = fixture(20);
        SalesOrder order = allocated(fixture.order(10));
        OrderAmendment amendment = amendments().request(command(order, 6), sales);
        assertTrue(AopUtils.isAopProxy(amendments()));
        assertEquals("REQUESTED", amendment.getStatus());
        assertEquals(sales.getUserId(), amendment.getRequestedById());
        assertEquals(order.getVersion(), amendment.getBaseOrderVersion());
        assertEquals(64, amendment.getSourceFingerprint().length());
        assertEquals(new BigDecimal("-440.00"), amendment.getExposureDelta());
        assertEquals(10, orders().getOrder(order.getId(), sales).getLines().get(0).getQuantity());
        assertEquals(10, balance(fixture.product).getReserved());
    }

    @Test
    public void reductionsReleaseOnlyExcessReservationAndRepeatApprovalIsIdempotent() {
        Fixture fixture = fixture(20);
        SalesOrder order = allocated(fixture.order(10));
        OrderAmendment requested = amendments().request(command(order, 6), sales);
        OrderAmendment applied = amendments().approve(requested.getId(), requested.getVersion(), manager);
        assertEquals("APPLIED", applied.getStatus());
        assertEquals(6, applied.getOrder().getLines().get(0).getQuantity());
        assertEquals("ALLOCATED", applied.getOrder().getStatus());
        assertEquals(new BigDecimal("660.00"), applied.getOrder().getTotalAmount());
        assertEquals(20, balance(fixture.product).getOnHand());
        assertEquals(6, balance(fixture.product).getReserved());
        assertEquals(Integer.valueOf(applied.getOrder().getVersion()), applied.getAppliedOrderVersion());
        assertEquals(applied.getId(), amendments().approve(requested.getId(), requested.getVersion(), manager).getId());
        assertEquals(6, balance(fixture.product).getReserved());
    }

    @Test
    public void increasesCheckOnlyIncrementalExposureAndDoNotReserveAdditionalStockImplicitly() {
        Fixture fixture = fixture(20);
        SalesOrder order = allocated(fixture.order(10));
        creditLimit(fixture, "1650.00");
        OrderAmendment request = amendments().request(command(order, 15), sales);
        OrderAmendment applied = amendments().approve(request.getId(), request.getVersion(), manager);
        assertEquals(new BigDecimal("550.00"), applied.getExposureDelta());
        assertEquals("PART_ALLOCATED", applied.getOrder().getStatus());
        assertEquals(10, balance(fixture.product).getReserved());
        SalesOrder allocated = orders().allocate(order.getId(), applied.getOrder().getVersion(), warehouseActor);
        assertEquals(15, allocated.getAllocatedQuantity());
        assertEquals(15, balance(fixture.product).getReserved());
    }

    @Test
    public void otherApprovedOrdersAreIncludedWhenEvaluatingAnIncrease() {
        final Fixture fixture = fixture(30);
        SalesOrder first = approved(fixture.order(10));
        approved(fixture.order(5));
        creditLimit(fixture, "1800.00");
        final OrderAmendment request = amendments().request(command(first, 13), sales);
        expect("credit.limit", new Runnable() {
            public void run() { amendments().approve(request.getId(), request.getVersion(), manager); }
        });
        assertEquals(10, orders().getOrder(first.getId(), sales).getLines().get(0).getQuantity());
        assertEquals("REQUESTED", amendments().getAmendment(request.getId(), sales).getStatus());
    }

    @Test
    public void reductionsRemainPossibleWhenTheCustomerLimitHasBeenReducedBelowExposure() {
        Fixture fixture = fixture(10);
        SalesOrder order = allocated(fixture.order(10));
        creditLimit(fixture, "1.00");
        OrderAmendment request = amendments().request(command(order, 5), sales);
        assertEquals("APPLIED", amendments().approve(request.getId(), request.getVersion(), manager).getStatus());
        assertEquals(5, balance(fixture.product).getReserved());
    }

    @Test
    public void partiallyShippedOrdersKeepTheirAgreedPricesAndTaxHistoryAfterAnIncrease() {
        Fixture fixture = fixture(20);
        SalesOrder order = allocated(fixture.order(10));
        Shipment shipment = ship(order, 4);
        order = orders().getOrder(order.getId(), sales);
        Product product = catalog.getProduct(fixture.product.getId(), manager);
        product.setListPrice(new BigDecimal("500.00"));
        product.setTaxCategory("REDUCED");
        catalog.saveProduct(product, product.getVersion(), manager);
        OrderAmendment request = amendments().request(command(order, 12), sales);
        OrderAmendment applied = amendments().approve(request.getId(), request.getVersion(), manager);
        SalesOrderLine changed = applied.getOrder().getLines().get(0);
        assertEquals(12, changed.getQuantity());
        assertEquals(4, changed.getShippedQuantity());
        assertEquals(6, changed.getAllocatedQuantity());
        assertEquals(new BigDecimal("100.00"), changed.getUnitPrice());
        assertEquals(new BigDecimal("0.1000"), changed.getTaxRate());
        assertEquals(new BigDecimal("1320.00"), applied.getOrder().getTotalAmount());
        Shipment historic = shipping().getShipment(shipment.getId(), billingActor);
        assertEquals(4, historic.getLines().get(0).getQuantity());
        assertEquals(new BigDecimal("100.00"), historic.getLines().get(0).getUnitPrice());
        assertEquals(new BigDecimal("0.1000"), historic.getLines().get(0).getTaxRate());
        assertEquals(10, applied.getLines().get(0).getOriginalQuantity());
    }

    @Test
    public void reducingToAlreadyShippedQuantityClosesOnlyTheRemainingCommitment() {
        Fixture fixture = fixture(20);
        SalesOrder order = allocated(fixture.order(10));
        ship(order, 4);
        order = orders().getOrder(order.getId(), sales);
        OrderAmendment request = amendments().request(command(order, 4), sales);
        OrderAmendment applied = amendments().approve(request.getId(), request.getVersion(), manager);
        assertEquals("SHIPPED", applied.getOrder().getStatus());
        assertEquals(0, applied.getOrder().getOpenQuantity());
        assertEquals(0, balance(fixture.product).getReserved());
        assertEquals(16, balance(fixture.product).getOnHand());
        assertEquals(new BigDecimal("440.00"), applied.getOrder().getTotalAmount());
        assertEquals(1, applied.getOrder().getLines().size());
    }

    @Test
    public void quantitiesBelowShippedUnitsAndTerminalOrdersAreRejected() {
        Fixture fixture = fixture(10);
        SalesOrder order = allocated(fixture.order(10));
        ship(order, 4);
        final SalesOrder partial = orders().getOrder(order.getId(), sales);
        expect("amendment.fulfilledQuantity", new Runnable() {
            public void run() { amendments().request(command(partial, 3), sales); }
        });
        orders().cancel(partial.getId(), partial.getVersion(), "架空残数中止", sales);
        final SalesOrder closed = orders().getOrder(partial.getId(), sales);
        expect("amendment.orderState", new Runnable() {
            public void run() { amendments().request(command(closed, 12), sales); }
        });
    }

    @Test
    public void openShipmentInstructionsMustBeExplicitlyCancelledBeforeApplication() {
        Fixture fixture = fixture(20);
        SalesOrder order = allocated(fixture.order(10));
        Shipment instruction = instruction(order, 10);
        final OrderAmendment request = amendments().request(command(order, 6), sales);
        expect("amendment.instructions", new Runnable() {
            public void run() { amendments().approve(request.getId(), request.getVersion(), manager); }
        });
        assertEquals("INSTRUCTED", shipping().getShipment(instruction.getId(), warehouseActor).getStatus());
        assertEquals(10, balance(fixture.product).getReserved());
        shipping().cancelInstruction(instruction.getId(), instruction.getVersion(), "架空受注変更のため取消", warehouseActor);
        assertEquals("APPLIED", amendments().approve(request.getId(), request.getVersion(), manager).getStatus());
        assertEquals(6, balance(fixture.product).getReserved());
    }

    @Test
    public void shipmentAfterRequestInvalidatesTheBaseInsteadOfRebasing() {
        Fixture fixture = fixture(20);
        SalesOrder order = allocated(fixture.order(10));
        final OrderAmendment request = amendments().request(command(order, 15), sales);
        ship(order, 4);
        expect("amendment.stale", new Runnable() {
            public void run() { amendments().approve(request.getId(), request.getVersion(), manager); }
        });
        assertEquals(10, orders().getOrder(order.getId(), sales).getLines().get(0).getQuantity());
        assertEquals(4, orders().getOrder(order.getId(), sales).getShippedQuantity());
    }

    @Test
    public void lineOnlyShipmentChangesAreDetectedEvenWhenHeaderStateAndVersionStayTheSame() {
        Fixture fixture = fixture(20);
        SalesOrder order = allocated(fixture.order(15));
        ship(order, 3);
        order = orders().getOrder(order.getId(), sales);
        final OrderAmendment request = amendments().request(command(order, 17), sales);
        ship(order, 3);
        SalesOrder now = orders().getOrder(order.getId(), sales);
        assertEquals("PART_SHIPPED", now.getStatus());
        assertEquals(request.getBaseOrderVersion(), now.getVersion());
        expect("amendment.stale", new Runnable() {
            public void run() { amendments().approve(request.getId(), request.getVersion(), manager); }
        });
    }

    @Test
    public void groupReductionRollsBackEarlierReleasesWhenALaterBalanceIsFrozen() {
        Fixture fixture = fixture(20);
        Product second = newProduct(uniqueCode("SECOND"), 1);
        inventory().receive(uniqueCode("RC"), fixture.warehouse.getId(), second.getId(), 20,
                new BigDecimal("60.00"), Dates.today(), "架空第二商品", "", warehouseActor);
        OrderInput input = fixture.input(10);
        OrderLineInput extra = new OrderLineInput();
        extra.setProductId(second.getId());
        extra.setQuantity(10);
        input.getLines().add(extra);
        SalesOrder order = allocated(orders().saveDraft(null, 0, input, sales));
        OrderAmendmentCommand change = command(order, 5);
        change.getLines().add(change(order.getLines().get(1).getId(), 5));
        final OrderAmendment request = amendments().request(change, sales);
        StockCountCommand countInput = new StockCountCommand();
        countInput.setWarehouseId(fixture.warehouse.getId());
        countInput.getProductIds().add(second.getId());
        StockCount count = service("stockCountService", StockCountService.class).begin(warehouseActor, countInput);
        expect("inventory.blocked", new Runnable() {
            public void run() { amendments().approve(request.getId(), request.getVersion(), manager); }
        });
        assertEquals(10, balance(fixture.product).getReserved());
        assertEquals(10, balance(second).getReserved());
        SalesOrder unchanged = orders().getOrder(order.getId(), sales);
        assertEquals(10, unchanged.getLines().get(0).getQuantity());
        assertEquals(10, unchanged.getLines().get(1).getQuantity());
        assertEquals("REQUESTED", amendments().getAmendment(request.getId(), sales).getStatus());
        service("stockCountService", StockCountService.class).cancel(warehouseActor, count.getId(), count.getVersion(), "架空棚卸取消");
        amendments().approve(request.getId(), request.getVersion(), manager);
        assertEquals(5, balance(fixture.product).getReserved());
        assertEquals(5, balance(second).getReserved());
    }

    @Test
    public void foreignDuplicateAndNoChangeProposalsCreateNoPartialRequests() {
        Fixture fixture = fixture(10);
        final SalesOrder order = approved(fixture.order(5));
        SalesOrder other = approved(fixture.order(2));
        final OrderAmendmentCommand foreign = command(order, 3);
        foreign.getLines().add(change(other.getLines().get(0).getId(), 1));
        expect("amendment.foreignLine", new Runnable() {
            public void run() { amendments().request(foreign, sales); }
        });
        final OrderAmendmentCommand duplicate = command(order, 3);
        duplicate.getLines().add(change(order.getLines().get(0).getId(), 4));
        expect("amendment.duplicateLine", new Runnable() {
            public void run() { amendments().request(duplicate, sales); }
        });
        expect("amendment.noChange", new Runnable() {
            public void run() { amendments().request(command(order, 5), sales); }
        });
        assertTrue(amendments().listForOrder(order.getId(), sales).isEmpty());
    }

    @Test
    public void managerApprovalUsesActorIdAndCannotBePerformedByTheRequester() {
        Fixture fixture = fixture(10);
        SalesOrder order = approved(fixture.order(5));
        final OrderAmendment request = amendments().request(command(order, 3), sales);
        final Actor renamedRequester = new Actor(sales.getUserId(), "renamed-requester", "別表示", "MANAGER");
        expect("approval.self", new Runnable() {
            public void run() { amendments().approve(request.getId(), request.getVersion(), renamedRequester); }
        });
        expect("permission.denied", new Runnable() {
            public void run() { amendments().approve(request.getId(), request.getVersion(), warehouseActor); }
        });
        assertEquals("APPLIED", amendments().approve(request.getId(), request.getVersion(), manager).getStatus());
    }

    @Test
    public void rejectionAndCancellationPreserveProposalsAndPermitANewRequest() {
        Fixture fixture = fixture(10);
        SalesOrder order = approved(fixture.order(5));
        OrderAmendment request = amendments().request(command(order, 3), sales);
        amendments().reject(request.getId(), request.getVersion(), "架空条件不一致", manager);
        OrderAmendment second = amendments().request(command(order, 4), sales);
        amendments().cancel(second.getId(), second.getVersion(), "架空顧客依頼取消", sales);
        assertEquals(2, amendments().listForOrder(order.getId(), sales).size());
        assertEquals(5, orders().getOrder(order.getId(), sales).getLines().get(0).getQuantity());
        Search search = new Search();
        search.setText(second.getNumber());
        search.setStatus("CANCELLED");
        assertEquals(1L, amendments().searchAmendments(search, warehouseActor).getTotal());
    }

    @Test
    public void dateOnlyAmendmentsPreserveQuantitiesAndNeedIndependentApproval() {
        Fixture fixture = fixture(10);
        SalesOrder order = approved(fixture.order(5));
        OrderAmendmentCommand input = command(order, 5);
        input.getLines().clear();
        input.setRequestedDate(Dates.addDays(order.getRequestedDate(), 3));
        OrderAmendment request = amendments().request(input, sales);
        assertTrue(request.getLines().isEmpty());
        OrderAmendment applied = amendments().approve(request.getId(), request.getVersion(), manager);
        assertEquals(Dates.format(input.getRequestedDate()), Dates.format(applied.getOrder().getRequestedDate()));
        assertEquals(5, applied.getOrder().getLines().get(0).getQuantity());
        assertEquals(new BigDecimal("0.00"), applied.getExposureDelta());
    }

    @Test
    public void zeroPricedQuantityChangesStillAdvanceTheOrderHeaderVersion() {
        Fixture fixture = fixture(10);
        Product product = catalog.getProduct(fixture.product.getId(), manager);
        product.setListPrice(BigDecimal.ZERO);
        catalog.saveProduct(product, product.getVersion(), manager);
        SalesOrder order = approved(fixture.order(2));
        OrderAmendment request = amendments().request(command(order, 3), sales);
        OrderAmendment applied = amendments().approve(request.getId(), request.getVersion(), manager);
        assertTrue(applied.getOrder().getVersion() > order.getVersion());
        assertEquals(Integer.valueOf(applied.getOrder().getVersion()), applied.getAppliedOrderVersion());
        assertEquals(new BigDecimal("0.00"), applied.getOrder().getNetAmount());
    }

    @Test
    public void concurrentRequestsSerializeToOnePendingProposal() throws Exception {
        Fixture fixture = fixture(10);
        final SalesOrder order = approved(fixture.order(5));
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> request = new Callable<Boolean>() {
                public Boolean call() throws Exception {
                    assertTrue(start.await(15, TimeUnit.SECONDS));
                    try { amendments().request(command(order, 3), sales); return Boolean.TRUE; }
                    catch (BusinessException ex) { assertEquals("amendment.pending", ex.getCode()); return Boolean.FALSE; }
                }
            };
            Future<Boolean> first = executor.submit(request);
            Future<Boolean> second = executor.submit(request);
            start.countDown();
            assertTrue(first.get(30, TimeUnit.SECONDS) != second.get(30, TimeUnit.SECONDS));
            assertEquals(1, amendments().listForOrder(order.getId(), sales).size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void concurrentApprovalRetriesDoNotReleaseReservationsTwice() throws Exception {
        Fixture fixture = fixture(10);
        SalesOrder order = allocated(fixture.order(10));
        final OrderAmendment request = amendments().request(command(order, 5), sales);
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> approve = new Callable<Long>() {
                public Long call() throws Exception {
                    assertTrue(start.await(15, TimeUnit.SECONDS));
                    return amendments().approve(request.getId(), request.getVersion(), manager).getId();
                }
            };
            Future<Long> first = executor.submit(approve);
            Future<Long> second = executor.submit(approve);
            start.countDown();
            assertEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(5, balance(fixture.product).getReserved());
            assertEquals(5, orders().getOrder(order.getId(), sales).getLines().get(0).getQuantity());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private OrderAmendmentCommand command(SalesOrder order, int target) {
        OrderAmendmentCommand command = new OrderAmendmentCommand();
        command.setOrderId(order.getId());
        command.setExpectedOrderVersion(order.getVersion());
        command.setReason("架空顧客による数量・納期変更依頼");
        command.getLines().add(change(order.getLines().get(0).getId(), target));
        return command;
    }

    private OrderAmendmentLineCommand change(Long lineId, int quantity) {
        OrderAmendmentLineCommand change = new OrderAmendmentLineCommand();
        change.setOrderLineId(lineId);
        change.setTargetQuantity(quantity);
        return change;
    }

    private SalesOrder approved(SalesOrder order) {
        order = orders().submit(order.getId(), order.getVersion(), sales);
        return orders().approve(order.getId(), order.getVersion(), manager);
    }

    private SalesOrder allocated(SalesOrder order) {
        order = approved(order);
        return orders().allocate(order.getId(), order.getVersion(), warehouseActor);
    }

    private Shipment instruction(SalesOrder order, int quantity) {
        ShipmentLineInput line = new ShipmentLineInput();
        line.setOrderLineId(order.getLines().get(0).getId());
        line.setQuantity(quantity);
        return shipping().instruct(order.getId(), order.getVersion(), Dates.today(), "OWN", "",
                Collections.singletonList(line), warehouseActor);
    }

    private Shipment ship(SalesOrder order, int quantity) {
        Shipment instruction = instruction(order, quantity);
        return shipping().confirm(instruction.getId(), instruction.getVersion(), Dates.today(), uniqueCode("TRACK"), warehouseActor);
    }

    private void creditLimit(Fixture fixture, String amount) {
        Customer customer = catalog.getCustomer(fixture.customer.getId(), manager);
        customer.setCreditLimit(new BigDecimal(amount));
        catalog.saveCustomer(customer, customer.getVersion(), manager);
    }

    private StockBalance balance(Product product) {
        Search search = new Search();
        search.setWarehouseId(21L);
        search.setText(product.getCode());
        return inventory().searchStock(search, warehouseActor).getItems().get(0);
    }

    private OrderAmendmentService amendments() { return service("orderAmendmentService", OrderAmendmentService.class); }
    private OrderService orders() { return service("orderService", OrderService.class); }
    private InventoryService inventory() { return service("inventoryService", InventoryService.class); }
    private ShippingService shipping() { return service("shippingService", ShippingService.class); }
    private void expect(String code, Runnable action) {
        try { action.run(); fail("Expected " + code); }
        catch (BusinessException ex) { assertEquals(code, ex.getCode()); }
    }
}
