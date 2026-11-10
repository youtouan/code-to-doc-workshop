package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.PurchasingReceiptCommand;
import jp.co.tsubame.wholesale.common.PurchasingReceiptLineCommand;
import jp.co.tsubame.wholesale.common.PurchasingReorderSuggestion;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.PurchaseOrder;
import jp.co.tsubame.wholesale.entity.PurchaseOrderLine;
import jp.co.tsubame.wholesale.entity.PurchaseReceipt;
import jp.co.tsubame.wholesale.entity.StockReceipt;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.entity.SupplierProduct;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.PurchasingService;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.Assert.*;

public class PurchasingIntegrationTest extends PostgresTestSupport {
    @Test
    public void purchasingAndInventoryAreBothTransactionalXmlProxies() {
        assertTrue(AopUtils.isAopProxy(purchasing()));
        assertTrue(AopUtils.isAopProxy(inventory()));
    }

    @Test
    public void purchaseCreationWinningProductLockPreventsConcurrentProductDeactivation() throws Exception {
        referenceRace(false, true);
    }

    @Test
    public void productDeactivationWinningLockPreventsConcurrentPurchaseCreation() throws Exception {
        referenceRace(false, false);
    }

    @Test
    public void purchaseCreationWinningWarehouseLockPreventsConcurrentWarehouseDeactivation() throws Exception {
        referenceRace(true, true);
    }

    @Test
    public void warehouseDeactivationWinningLockPreventsConcurrentPurchaseCreation() throws Exception {
        referenceRace(true, false);
    }

