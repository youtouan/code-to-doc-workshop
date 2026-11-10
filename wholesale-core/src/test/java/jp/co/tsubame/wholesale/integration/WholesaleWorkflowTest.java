package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.AuthenticationResult;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.common.OrderLineInput;
import jp.co.tsubame.wholesale.common.Passwords;
import jp.co.tsubame.wholesale.common.ReturnLineInput;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.ShipmentLineInput;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.AppUser;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesReturn;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.entity.StockReceipt;
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

public class WholesaleWorkflowTest extends PostgresTestSupport {
    @Test
    public void xmlActuallyCreatesTransactionalServiceProxies() {
        assertTrue(AopUtils.isAopProxy(service("orderService", OrderService.class)));
        assertTrue(AopUtils.isAopProxy(service("inventoryService", InventoryService.class)));
        assertTrue(AopUtils.isAopProxy(service("shippingService", ShippingService.class)));
        assertNotNull(context.getBean("sessionFactory", org.hibernate.SessionFactory.class));
    }

    @Test
    public void approvalAllocationAndCancellationReleaseTheSamePhysicalBalance() {
        Fixture fixture = fixture(100);
        SalesOrder order = approved(fixture.order(12));
        order = orders().allocate(order.getId(), order.getVersion(), warehouseActor);
        assertEquals("ALLOCATED", order.getStatus());
        assertEquals(12, balance(fixture).getReserved());
        assertEquals(88, balance(fixture).getAvailable());
        order = orders().cancel(order.getId(), order.getVersion(), "客先取消", sales);
        assertEquals("CANCELLED", order.getStatus());
        assertEquals(12, order.getLines().get(0).getCancelledQuantity());
        assertEquals(0, balance(fixture).getReserved());
        assertEquals(100, balance(fixture).getOnHand());
        assertTrue(inventory().listReservations(order.getId(), warehouseActor).isEmpty());
    }

    @Test
    public void shortagesAreExplicitAndCanBeAllocatedAfterAReceipt() {
        Fixture fixture = fixture(3);
        SalesOrder order = approved(fixture.order(10));
        order = orders().allocate(order.getId(), order.getVersion(), warehouseActor);
        assertEquals("PART_ALLOCATED", order.getStatus());
        assertEquals(7, order.getLines().get(0).getShortageQuantity());
        inventory().receive(uniqueCode("RC"), fixture.warehouse.getId(), fixture.product.getId(), 7,
                new BigDecimal("60.00"), Dates.today(), "追加入庫", "", warehouseActor);
        order = orders().allocate(order.getId(), order.getVersion(), batchActor);
        assertEquals("ALLOCATED", order.getStatus());
        assertEquals(0, order.getLines().get(0).getShortageQuantity());
        assertEquals(10, balance(fixture).getReserved());
    }

    @Test
    public void draftEditingReplacesLinesButApprovalPreservesPriceSnapshots() {
        Fixture fixture = fixture(20);
        SalesOrder original = fixture.order(2);
        OrderInput replacement = fixture.input(4);
        SalesOrder updated = orders().saveDraft(original.getId(), original.getVersion(), replacement, sales);
        assertEquals(1, updated.getLines().size());
        assertEquals(new BigDecimal("400.00"), updated.getNetAmount());
        Product product = catalog.getProduct(fixture.product.getId(), manager);
        product.setListPrice(new BigDecimal("150.00"));
        catalog.saveProduct(product, product.getVersion(), manager);
        SalesOrder approved = approved(updated);
        assertEquals(new BigDecimal("100.00"), approved.getLines().get(0).getUnitPrice());
        assertEquals(new BigDecimal("400.00"), approved.getNetAmount());
        SalesOrder copy = orders().copyToDraft(approved.getId(), sales);
        assertEquals(new BigDecimal("150.00"), copy.getLines().get(0).getUnitPrice());
        assertEquals("DRAFT", copy.getStatus());
    }

    @Test
    public void staleVersionsAndWrongRolesDoNotChangeOrderState() {
        Fixture fixture = fixture(10);
        final SalesOrder draft = fixture.order(2);
        final SalesOrder submitted = orders().submit(draft.getId(), draft.getVersion(), sales);
        expect("concurrent.update", new Runnable() {
            @Override
            public void run() {
                orders().cancel(draft.getId(), draft.getVersion(), "stale", sales);
            }
        });
        expect("permission.denied", new Runnable() {
            @Override
            public void run() {
                orders().approve(submitted.getId(), submitted.getVersion(), sales);
            }
        });
        assertEquals("SUBMITTED", orders().getOrder(submitted.getId(), sales).getStatus());
    }

    @Test
    public void separationOfDutiesDoesNotDisappearForManagerAccounts() {
        Fixture fixture = fixture(10);
        SalesOrder draft = orders().saveDraft(null, 0, fixture.input(2), manager);
        final SalesOrder submitted = orders().submit(draft.getId(), draft.getVersion(), manager);
        expect("approval.self", new Runnable() {
            @Override
            public void run() {
                orders().approve(submitted.getId(), submitted.getVersion(), manager);
            }
        });
        assertEquals("SUBMITTED", orders().getOrder(submitted.getId(), manager).getStatus());
    }

    @Test
    public void priceOverrideNeedsBothPermissionAndAnExplanation() {
        final Fixture fixture = fixture(10);
        final OrderInput input = fixture.input(2);
        input.getLines().get(0).setPriceOverride(new BigDecimal("0.01"));
        input.getLines().get(0).setPriceReason("authorized promotion");
        expect("permission.denied", new Runnable() {
            @Override
            public void run() {
                orders().saveDraft(null, 0, input, sales);
            }
        });
        input.getLines().get(0).setPriceReason("");
        expect("validation.required", new Runnable() {
            @Override
            public void run() {
                orders().saveDraft(null, 0, input, manager);
            }
        });
    }