    @Test
    public void draftIgnoresForgedStatusCostsFulfillmentAndAuditFields() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder input = fixture.input(10);
        input.setStatus("RECEIVED");
        input.setNumber("FORGED");
        input.setCreatedBy("forged");
        input.setApprovedBy("forged");
        input.setTotalAmount(BigDecimal.ZERO);
        input.getLines().get(0).setUnitCost(BigDecimal.ZERO);
        input.getLines().get(0).setReceivedQuantity(10);
        PurchaseOrder order = purchasing().saveOrder(input, 0, warehouseActor);
        assertEquals("DRAFT", order.getStatus());
        assertTrue(order.getNumber().startsWith("PO-"));
        assertEquals(warehouseActor.getLogin(), order.getCreatedBy());
        assertNull(order.getApprovedBy());
        assertEquals(new BigDecimal("600.00"), order.getTotalAmount());
        assertEquals(0, order.getLines().get(0).getReceivedQuantity());
    }

    @Test
    public void editingDraftRemovesOldLinesWithoutUniqueConstraintFailures() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder order = fixture.draft(10);
        Long oldLineId = order.getLines().get(0).getId();
        order.getLines().get(0).setQuantity(5);
        order = purchasing().saveOrder(order, order.getVersion(), warehouseActor);
        assertEquals(1, order.getLines().size());
        assertEquals(new BigDecimal("300.00"), order.getTotalAmount());
        assertFalse(oldLineId.equals(order.getLines().get(0).getId()));
    }

    @Test
    public void supplierContractsUseQuantityTiersAndEffectiveDates() {
        PurchaseFixture fixture = purchaseFixture();
        SupplierProduct tier = fixture.offer(fixture.product, 10, 1, "50.00");
        purchasing().saveSupplierProduct(tier, 0, manager);
        assertEquals(new BigDecimal("60.00"), purchasing().previewSupplierQuote(fixture.supplier.getId(),
                fixture.product.getId(), 9, Dates.today(), warehouseActor).getUnitCost());
        assertEquals(new BigDecimal("50.00"), fixture.draft(10).getLines().get(0).getUnitCost());
    }

    @Test
    public void overlappingSupplierContractsAreRejectedUnderSupplierLock() {
        final PurchaseFixture fixture = purchaseFixture();
        expect("supplierProduct.overlap", new Runnable() {
            public void run() {
                purchasing().saveSupplierProduct(fixture.offer(fixture.product, 1, 1, "55.00"), 0, manager);
            }
        });
        assertEquals(1, purchasing().listSupplierProducts(fixture.supplier.getId(), warehouseActor).size());
    }

    @Test
    public void orderQuantityMustMatchSupplierPack() {
        final PurchaseFixture fixture = purchaseFixture();
        SupplierProduct offer = purchasing().listSupplierProducts(fixture.supplier.getId(), manager).get(0);
        offer.setMinimumQuantity(6);
        offer.setOrderPackSize(6);
        purchasing().saveSupplierProduct(offer, offer.getVersion(), manager);
        expect("purchasing.orderPack", new Runnable() {
            public void run() { fixture.draft(7); }
        });
        assertEquals(12, fixture.draft(12).getLines().get(0).getQuantity());
    }

    @Test
    public void leadTimeAndMinimumOrderAmountAreEnforced() {
        final PurchaseFixture fixture = purchaseFixture();
        SupplierProduct offer = purchasing().listSupplierProducts(fixture.supplier.getId(), manager).get(0);
        offer.setLeadTimeDays(10);
        purchasing().saveSupplierProduct(offer, offer.getVersion(), manager);
        expect("purchasing.leadTime", new Runnable() {
            public void run() { fixture.draft(1); }
        });
        offer = purchasing().listSupplierProducts(fixture.supplier.getId(), manager).get(0);
        offer.setLeadTimeDays(0);
        purchasing().saveSupplierProduct(offer, offer.getVersion(), manager);
        Supplier supplier = purchasing().getSupplier(fixture.supplier.getId(), manager);
        supplier.setMinimumOrderAmount(new BigDecimal("1000.00"));
        purchasing().saveSupplier(supplier, supplier.getVersion(), manager);
        final PurchaseOrder draft = fixture.draft(1);
        expect("purchasing.minimumAmount", new Runnable() {
            public void run() { purchasing().submitOrder(draft.getId(), draft.getVersion(), warehouseActor); }
        });
    }

    @Test
    public void creatorCannotApproveEvenWithManagerRole() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder order = purchasing().saveOrder(fixture.input(10), 0, manager);
        final PurchaseOrder submitted = purchasing().submitOrder(order.getId(), order.getVersion(), warehouseActor);
        expect("approval.self", new Runnable() {
            public void run() { purchasing().approveOrder(submitted.getId(), submitted.getVersion(), manager); }
        });
        assertEquals("SUBMITTED", purchasing().getOrder(order.getId(), manager).getStatus());
        assertEquals("APPROVED", purchasing().approveOrder(order.getId(), submitted.getVersion(), admin).getStatus());
    }

    @Test
    public void rejectedOrdersCanBeCorrectedAndResubmitted() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder order = fixture.draft(10);
        order = purchasing().submitOrder(order.getId(), order.getVersion(), warehouseActor);
        order = purchasing().rejectOrder(order.getId(), order.getVersion(), "架空発注数量を再確認", manager);
        assertEquals("REJECTED", order.getStatus());
        order.getLines().get(0).setQuantity(5);
        order = purchasing().saveOrder(order, order.getVersion(), warehouseActor);
        assertEquals("DRAFT", order.getStatus());
        assertEquals("", order.getDecisionReason());
        assertNull(order.getSubmittedBy());
        assertEquals("APPROVED", approve(order).getStatus());
    }

    @Test
    public void inactiveOrHeldSupplierPreventsNewCommitmentsButDoesNotBlockPreviouslyApprovedReceipt() {
        final PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder approved = fixture.approved(10);
        Supplier supplier = purchasing().getSupplier(fixture.supplier.getId(), manager);
        supplier.setOnHold(true);
        purchasing().saveSupplier(supplier, supplier.getVersion(), manager);
        expect("purchasing.supplierUnavailable", new Runnable() {
            public void run() { fixture.draft(1); }
        });
        PurchaseReceipt receipt = purchasing().receive(command(approved, 10, 0), warehouseActor);
        assertEquals(10, receipt.getAcceptedQuantity());
        assertEquals("RECEIVED", purchasing().getOrder(approved.getId(), warehouseActor).getStatus());
    }

    @Test
    public void priceSnapshotSurvivesMasterChangesAfterSubmission() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder draft = fixture.draft(10);
        PurchaseOrder submitted = purchasing().submitOrder(draft.getId(), draft.getVersion(), warehouseActor);
        SupplierProduct offer = purchasing().listSupplierProducts(fixture.supplier.getId(), manager).get(0);
        offer.setUnitCost(new BigDecimal("75.00"));
        purchasing().saveSupplierProduct(offer, offer.getVersion(), manager);
        PurchaseOrder approved = purchasing().approveOrder(submitted.getId(), submitted.getVersion(), manager);
        PurchaseReceipt receipt = purchasing().receive(command(approved, 10, 0), warehouseActor);
        assertEquals(new BigDecimal("60.00"), receipt.getLines().get(0).getStockReceipt().getUnitCost());
        assertEquals(new BigDecimal("600.00"), receipt.getAcceptedAmount());
    }

    @Test
    public void partialReceivingRejectsBadGoodsWithoutLosingReplacementCommitment() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder order = fixture.approved(10);
        PurchaseReceipt first = purchasing().receive(command(order, 4, 2), warehouseActor);
        assertEquals(4, first.getAcceptedQuantity());
        assertEquals(2, first.getRejectedQuantity());
        assertEquals(4L, onHand(fixture.product, fixture.warehouse));
        order = purchasing().getOrder(order.getId(), warehouseActor);
        assertEquals("PART_RECEIVED", order.getStatus());
        assertEquals(6, order.getLines().get(0).getOutstandingQuantity());
        assertEquals(2, order.getLines().get(0).getRejectedQuantity());
        PurchaseReceipt finalReceipt = purchasing().receive(command(order, 6, 0), warehouseActor);
        assertEquals(6, finalReceipt.getAcceptedQuantity());
        assertEquals(10L, onHand(fixture.product, fixture.warehouse));
        assertEquals("RECEIVED", purchasing().getOrder(order.getId(), manager).getStatus());
        assertEquals(2, purchasing().listReceipts(order.getId(), billingActor).size());
    }

    @Test
    public void allRejectedDeliveryCreatesInspectionHistoryButNoInventoryReceipt() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder order = fixture.approved(10);
        PurchaseReceipt receipt = purchasing().receive(command(order, 0, 10), warehouseActor);
        assertNull(receipt.getLines().get(0).getStockReceipt());
        assertEquals(new BigDecimal("0.00"), receipt.getAcceptedAmount());
        assertEquals(0L, onHand(fixture.product, fixture.warehouse));
        order = purchasing().getOrder(order.getId(), warehouseActor);
        assertEquals(10L, order.getOutstandingQuantity());
        assertEquals("CLOSED", purchasing().closeOutstanding(order.getId(), order.getVersion(),
                "架空不良品の代替品調達を中止", manager).getStatus());
    }

    @Test
    public void duplicateReceiptIsPayloadBoundAndDoesNotIncreaseInventory() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder order = fixture.approved(10);
        final PurchasingReceiptCommand command = command(order, 4, 0);
        PurchaseReceipt first = purchasing().receive(command, warehouseActor);
        assertEquals(first.getId(), purchasing().receive(command, warehouseActor).getId());
        command.getLines().get(0).setAcceptedQuantity(5);
        expect("purchasing.idempotencyConflict", new Runnable() {
            public void run() { purchasing().receive(command, warehouseActor); }
        });
        assertEquals(4L, onHand(fixture.product, fixture.warehouse));
    }

    @Test
    public void simultaneousIdenticalReceiptRequestsReturnTheSameReceipt() throws Exception {
        final PurchaseFixture fixture = purchaseFixture();
        final PurchasingReceiptCommand command = command(fixture.approved(10), 4, 0);
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> attempt = new Callable<Long>() {
                public Long call() throws Exception {
                    assertTrue(start.await(15, TimeUnit.SECONDS));
                    return purchasing().receive(command, warehouseActor).getId();
                }
            };
            Future<Long> first = executor.submit(attempt);
            Future<Long> second = executor.submit(attempt);
            start.countDown();
            assertEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(4L, onHand(fixture.product, fixture.warehouse));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void excessReceiptAndStaleOrderVersionAreRejected() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder order = fixture.approved(10);
        final PurchasingReceiptCommand excess = command(order, 11, 0);
        expect("purchasing.overReceipt", new Runnable() {
            public void run() { purchasing().receive(excess, warehouseActor); }
        });
        final PurchasingReceiptCommand stale = command(order, 1, 0);
        stale.setExpectedVersion(order.getVersion() + 1);
        expect("concurrent.update", new Runnable() {
            public void run() { purchasing().receive(stale, warehouseActor); }
        });
        assertEquals(0L, onHand(fixture.product, fixture.warehouse));
        assertTrue(purchasing().listReceipts(order.getId(), manager).isEmpty());
    }

    @Test
    public void foreignReceiptLineIsRejectedBeforeAnyStockChanges() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder order = fixture.approved(10);
        PurchaseOrder other = fixture.approved(2);
        final PurchasingReceiptCommand command = command(order, 1, 0);
        command.getLines().get(0).setOrderLineId(other.getLines().get(0).getId());
        expect("purchasing.foreignLine", new Runnable() {
            public void run() { purchasing().receive(command, warehouseActor); }
        });
        assertEquals(0L, onHand(fixture.product, fixture.warehouse));
    }

    @Test
    public void cancellationAndShortClosureReleaseOnlyUnreceivedCommitments() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder order = fixture.approved(10);
        purchasing().receive(command(order, 4, 0), warehouseActor);
        final PurchaseOrder partial = purchasing().getOrder(order.getId(), warehouseActor);
        expect("purchasing.notCancellable", new Runnable() {
            public void run() { purchasing().cancelOrder(partial.getId(), partial.getVersion(), "取消", manager); }
        });
        order = purchasing().closeOutstanding(partial.getId(), partial.getVersion(), "架空不足分の調達を中止", manager);
        assertEquals("CLOSED", order.getStatus());
        assertEquals(4, order.getLines().get(0).getReceivedQuantity());
        assertEquals(6, order.getLines().get(0).getCancelledQuantity());
        assertEquals(0L, order.getOutstandingQuantity());
        assertEquals(4L, onHand(fixture.product, fixture.warehouse));
    }

    @Test
    public void approvedCancellationRequiresManagerAndPreservesSnapshots() {
        PurchaseFixture fixture = purchaseFixture();
        final PurchaseOrder order = fixture.approved(10);
        expect("permission.denied", new Runnable() {
            public void run() { purchasing().cancelOrder(order.getId(), order.getVersion(), "取り消し", warehouseActor); }
        });
        PurchaseOrder cancelled = purchasing().cancelOrder(order.getId(), order.getVersion(), "架空需要変更", manager);
        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals(10, cancelled.getLines().get(0).getCancelledQuantity());
        assertEquals(new BigDecimal("600.00"), cancelled.getTotalAmount());
    }

    @Test
    public void reorderCombinesStockAndApprovedCommitmentsButExcludesDraftsAndCancelledLines() {
        PurchaseFixture fixture = purchaseFixture();
        fixture.draft(100);
        PurchasingReorderSuggestion initial = suggestion(fixture);
        assertNotNull(initial);
        assertEquals(20, initial.getSuggestedQuantity());
        PurchaseOrder approved = fixture.approved(10);
        assertNull(suggestion(fixture));
        purchasing().receive(command(approved, 4, 0), warehouseActor);
        assertNull(suggestion(fixture));
        PurchaseOrder partial = purchasing().getOrder(approved.getId(), warehouseActor);
        purchasing().closeOutstanding(partial.getId(), partial.getVersion(), "架空未入荷の取消", manager);
        PurchasingReorderSuggestion after = suggestion(fixture);
        assertNotNull(after);
        assertEquals(4L, after.getOnHand());
        assertEquals(0L, after.getOpenPurchaseQuantity());
        assertEquals(20, after.getSuggestedQuantity());
    }

    @Test
    public void reorderExcludesHeldSuppliersAndMarksProductsWithoutAQuote() {
        PurchaseFixture fixture = purchaseFixture();
        Supplier supplier = purchasing().getSupplier(fixture.supplier.getId(), manager);
        supplier.setOnHold(true);
        purchasing().saveSupplier(supplier, supplier.getVersion(), manager);
        PurchasingReorderSuggestion suggestion = suggestion(fixture);
        assertNotNull(suggestion);
        assertNull(suggestion.getSupplier());
        assertEquals(0, suggestion.getSuggestedQuantity());
        assertTrue(suggestion.getWarning().length() > 0);
    }

    @Test
    public void receiptInventoryFailureRollsBackAlreadyFlushedEarlierLines() {
        final PurchaseFixture fixture = purchaseFixture();
        final Product second = newProduct(uniqueCode("R"), 1);
        purchasing().saveSupplierProduct(fixture.offer(second, 1, 1, "60.00"), 0, manager);
        PurchaseOrder input = fixture.input(4);
        PurchaseOrderLine secondLine = new PurchaseOrderLine();
        secondLine.setProduct(second);
        secondLine.setQuantity(3);
        input.getLines().add(secondLine);
        final PurchaseOrder order = approve(purchasing().saveOrder(input, 0, warehouseActor));
        final PurchasingReceiptCommand command = command(order, 4, 0);
        PurchasingReceiptLineCommand receiptSecond = new PurchasingReceiptLineCommand();
        receiptSecond.setOrderLineId(order.getLines().get(1).getId());
        receiptSecond.setAcceptedQuantity(3);
        command.getLines().add(receiptSecond);
        final PurchasingService isolated = new PurchasingService();
        isolated.setDao(dao());
        isolated.setInventoryService(new InventoryService() {
            private int calls;
            @Override
            public StockReceipt receive(String key, Long warehouseId, Long productId, int quantity,
                    BigDecimal cost, Date date, String reference, String note, Actor actor) {
                StockReceipt receipt = inventory().receive(key, warehouseId, productId, quantity, cost, date, reference, note, actor);
                if (++calls == 2) { throw new BusinessException("test.inventoryFailure", "injected after actual inventory flush"); }
                return receipt;
            }
        });
        expect("test.inventoryFailure", new Runnable() {
            public void run() {
                transaction().execute(new TransactionCallback<Object>() {
                    public Object doInTransaction(TransactionStatus status) { return isolated.receive(command, warehouseActor); }
                });
            }
        });
        assertEquals(0L, onHand(fixture.product, fixture.warehouse));
        assertEquals(0L, onHand(second, fixture.warehouse));
        assertTrue(purchasing().listReceipts(order.getId(), manager).isEmpty());
        assertEquals("APPROVED", purchasing().getOrder(order.getId(), manager).getStatus());
        assertEquals(0, purchasing().getOrder(order.getId(), manager).getLines().get(0).getReceivedQuantity());
    }

    @Test
    public void searchAndOverdueListsReturnInitializedDocumentLines() {
        PurchaseFixture fixture = purchaseFixture();
        PurchaseOrder input = fixture.input(2);
        input.setOrderDate(Dates.addDays(Dates.today(), -5));
        input.setExpectedDate(Dates.addDays(Dates.today(), -1));
        PurchaseOrder order = approve(purchasing().saveOrder(input, 0, warehouseActor));
        Search search = new Search();
        search.setText(order.getNumber());
        search.setStatus("APPROVED");
        assertEquals(1L, purchasing().searchOrders(search, warehouseActor).getTotal());
        assertEquals(1, purchasing().searchOrders(search, warehouseActor).getItems().get(0).getLines().size());
        boolean found = false;
        for (PurchaseOrderLine line : purchasing().listOverdueLines(fixture.warehouse.getId(), Dates.today(), manager)) {
            if (line.getOrder().getId().equals(order.getId())) { found = true; }
        }
        assertTrue(found);
    }

    private PurchaseFixture purchaseFixture() {
        Product product = newProduct(uniqueCode("P"), 1);
        Supplier supplier = new Supplier();
        supplier.setCode(uniqueCode("S"));
        supplier.setName("架空仕入試験会社");
        supplier.setDefaultLeadTimeDays(0);
        supplier = purchasing().saveSupplier(supplier, 0, manager);
        PurchaseFixture fixture = new PurchaseFixture(product, supplier, catalog.getWarehouse(21L, warehouseActor));
        purchasing().saveSupplierProduct(fixture.offer(product, 1, 1, "60.00"), 0, manager);
        return fixture;
    }

    private void referenceRace(final boolean warehouseReference, final boolean creationFirst) throws Exception {
        PurchaseFixture base = purchaseFixture();
        Warehouse isolated = new Warehouse();
        isolated.setCode(uniqueCode("W"));
        isolated.setName("架空参照ロック競合試験倉庫");
        isolated = catalog.saveWarehouse(isolated, 0, manager);
        final PurchaseFixture fixture = new PurchaseFixture(base.product, base.supplier, isolated);
        final CountDownLatch holderReady = new CountDownLatch(1);
        final CountDownLatch releaseHolder = new CountDownLatch(1);
        final AtomicInteger waiterPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> holder = executor.submit(new Callable<String>() {
                public String call() {
                    return transaction().execute(new TransactionCallback<String>() {
                        public String doInTransaction(TransactionStatus status) {
                            if (creationFirst) {
                                purchasing().saveOrder(fixture.input(1), 0, warehouseActor);
                            } else {
                                deactivateReference(fixture, warehouseReference);
                            }
                            holderReady.countDown();
                            awaitRelease(releaseHolder);
                            return "committed";
                        }
                    });
                }
            });
            assertTrue("The first transaction must acquire and retain its row lock",
                    holderReady.await(20, TimeUnit.SECONDS));
            Future<String> contender = executor.submit(new Callable<String>() {
                public String call() {
                    try {
                        return transaction().execute(new TransactionCallback<String>() {
                            public String doInTransaction(TransactionStatus status) {
                                waiterPid.set(((Number) dao().session().createSQLQuery("select pg_backend_pid()")
                                        .uniqueResult()).intValue());
                                if (creationFirst) {
                                    deactivateReference(fixture, warehouseReference);
                                } else {
                                    purchasing().saveOrder(fixture.input(1), 0, warehouseActor);
                                }
                                return "unexpected success";
                            }
                        });
                    } catch (BusinessException ex) {
                        return ex.getCode();
                    }
                }
            });
            assertDatabaseBlocked(waiterPid, contender);
            releaseHolder.countDown();
            assertEquals("committed", holder.get(20, TimeUnit.SECONDS));
            String expected = creationFirst ? "catalog.inUse.purchasing"
                    : warehouseReference ? "purchasing.inactiveWarehouse" : "purchasing.inactiveProduct";
            assertEquals(expected, contender.get(20, TimeUnit.SECONDS));
            Search search = new Search();
            search.setWarehouseId(fixture.warehouse.getId());
            assertEquals(creationFirst ? 1L : 0L, purchasing().searchOrders(search, manager).getTotal());
            boolean active = warehouseReference
                    ? catalog.getWarehouse(fixture.warehouse.getId(), manager).isActive()
                    : catalog.getProduct(fixture.product.getId(), manager).isActive();
            assertEquals(creationFirst, active);
        } finally {
            releaseHolder.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private void deactivateReference(PurchaseFixture fixture, boolean warehouseReference) {
        if (warehouseReference) {
            Warehouse warehouse = catalog.getWarehouse(fixture.warehouse.getId(), manager);
            warehouse.setActive(false);
            catalog.saveWarehouse(warehouse, warehouse.getVersion(), manager);
        } else {
            Product product = catalog.getProduct(fixture.product.getId(), manager);
            product.setActive(false);
            catalog.saveProduct(product, product.getVersion(), manager);
        }
    }

    private void assertDatabaseBlocked(AtomicInteger backendPid, Future<String> contender) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        DataSource dataSource = service("dataSource", DataSource.class);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select cardinality(pg_blocking_pids(?))")) {
            while (System.nanoTime() < deadline) {
                assertFalse("The competing transaction must wait, not complete before the holder commits", contender.isDone());
                if (backendPid.get() != 0) {
                    statement.setInt(1, backendPid.get());
                    try (ResultSet rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        if (rows.getInt(1) > 0) { return; }
                    }
                }
                Thread.sleep(25L);
            }
        }
        fail("PostgreSQL did not report the expected reference-row lock wait");
    }

    private void awaitRelease(CountDownLatch release) {
        try {
            assertTrue("The test must release its transaction holder", release.await(25, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reference-lock test interrupted", ex);
        }
    }

    private PurchaseOrder approve(PurchaseOrder draft) {
        PurchaseOrder submitted = purchasing().submitOrder(draft.getId(), draft.getVersion(), warehouseActor);
        return purchasing().approveOrder(submitted.getId(), submitted.getVersion(), manager);
    }

    private PurchasingReceiptCommand command(PurchaseOrder order, int accepted, int rejected) {
        PurchasingReceiptCommand command = new PurchasingReceiptCommand();
        command.setRequestKey(uniqueCode("PR"));
        command.setOrderId(order.getId());
        command.setExpectedVersion(order.getVersion());
        command.setReceiptDate(Dates.today());
        PurchasingReceiptLineCommand line = new PurchasingReceiptLineCommand();
        line.setOrderLineId(order.getLines().get(0).getId());
        line.setAcceptedQuantity(accepted);
        line.setRejectedQuantity(rejected);
        line.setRejectionReason(rejected == 0 ? "" : "架空検品不良");
        command.getLines().add(line);
        return command;
    }

    private PurchasingReorderSuggestion suggestion(PurchaseFixture fixture) {
        List<PurchasingReorderSuggestion> suggestions = purchasing().previewReorder(fixture.warehouse.getId(), Dates.today(), manager);
        for (PurchasingReorderSuggestion suggestion : suggestions) {
            if (suggestion.getProduct().getId().equals(fixture.product.getId())) { return suggestion; }
        }
        return null;
    }

    private long onHand(final Product product, final Warehouse warehouse) {
        return transaction().execute(new TransactionCallback<Long>() {
            public Long doInTransaction(TransactionStatus status) {
                return Long.valueOf(dao().count("select sum(b.onHand) from StockBalance b"
                        + " where b.product = :product and b.warehouse = :warehouse",
                        WholesaleDao.params("product", product, "warehouse", warehouse)));
            }
        }).longValue();
    }

    private PurchasingService purchasing() { return service("purchasingService", PurchasingService.class); }
    private InventoryService inventory() { return service("inventoryService", InventoryService.class); }
    private WholesaleDao dao() { return service("wholesaleDao", WholesaleDao.class); }
    private TransactionTemplate transaction() {
        return new TransactionTemplate(service("transactionManager", PlatformTransactionManager.class));
    }

    private void expect(String code, Runnable operation) {
        try {
            operation.run();
            fail("Expected " + code);
        } catch (BusinessException ex) {
            assertEquals(code, ex.getCode());
        }
    }

    private final class PurchaseFixture {
        private final Product product;
        private final Supplier supplier;
        private final Warehouse warehouse;

        private PurchaseFixture(Product product, Supplier supplier, Warehouse warehouse) {
            this.product = product;
            this.supplier = supplier;
            this.warehouse = warehouse;
        }

        private SupplierProduct offer(Product item, int minimum, int pack, String cost) {
            SupplierProduct offer = new SupplierProduct();
            offer.setSupplier(supplier);
            offer.setProduct(item);
            offer.setValidFrom(Dates.parse("2020-01-01"));
            offer.setMinimumQuantity(minimum);
            offer.setOrderPackSize(pack);
            offer.setUnitCost(new BigDecimal(cost));
            offer.setLeadTimeDays(0);
            offer.setPreferred(true);
            return offer;
        }

        private PurchaseOrder input(int quantity) {
            PurchaseOrder input = new PurchaseOrder();
            input.setSupplier(supplier);
            input.setWarehouse(warehouse);
            input.setOrderDate(Dates.addDays(Dates.today(), -2));
            input.setExpectedDate(Dates.addDays(Dates.today(), 1));
            input.setNotes("架空仕入試験");
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setProduct(product);
            line.setQuantity(quantity);
            input.getLines().add(line);
            return input;
        }

        private PurchaseOrder draft(int quantity) { return purchasing().saveOrder(input(quantity), 0, warehouseActor); }
        private PurchaseOrder approved(int quantity) { return approve(draft(quantity)); }
    }
}