    @Test
    public void receiptRerunsAreIdempotentButMismatchedPayloadsFail() {
        final Fixture fixture = fixture(0);
        final String key = uniqueCode("RC");
        StockReceipt first = inventory().receive(key, fixture.warehouse.getId(), fixture.product.getId(), 5,
                new BigDecimal("60"), Dates.today(), "納品001", "first", warehouseActor);
        StockReceipt second = inventory().receive(key, fixture.warehouse.getId(), fixture.product.getId(), 5,
                new BigDecimal("60.00"), Dates.today(), "納品001", "first", batchActor);
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getNumber(), inventory().getReceipt(first.getId(), warehouseActor).getNumber());
        assertTrue(first.getNumber().startsWith("RC-"));
        assertEquals(5, balance(fixture).getOnHand());
        expect("idempotency.conflict", new Runnable() {
            @Override
            public void run() {
                inventory().receive(key, fixture.warehouse.getId(), fixture.product.getId(), 6,
                        new BigDecimal("60.00"), Dates.today(), "納品001", "first", warehouseActor);
            }
        });
        assertEquals(5, balance(fixture).getOnHand());
    }

    @Test
    public void partialShippingAndCancellingRemainderDoNotUndoPhysicalShipment() {
        Fixture fixture = fixture(100);
        SalesOrder order = allocated(fixture.order(10));
        Shipment shipment = instruction(order, 4);
        shipment = shipping().confirm(shipment.getId(), shipment.getVersion(), Dates.today(), uniqueCode("TRACK"), warehouseActor);
        assertEquals("CONFIRMED", shipment.getStatus());
        assertEquals(96, balance(fixture).getOnHand());
        assertEquals(6, balance(fixture).getReserved());
        order = orders().getOrder(order.getId(), sales);
        assertEquals("PART_SHIPPED", order.getStatus());
        order = orders().cancel(order.getId(), order.getVersion(), "残数取消", sales);
        assertEquals("CLOSED_PARTIAL", order.getStatus());
        assertEquals(4, order.getLines().get(0).getShippedQuantity());
        assertEquals(6, order.getLines().get(0).getCancelledQuantity());
        assertEquals(96, balance(fixture).getOnHand());
        assertEquals(0, balance(fixture).getReserved());
    }

    @Test
    public void multipleInstructionsCannotOverpromiseTheSameReservation() {
        Fixture fixture = fixture(100);
        final SalesOrder order = allocated(fixture.order(10));
        final Shipment first = instruction(order, 6);
        expect("shipping.excessInstruction", new Runnable() {
            @Override
            public void run() {
                instruction(order, 5);
            }
        });
        expect("allocation.instruction", new Runnable() {
            @Override
            public void run() {
                SalesOrder current = orders().getOrder(order.getId(), sales);
                orders().releaseAllocation(current.getId(), current.getVersion(), "release", sales);
            }
        });
        shipping().cancelInstruction(first.getId(), first.getVersion(), "再指示", warehouseActor);
        Shipment replacement = instruction(order, 10);
        assertEquals(10, replacement.getLines().get(0).getQuantity());
    }

    @Test
    public void orderCancellationAlsoCancelsOpenInstructions() {
        Fixture fixture = fixture(20);
        SalesOrder order = allocated(fixture.order(10));
        Shipment instruction = instruction(order, 10);
        order = orders().getOrder(order.getId(), sales);
        orders().cancel(order.getId(), order.getVersion(), "受注取消", sales);
        assertEquals("CANCELLED", shipping().getShipment(instruction.getId(), warehouseActor).getStatus());
        assertEquals(0, balance(fixture).getReserved());
    }

    @Test
    public void shipmentFailureRollsBackEarlierLinesAndLedgerEntries() {
        final Fixture fixture = fixture(20);
        final Product second = newProduct(uniqueCode("Q"), 1);
        inventory().receive(uniqueCode("R"), fixture.warehouse.getId(), second.getId(), 20,
                new BigDecimal("60.00"), Dates.today(), "second product", "", warehouseActor);
        OrderInput input = fixture.input(5);
        OrderLineInput line = new OrderLineInput();
        line.setProductId(second.getId());
        line.setQuantity(5);
        input.getLines().add(line);
        SalesOrder order = allocated(orders().saveDraft(null, 0, input, sales));
        List<ShipmentLineInput> lines = new ArrayList<ShipmentLineInput>();
        for (jp.co.tsubame.wholesale.entity.SalesOrderLine orderLine : order.getLines()) {
            ShipmentLineInput shipmentLine = new ShipmentLineInput();
            shipmentLine.setOrderLineId(orderLine.getId());
            shipmentLine.setQuantity(5);
            lines.add(shipmentLine);
        }
        final Shipment shipment = shipping().instruct(order.getId(), order.getVersion(), Dates.today(),
                "OWN", "", lines, warehouseActor);
        transaction(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction(TransactionStatus status) {
                WholesaleDao dao = context.getBean("wholesaleDao", WholesaleDao.class);
                StockBalance balance = StockBalance.class.cast(dao.query("from StockBalance b where "
                        + "b.warehouse.id=:warehouse and b.product.id=:product",
                        WholesaleDao.params("warehouse", fixture.warehouse.getId(), "product", second.getId())).uniqueResult());
                balance.setBlocked(true);
                return null;
            }
        });
        expect("inventory.blocked", new Runnable() {
            @Override
            public void run() {
                shipping().confirm(shipment.getId(), shipment.getVersion(), Dates.today(), "rollback-case", warehouseActor);
            }
        });
        assertEquals(20, balance(fixture).getOnHand());
        assertEquals(5, balance(fixture).getReserved());
        SalesOrder unchanged = orders().getOrder(order.getId(), sales);
        assertEquals(0, unchanged.getShippedQuantity());
        assertEquals("INSTRUCTED", shipping().getShipment(shipment.getId(), warehouseActor).getStatus());
        final Long shipmentId = shipment.getId();
        Long rows = transaction(new TransactionCallback<Long>() {
            @Override
            public Long doInTransaction(TransactionStatus status) {
                return context.getBean("wholesaleDao", WholesaleDao.class).count(
                        "select count(m.id) from StockMovement m where m.documentType='Shipment' and m.documentId=:id",
                        WholesaleDao.params("id", shipmentId));
            }
        });
        assertEquals(Long.valueOf(0), rows);
    }

    @Test
    public void competingOrdersDoNotDoubleAllocateOneBalance() throws Exception {
        final Fixture first = fixture(100);
        Fixture other = fixture(0);
        OrderInput secondInput = other.input(80);
        secondInput.getLines().get(0).setProductId(first.product.getId());
        final SalesOrder firstOrder = approved(first.order(80));
        final SalesOrder secondOrder = approved(orders().saveDraft(null, 0, secondInput, sales));
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<SalesOrder> a = pool.submit(new Callable<SalesOrder>() {
                @Override
                public SalesOrder call() throws Exception {
                    start.await();
                    return orders().allocate(firstOrder.getId(), firstOrder.getVersion(), warehouseActor);
                }
            });
            Future<SalesOrder> b = pool.submit(new Callable<SalesOrder>() {
                @Override
                public SalesOrder call() throws Exception {
                    start.await();
                    return orders().allocate(secondOrder.getId(), secondOrder.getVersion(), warehouseActor);
                }
            });
            start.countDown();
            SalesOrder one = a.get(30, TimeUnit.SECONDS);
            SalesOrder two = b.get(30, TimeUnit.SECONDS);
            assertEquals(100, one.getAllocatedQuantity() + two.getAllocatedQuantity());
            assertEquals(100, balance(first).getReserved());
            assertEquals(0, balance(first).getAvailable());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void receivedReturnRestocksAndCannotBeReceivedOrRequestedTwice() {
        final Fixture fixture = fixture(20);
        SalesOrder order = allocated(fixture.order(5));
        Shipment shipment = instruction(order, 5);
        shipment = shipping().confirm(shipment.getId(), shipment.getVersion(), Dates.today(), uniqueCode("TRACK"), warehouseActor);
        final ReturnLineInput input = new ReturnLineInput();
        input.setShipmentLineId(shipment.getLines().get(0).getId());
        input.setQuantity(3);
        input.setRestock(true);
        SalesReturn requested = shipping().requestReturn(shipment.getId(), "CUSTOMER_CHANGE", "",
                Arrays.asList(input), sales);
        SalesReturn approved = shipping().approveReturn(requested.getId(), requested.getVersion(), manager);
        final SalesReturn received = shipping().receiveReturn(approved.getId(), approved.getVersion(), Dates.today(), warehouseActor);
        assertEquals("RECEIVED", received.getStatus());
        assertEquals(18, balance(fixture).getOnHand());
        assertEquals(0, balance(fixture).getReserved());
        assertEquals(3, shipping().getShipment(shipment.getId(), warehouseActor).getLines().get(0).getReturnedQuantity());
        expect("return.notApproved", new Runnable() {
            @Override
            public void run() {
                shipping().receiveReturn(received.getId(), received.getVersion(), Dates.today(), warehouseActor);
            }
        });
        final Long shipmentId = shipment.getId();
        expect("return.excess", new Runnable() {
            @Override
            public void run() {
                shipping().requestReturn(shipmentId, "CUSTOMER_CHANGE", "", Arrays.asList(input), sales);
            }
        });
        assertEquals(18, balance(fixture).getOnHand());
    }

    @Test
    public void damagedReturnsCannotBeRestocked() {
        Fixture fixture = fixture(10);
        SalesOrder order = allocated(fixture.order(2));
        Shipment instruction = instruction(order, 2);
        final Shipment shipped = shipping().confirm(instruction.getId(), instruction.getVersion(), Dates.today(), "DAMAGED-TEST", warehouseActor);
        final ReturnLineInput line = new ReturnLineInput();
        line.setShipmentLineId(shipped.getLines().get(0).getId());
        line.setQuantity(1);
        line.setRestock(true);
        expect("return.disposition", new Runnable() {
            @Override
            public void run() {
                shipping().requestReturn(shipped.getId(), "DAMAGED", "", Arrays.asList(line), sales);
            }
        });
        assertEquals(8, balance(fixture).getOnHand());
    }

    @Test
    public void invalidLoginsPersistThrottlingInsteadOfRollingItBack() {
        final AppUser user = new AppUser();
        user.setLogin(uniqueCode("auth").toLowerCase(java.util.Locale.ROOT));
        user.setDisplayName("認証試験利用者");
        user.setPasswordHash(Passwords.hash("Synthetic-Auth-123".toCharArray()));
        user.setRoles("SALES");
        user.setActive(true);
        transaction(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction(TransactionStatus status) {
                context.getBean("wholesaleDao", WholesaleDao.class).save(user);
                return null;
            }
        });
        for (int attempt = 0; attempt < 5; attempt++) {
            assertFalse(auth.authenticate(user.getLogin(), "Wrong-Password-123".toCharArray()).isAuthenticated());
        }
        assertFalse(auth.authenticate(user.getLogin(), "Synthetic-Auth-123".toCharArray()).isAuthenticated());
        Integer attempts = transaction(new TransactionCallback<Integer>() {
            @Override
            public Integer doInTransaction(TransactionStatus status) {
                AppUser persisted = context.getBean("wholesaleDao", WholesaleDao.class).get(AppUser.class, user.getId());
                assertNotNull(persisted.getLockedUntil());
                return persisted.getFailedAttempts();
            }
        });
        assertEquals(Integer.valueOf(5), attempts);
    }

    @Test
    public void badDatesAndUnauthorizedInventoryWritesLeaveNoReceipt() {
        final Fixture fixture = fixture(0);
        expect("permission.denied", new Runnable() {
            @Override
            public void run() {
                inventory().receive(uniqueCode("RC"), fixture.warehouse.getId(), fixture.product.getId(), 5,
                        new BigDecimal("60"), Dates.today(), "wrong-role", "", sales);
            }
        });
        expect("receipt.future", new Runnable() {
            @Override
            public void run() {
                inventory().receive(uniqueCode("RC"), fixture.warehouse.getId(), fixture.product.getId(), 5,
                        new BigDecimal("60"), Dates.addDays(Dates.today(), 1), "future", "", warehouseActor);
            }
        });
        Search search = new Search();
        search.setText(fixture.product.getCode());
        assertEquals(0, inventory().searchStock(search, warehouseActor).getTotal());
    }

    private OrderService orders() {
        return service("orderService", OrderService.class);
    }

    private InventoryService inventory() {
        return service("inventoryService", InventoryService.class);
    }

    private ShippingService shipping() {
        return service("shippingService", ShippingService.class);
    }

    private SalesOrder approved(SalesOrder draft) {
        SalesOrder submitted = orders().submit(draft.getId(), draft.getVersion(), sales);
        return orders().approve(submitted.getId(), submitted.getVersion(), manager);
    }

    private SalesOrder allocated(SalesOrder draft) {
        SalesOrder approved = approved(draft);
        return orders().allocate(approved.getId(), approved.getVersion(), warehouseActor);
    }

    private Shipment instruction(SalesOrder order, int quantity) {
        ShipmentLineInput line = new ShipmentLineInput();
        line.setOrderLineId(order.getLines().get(0).getId());
        line.setQuantity(quantity);
        return shipping().instruct(order.getId(), order.getVersion(), Dates.today(), "OWN", "", Arrays.asList(line), warehouseActor);
    }

    private StockBalance balance(Fixture fixture) {
        Search search = new Search();
        search.setText(fixture.product.getCode());
        search.setWarehouseId(fixture.warehouse.getId());
        List<StockBalance> balances = inventory().searchStock(search, warehouseActor).getItems();
        assertEquals(1, balances.size());
        return balances.get(0);
    }

    private <T> T transaction(TransactionCallback<T> callback) {
        PlatformTransactionManager manager = context.getBean("transactionManager", PlatformTransactionManager.class);
        return new TransactionTemplate(manager).execute(callback);
    }

    private void expect(String code, Runnable operation) {
        try {
            operation.run();
            fail("Expected business failure " + code);
        } catch (BusinessException ex) {
            assertEquals(code, ex.getCode());
        }
    }
}
