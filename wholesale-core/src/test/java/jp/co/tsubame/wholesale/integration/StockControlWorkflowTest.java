package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.StockAdjustmentCommand;
import jp.co.tsubame.wholesale.common.StockControlLineCommand;
import jp.co.tsubame.wholesale.common.StockCountCommand;
import jp.co.tsubame.wholesale.common.StockCountEntry;
import jp.co.tsubame.wholesale.common.StockReconciliationRow;
import jp.co.tsubame.wholesale.common.StockTransferCommand;
import jp.co.tsubame.wholesale.common.StockTransferReceiptCommand;
import jp.co.tsubame.wholesale.common.StockValuationRow;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.StockAdjustment;
import jp.co.tsubame.wholesale.entity.StockCount;
import jp.co.tsubame.wholesale.entity.StockTransfer;
import jp.co.tsubame.wholesale.entity.StockTransferReceipt;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.StockAdjustmentService;
import jp.co.tsubame.wholesale.service.StockControlReportService;
import jp.co.tsubame.wholesale.service.StockCountService;
import jp.co.tsubame.wholesale.service.StockTransferService;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import static org.junit.Assert.*;

public class StockControlWorkflowTest extends PostgresTestSupport {
    @Test
    public void moduleServicesAreRealTransactionalXmlBeans() {
        assertTrue(AopUtils.isAopProxy(transfers()));
        assertTrue(AopUtils.isAopProxy(adjustments()));
        assertTrue(AopUtils.isAopProxy(counts()));
        assertTrue(AopUtils.isAopProxy(reports()));
    }

    @Test
    public void transferDispatchPartialReceiptAndManagerLossCloseOnlyActualTransit() throws Exception {
        final Fixture fixture = fixture(30);
        StockTransfer transfer = dispatched(fixture, 12);
        assertEquals(18, physical(21L, fixture.product.getId(), "on_hand"));
        assertEquals(0, physical(22L, fixture.product.getId(), "on_hand"));
        assertEquals(12L, transfer.getInTransitQuantity());
        transfer = transfers().receive(warehouseActor, transfer.getId(), transfer.getVersion(), receipt(fixture, 5));
        assertEquals("PART_RECEIVED", transfer.getStatus());
        assertEquals(5, physical(22L, fixture.product.getId(), "on_hand"));
        assertEquals(7L, transfer.getInTransitQuantity());
        final StockTransfer partial = transfer;
        expect("permission.denied", new Runnable() {
            @Override public void run() {
                transfers().reconcileLoss(warehouseActor, partial.getId(), partial.getVersion(), receipt(fixture, 7));
            }
        });
        StockTransferReceiptCommand loss = receipt(fixture, 7);
        loss.setNote("運送会社の事故報告により破損を確認");
        transfer = transfers().reconcileLoss(manager, transfer.getId(), transfer.getVersion(), loss);
        assertEquals("RECONCILED", transfer.getStatus());
        assertEquals(0L, transfer.getInTransitQuantity());
        assertEquals(18, physical(21L, fixture.product.getId(), "on_hand"));
        assertEquals(5, physical(22L, fixture.product.getId(), "on_hand"));
        assertEquals(7, transfer.getLines().get(0).getLostQuantity());
        Page<StockTransferReceipt> receipts = transfers().listReceipts(warehouseActor, transfer.getId(), new Search());
        assertEquals(2L, receipts.getTotal());
        assertEquals(new BigDecimal("420.00"), receipts.getItems().get(0).getValue());
        assertTrue(reconciliation(fixture, 21L).isConsistent());
        assertTrue(reconciliation(fixture, 22L).isConsistent());
    }

    @Test
    public void identicalReceiptRetrySucceedsWithOldVersionButChangedPayloadFails() throws Exception {
        final Fixture fixture = fixture(10);
        final StockTransfer dispatched = dispatched(fixture, 10);
        final StockTransferReceiptCommand input = receipt(fixture, 4);
        StockTransfer first = transfers().receive(warehouseActor, dispatched.getId(), dispatched.getVersion(), input);
        StockTransfer retry = transfers().receive(warehouseActor, dispatched.getId(), dispatched.getVersion(), input);
        assertEquals(first.getVersion(), retry.getVersion());
        assertEquals(4, physical(22L, fixture.product.getId(), "on_hand"));
        assertEquals(1L, transfers().listReceipts(warehouseActor, first.getId(), new Search()).getTotal());
        input.getLines().get(0).setQuantity(5);
        expect("idempotency.conflict", new Runnable() {
            @Override public void run() {
                transfers().receive(warehouseActor, dispatched.getId(), dispatched.getVersion(), input);
            }
        });
        assertEquals(4, physical(22L, fixture.product.getId(), "on_hand"));
    }

    @Test
    public void overReceiptAndCancellationAfterDispatchCannotChangeStocks() throws Exception {
        final Fixture fixture = fixture(10);
        final StockTransfer transfer = dispatched(fixture, 6);
        expect("stockControl.overReceipt", new Runnable() {
            @Override public void run() {
                transfers().receive(warehouseActor, transfer.getId(), transfer.getVersion(), receipt(fixture, 7));
            }
        });
        expect("stockControl.state", new Runnable() {
            @Override public void run() {
                transfers().cancel(warehouseActor, transfer.getId(), transfer.getVersion(), "取消");
            }
        });
        assertEquals(4, physical(21L, fixture.product.getId(), "on_hand"));
        assertEquals(0, physical(22L, fixture.product.getId(), "on_hand"));
        assertEquals(0L, transfers().listReceipts(warehouseActor, transfer.getId(), new Search()).getTotal());
    }

    @Test
    public void draftsCanReplaceLinesAndBeCancelledWithoutReservingStock() throws Exception {
        Fixture fixture = fixture(10);
        StockTransfer draft = transfers().saveDraft(warehouseActor, transferCommand(fixture, 4));
        StockTransferCommand replacement = transferCommand(fixture, 7);
        replacement.setId(draft.getId());
        replacement.setExpectedVersion(draft.getVersion());
        draft = transfers().saveDraft(warehouseActor, replacement);
        assertEquals(1, draft.getLines().size());
        assertEquals(7, draft.getLines().get(0).getQuantity());
        assertEquals(0, physical(21L, fixture.product.getId(), "reserved"));
        draft = transfers().submit(warehouseActor, draft.getId(), draft.getVersion());
        draft = transfers().approve(manager, draft.getId(), draft.getVersion());
        draft = transfers().cancel(warehouseActor, draft.getId(), draft.getVersion(), "補充計画を延期");
        assertEquals("CANCELLED", draft.getStatus());
        assertEquals(10, physical(21L, fixture.product.getId(), "on_hand"));
    }

    @Test
    public void sameWarehouseInactiveProductAndMissingDestinationAreRejected() {
        final Fixture fixture = fixture(10);
        final StockTransferCommand same = transferCommand(fixture, 1);
        same.setDestinationWarehouseId(21L);
        expect("stockControl.sameWarehouse", new Runnable() {
            @Override public void run() { transfers().saveDraft(warehouseActor, same); }
        });
        final StockTransferCommand missing = transferCommand(fixture, 1);
        missing.setDestinationWarehouseId(Long.MAX_VALUE);
        expect("notFound", new Runnable() {
            @Override public void run() { transfers().saveDraft(warehouseActor, missing); }
        });
        final Product inactive = newProduct(uniqueCode("INA"), 1);
        inactive.setActive(false);
        catalog.saveProduct(inactive, inactive.getVersion(), manager);
        final StockTransferCommand disabled = transferCommand(fixture, 1);
        disabled.getLines().get(0).setProductId(inactive.getId());
        expect("stockControl.productInactive", new Runnable() {
            @Override public void run() { transfers().saveDraft(warehouseActor, disabled); }
        });
    }

    @Test
    public void transferSelfApprovalAndStaleCommandsAreRejected() {
        Fixture fixture = fixture(10);
        StockTransfer draft = transfers().saveDraft(manager, transferCommand(fixture, 1));
        final StockTransfer submitted = transfers().submit(manager, draft.getId(), draft.getVersion());
        expect("approval.self", new Runnable() {
            @Override public void run() { transfers().approve(manager, submitted.getId(), submitted.getVersion()); }
        });
        expect("concurrent.update", new Runnable() {
            @Override public void run() { transfers().cancel(manager, submitted.getId(), 0, "古い画面"); }
        });
    }

    @Test
    public void dispatchNeverConsumesOrderReservationsAndRollsBackAllLinesOnShortage() throws Exception {
        Fixture first = fixture(10);
        final Fixture second = fixture(1);
        SalesOrder order = allocated(first, 8);
        final StockTransfer transfer = approved(first, 3);
        expect("inventory.insufficient", new Runnable() {
            @Override public void run() { transfers().dispatch(warehouseActor, transfer.getId(), transfer.getVersion()); }
        });
        assertEquals(10, physical(21L, first.product.getId(), "on_hand"));
        assertEquals(8, physical(21L, first.product.getId(), "reserved"));
        assertEquals(8, orders().getOrder(order.getId(), sales).getLines().get(0).getAllocatedQuantity());
        StockTransferCommand two = transferCommand(first, 2);
        two.getLines().add(new StockControlLineCommand(second.product.getId(), 2));
        StockTransfer document = transfers().saveDraft(warehouseActor, two);
        document = transfers().submit(warehouseActor, document.getId(), document.getVersion());
        final StockTransfer multi = transfers().approve(manager, document.getId(), document.getVersion());
        expect("inventory.insufficient", new Runnable() {
            @Override public void run() { transfers().dispatch(warehouseActor, multi.getId(), multi.getVersion()); }
        });
        assertEquals(10, physical(21L, first.product.getId(), "on_hand"));
        assertEquals(1, physical(21L, second.product.getId(), "on_hand"));
        assertEquals("APPROVED", transfers().get(warehouseActor, multi.getId()).getStatus());
    }

    @Test
    public void dispatchSnapshotsCostAndValuationDoesNotDoubleCountIncomingTransit() throws Exception {
        Fixture fixture = fixture(20);
        StockTransfer transfer = dispatched(fixture, 10);
        Product product = catalog.getProduct(fixture.product.getId(), manager);
        product.setStandardCost(new BigDecimal("80.00"));
        catalog.saveProduct(product, product.getVersion(), manager);
        StockValuationRow source = valuation(fixture, 21L);
        StockValuationRow destination = valuation(fixture, 22L);
        assertEquals(new BigDecimal("800.00"), source.getOnHandValue());
        assertEquals(new BigDecimal("600.00"), source.getOutgoingTransitValue());
        assertEquals(new BigDecimal("1400.00"), source.getAttributedInventoryValue());
        assertEquals(new BigDecimal("600.00"), destination.getIncomingTransitValue());
        assertEquals(new BigDecimal("0.00"), destination.getAttributedInventoryValue());
        assertEquals(0, physical(22L, fixture.product.getId(), "on_hand"));
        assertEquals(0L, scalar("select count(*) from stock_balance where warehouse_id=22 and product_id=?",
                fixture.product.getId()));
        transfer = transfers().receive(warehouseActor, transfer.getId(), transfer.getVersion(), receipt(fixture, 10));
        assertEquals("COMPLETED", transfer.getStatus());
        assertEquals(new BigDecimal("60.00"), transfer.getLines().get(0).getUnitCost());
        assertEquals(new BigDecimal("800.00"), valuation(fixture, 22L).getOnHandValue());
    }

    @Test
    public void adjustmentRequiresIndependentApprovalAndProtectsReservedStock() throws Exception {
        Fixture fixture = fixture(10);
        allocated(fixture, 8);
        final StockAdjustment tooLarge = adjustments().propose(warehouseActor, adjustment(fixture, -3));
        expect("inventory.insufficient", new Runnable() {
            @Override public void run() {
                adjustments().approve(manager, tooLarge.getId(), tooLarge.getVersion(), "破損確認");
            }
        });
        assertEquals("PROPOSED", adjustments().get(manager, tooLarge.getId()).getStatus());
        StockAdjustment allowed = adjustments().propose(warehouseActor, adjustment(fixture, -2));
        allowed = adjustments().approve(manager, allowed.getId(), allowed.getVersion(), "破損写真確認");
        assertEquals("APPROVED", allowed.getStatus());
        assertEquals(8, physical(21L, fixture.product.getId(), "on_hand"));
        assertEquals(8, physical(21L, fixture.product.getId(), "reserved"));
        final StockAdjustment self = adjustments().propose(manager, adjustment(fixture, 1));
        expect("approval.self", new Runnable() {
            @Override public void run() { adjustments().approve(manager, self.getId(), self.getVersion(), "自己承認"); }
        });
        StockAdjustment rejected = adjustments().reject(admin, self.getId(), self.getVersion(), "再確認が必要");
        assertEquals("REJECTED", rejected.getStatus());
        assertTrue(reconciliation(fixture, 21L).isConsistent());
    }

    @Test
    public void adjustmentsCannotBeApprovedTwiceOrWithoutReasons() throws Exception {
        final Fixture fixture = fixture(10);
        final StockAdjustmentCommand noReason = adjustment(fixture, 1);
        noReason.setReason(" ");
        expect("validation.required", new Runnable() {
            @Override public void run() { adjustments().propose(warehouseActor, noReason); }
        });
        StockAdjustment proposal = adjustments().propose(warehouseActor, adjustment(fixture, 2));
        final StockAdjustment approved = adjustments().approve(manager, proposal.getId(), proposal.getVersion(), "数量確認");
        expect("stockControl.state", new Runnable() {
            @Override public void run() {
                adjustments().approve(manager, approved.getId(), approved.getVersion(), "再承認");
            }
        });
        assertEquals(12, physical(21L, fixture.product.getId(), "on_hand"));
    }

    @Test
    public void countBlocksReceiptsAndOverlappingCountsUntilExplicitAuditedCancellation() throws Exception {
        final Fixture fixture = fixture(10);
        StockCount count = counts().begin(warehouseActor, countCommand(fixture));
        assertEquals(1, physical(21L, fixture.product.getId(), "blocked"));
        expect("inventory.blocked", new Runnable() {
            @Override public void run() { counts().begin(warehouseActor, countCommand(fixture)); }
        });
        expect("inventory.blocked", new Runnable() {
            @Override public void run() {
                inventory().receive(uniqueCode("BLK"), 21L, fixture.product.getId(), 1,
                        new BigDecimal("60.00"), Dates.today(), "blocked", "", warehouseActor);
            }
        });
        count = counts().cancel(warehouseActor, count.getId(), count.getVersion(), "営業中のため延期");
        assertEquals("CANCELLED", count.getStatus());
        assertEquals(0, physical(21L, fixture.product.getId(), "blocked"));
        assertEquals(10, physical(21L, fixture.product.getId(), "on_hand"));
        assertEquals(1L, scalar("select count(*) from audit_event where entity_id=? and operation='COUNT_CANCEL_UNBLOCK'",
                count.getId()));
    }

    @Test
    public void uncountedCannotBeReviewedButExplicitZeroProducesARealNegativeMovement() throws Exception {
        Fixture fixture = fixture(7);
        final StockCount begun = counts().begin(warehouseActor, countCommand(fixture));
        assertEquals(1, begun.getUncountedLines());
        expect("stockControl.countQuantity", new Runnable() {
            @Override public void run() { counts().review(warehouseActor, begun.getId(), begun.getVersion()); }
        });
        StockCount count = counts().record(warehouseActor, begun.getId(), begun.getVersion(),
                entries(begun, 0, "棚を全数確認し欠品"));
        assertEquals(0, count.getUncountedLines());
        assertEquals(1, count.getDiscrepancyLines());
        count = counts().review(warehouseActor, count.getId(), count.getVersion());
        count = counts().approve(manager, count.getId(), count.getVersion());
        assertEquals("APPROVED", count.getStatus());
        assertEquals(0, physical(21L, fixture.product.getId(), "on_hand"));
        assertEquals(0, physical(21L, fixture.product.getId(), "blocked"));
        assertEquals(1L, scalar("select count(*) from stock_movement where document_type='STOCK_COUNT' and document_id=?",
                count.getId()));
        assertTrue(reconciliation(fixture, 21L).isConsistent());
    }

    @Test
    public void equalCountUnblocksWithoutAnInvalidZeroLedgerMovement() throws Exception {
        Fixture fixture = fixture(7);
        StockCount count = counts().begin(warehouseActor, countCommand(fixture));
        count = counts().record(warehouseActor, count.getId(), count.getVersion(), entries(count, 7, ""));
        count = counts().review(warehouseActor, count.getId(), count.getVersion());
        count = counts().approve(manager, count.getId(), count.getVersion());
        assertEquals(7, physical(21L, fixture.product.getId(), "on_hand"));
        assertEquals(0, physical(21L, fixture.product.getId(), "blocked"));
        assertEquals(0L, scalar("select count(*) from stock_movement where document_type='STOCK_COUNT' and document_id=?",
                count.getId()));
    }

    @Test
    public void countSnapshotChangesRejectApprovalAndCancellationStillReleasesItsHold() throws Exception {
        final Fixture fixture = fixture(7);
        StockCount count = counts().begin(warehouseActor, countCommand(fixture));
        count = counts().record(warehouseActor, count.getId(), count.getVersion(), entries(count, 6, "差異確認"));
        final StockCount reviewed = counts().review(warehouseActor, count.getId(), count.getVersion());
        update("update stock_balance set version=version+1 where warehouse_id=21 and product_id=?", fixture.product.getId());
        expect("stockControl.countSnapshot", new Runnable() {
            @Override public void run() { counts().approve(manager, reviewed.getId(), reviewed.getVersion()); }
        });
        assertEquals(7, physical(21L, fixture.product.getId(), "on_hand"));
        assertEquals(1, physical(21L, fixture.product.getId(), "blocked"));
        count = counts().cancel(warehouseActor, reviewed.getId(), reviewed.getVersion(), "外部更新を調査");
        assertEquals(0, physical(21L, fixture.product.getId(), "blocked"));
        assertEquals("CANCELLED", count.getStatus());
    }

    @Test
    public void countRecordsAreVersionedAndForeignLinesNeverLeakBetweenCounts() {
        Fixture first = fixture(7);
        Fixture second = fixture(9);
        final StockCount count = counts().begin(warehouseActor, countCommand(first));
        StockCount other = counts().begin(warehouseActor, countCommand(second));
        final List<StockCountEntry> foreign = entries(other, 9, "");
        expect("stockControl.foreignLine", new Runnable() {
            @Override public void run() {
                counts().record(warehouseActor, count.getId(), count.getVersion(), foreign);
            }
        });
        StockCount updated = counts().record(warehouseActor, count.getId(), count.getVersion(), entries(count, 7, ""));
        expect("concurrent.update", new Runnable() {
            @Override public void run() {
                counts().record(warehouseActor, count.getId(), count.getVersion(), entries(count, 7, ""));
            }
        });
        counts().cancel(warehouseActor, updated.getId(), updated.getVersion(), "試験終了");
        counts().cancel(warehouseActor, other.getId(), other.getVersion(), "試験終了");
    }

    @Test
    public void reviewedCountsCanBeReopenedButCannotConsumeReservations() {
        Fixture fixture = fixture(7);
        allocated(fixture, 6);
        StockCount count = counts().begin(warehouseActor, countCommand(fixture));
        count = counts().record(warehouseActor, count.getId(), count.getVersion(), entries(count, 5, "不足を記録"));
        assertEquals(Integer.valueOf(1), count.getLines().get(0).getReservationShortage());
        assertEquals(1, count.getReservationShortageLines());
        final StockCount shortCount = counts().review(warehouseActor, count.getId(), count.getVersion());
        expect("inventory.insufficient", new Runnable() {
            @Override public void run() {
                counts().approve(manager, shortCount.getId(), shortCount.getVersion());
            }
        });
        count = counts().reopen(warehouseActor, shortCount.getId(), shortCount.getVersion(), "再計数");
        assertEquals("COUNTING", count.getStatus());
        assertNull(count.getReviewedAt());
        counts().cancel(warehouseActor, count.getId(), count.getVersion(), "再配置後に実施");
    }

    @Test
    public void reconciliationExposesPhysicalAndReservationCorruptionRatherThanBalancingItAway() throws Exception {
        Fixture fixture = fixture(10);
        assertTrue(reconciliation(fixture, 21L).isConsistent());
        update("update stock_balance set on_hand=on_hand+1,reserved=reserved+1 where warehouse_id=21 and product_id=?",
                fixture.product.getId());
        try {
            StockReconciliationRow mismatch = reconciliation(fixture, 21L);
            assertFalse(mismatch.isConsistent());
            assertEquals(1L, mismatch.getStockDifference());
            assertEquals(1L, mismatch.getReservedLedgerDifference());
            assertEquals(1L, mismatch.getReservationDifference());
            Search filter = productSearch(fixture, 21L);
            filter.setStatus("MISMATCH");
            assertEquals(1L, reports().listReconciliation(manager, filter).getTotal());
            filter.setStatus("OK");
            assertEquals(0L, reports().listReconciliation(manager, filter).getTotal());
        } finally {
            update("update stock_balance set on_hand=on_hand-1,reserved=reserved-1 where warehouse_id=21 and product_id=?",
                    fixture.product.getId());
        }
    }

    @Test
    public void reconciliationChecksEveryMovementSnapshotAndDoesNotJustTrustTheCurrentTotal() throws Exception {
        Fixture fixture = fixture(10);
        Long movementId = scalar("select max(m.id) from stock_movement m join stock_balance b on b.id=m.balance_id "
                + "where b.warehouse_id=21 and b.product_id=?", fixture.product.getId());
        update("update stock_movement set on_hand_after=on_hand_after+1 where id=?", movementId);
        try {
            StockReconciliationRow row = reconciliation(fixture, 21L);
            assertEquals(0L, row.getStockDifference());
            assertEquals(1L, row.getBrokenMovementSnapshots());
            assertFalse(row.isConsistent());
        } finally {
            update("update stock_movement set on_hand_after=on_hand_after-1 where id=?", movementId);
        }
    }

    @Test
    public void missingReservationQuantitiesRemainVisibleAgainstOrderAllocation() throws Exception {
        Fixture fixture = fixture(10);
        SalesOrder order = allocated(fixture, 4);
        Long lineId = order.getLines().get(0).getId();
        update("update stock_reservation set quantity=0 where order_line_id=?", lineId);
        try {
            StockReconciliationRow row = reconciliation(fixture, 21L);
            assertFalse(row.isConsistent());
            assertEquals(4L, row.getReserved());
            assertEquals(0L, row.getReservationQuantity());
            assertEquals(4L, row.getAllocatedOrderQuantity());
            assertEquals(1L, row.getInvalidReservations());
        } finally {
            update("update stock_reservation set quantity=4 where order_line_id=?", lineId);
        }
    }

    @Test
    public void countSelectionRejectsMissingBalancesRatherThanInventingZeroStock() {
        Fixture fixture = fixture(10);
        Product neverReceived = newProduct(uniqueCode("ZERO"), 1);
        final StockCountCommand command = countCommand(fixture);
        command.setProductIds(Arrays.asList(fixture.product.getId(), neverReceived.getId()));
        expect("stockControl.countMissingBalance", new Runnable() {
            @Override public void run() { counts().begin(warehouseActor, command); }
        });
    }

    @Test
    public void concurrentTransfersCannotOverdrawTheSameSource() throws Exception {
        final Fixture fixture = fixture(10);
        final StockTransfer first = approved(fixture, 7);
        final StockTransfer second = approved(fixture, 7);
        final AtomicInteger invocation = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();
        List<StockTransfer> results = race(new Callable<StockTransfer>() {
            @Override public StockTransfer call() {
                StockTransfer transfer = invocation.getAndIncrement() == 0 ? first : second;
                try {
                    return transfers().dispatch(warehouseActor, transfer.getId(), transfer.getVersion());
                } catch (BusinessException failure) {
                    assertEquals("inventory.insufficient", failure.getCode());
                    failures.incrementAndGet();
                    return null;
                }
            }
        });
        assertEquals(1, failures.get());
        assertTrue((results.get(0) == null) != (results.get(1) == null));
        assertEquals(3, physical(21L, fixture.product.getId(), "on_hand"));
        assertTrue(reconciliation(fixture, 21L).isConsistent());
    }

    @Test
    public void concurrentReceiptsWithTheSameKeyPostExactlyOnce() throws Exception {
        final Fixture fixture = fixture(12);
        final StockTransfer transfer = dispatched(fixture, 10);
        final StockTransferReceiptCommand command = receipt(fixture, 6);
        List<StockTransfer> results = race(new Callable<StockTransfer>() {
            @Override public StockTransfer call() {
                return transfers().receive(warehouseActor, transfer.getId(), transfer.getVersion(), command);
            }
        });
        assertEquals(2, results.size());
        assertEquals(6, physical(22L, fixture.product.getId(), "on_hand"));
        assertEquals(4L, transfers().get(warehouseActor, transfer.getId()).getInTransitQuantity());
        assertEquals(1L, transfers().listReceipts(warehouseActor, transfer.getId(), new Search()).getTotal());
    }

    private StockTransferService transfers() { return service("stockTransferService", StockTransferService.class); }
    private StockAdjustmentService adjustments() { return service("stockAdjustmentService", StockAdjustmentService.class); }
    private StockCountService counts() { return service("stockCountService", StockCountService.class); }
    private StockControlReportService reports() { return service("stockControlReportService", StockControlReportService.class); }
    private InventoryService inventory() { return service("inventoryService", InventoryService.class); }
    private OrderService orders() { return service("orderService", OrderService.class); }

    private StockTransferCommand transferCommand(Fixture fixture, int quantity) {
        StockTransferCommand command = new StockTransferCommand();
        command.setSourceWarehouseId(21L);
        command.setDestinationWarehouseId(22L);
        command.setNote("stock-control isolated fixture");
        command.getLines().add(new StockControlLineCommand(fixture.product.getId(), quantity));
        return command;
    }

    private StockTransfer approved(Fixture fixture, int quantity) {
        StockTransfer transfer = transfers().saveDraft(warehouseActor, transferCommand(fixture, quantity));
        transfer = transfers().submit(warehouseActor, transfer.getId(), transfer.getVersion());
        return transfers().approve(manager, transfer.getId(), transfer.getVersion());
    }

    private StockTransfer dispatched(Fixture fixture, int quantity) {
        StockTransfer transfer = approved(fixture, quantity);
        return transfers().dispatch(warehouseActor, transfer.getId(), transfer.getVersion());
    }

    private StockTransferReceiptCommand receipt(Fixture fixture, int quantity) {
        StockTransferReceiptCommand command = new StockTransferReceiptCommand();
        command.setRequestKey(uniqueCode("TRR"));
        command.getLines().add(new StockControlLineCommand(fixture.product.getId(), quantity));
        return command;
    }

    private StockAdjustmentCommand adjustment(Fixture fixture, int quantity) {
        StockAdjustmentCommand command = new StockAdjustmentCommand();
        command.setWarehouseId(21L);
        command.setProductId(fixture.product.getId());
        command.setQuantityChange(quantity);
        command.setReason("検品差異を確認");
        return command;
    }

    private StockCountCommand countCommand(Fixture fixture) {
        StockCountCommand command = new StockCountCommand();
        command.setWarehouseId(21L);
        command.setProductIds(Arrays.asList(fixture.product.getId()));
        return command;
    }

    private List<StockCountEntry> entries(StockCount count, int quantity, String note) {
        StockCountEntry entry = new StockCountEntry();
        entry.setLineId(count.getLines().get(0).getId());
        entry.setCountedQuantity(quantity);
        entry.setNote(note);
        return Arrays.asList(entry);
    }

    private SalesOrder allocated(Fixture fixture, int quantity) {
        SalesOrder order = fixture.order(quantity);
        order = orders().submit(order.getId(), order.getVersion(), sales);
        order = orders().approve(order.getId(), order.getVersion(), manager);
        return orders().allocate(order.getId(), order.getVersion(), warehouseActor);
    }

    private Search productSearch(Fixture fixture, Long warehouseId) {
        Search search = new Search();
        search.setWarehouseId(warehouseId);
        search.setText(fixture.product.getCode());
        return search;
    }

    private StockReconciliationRow reconciliation(Fixture fixture, Long warehouseId) {
        Page<StockReconciliationRow> page = reports().listReconciliation(manager, productSearch(fixture, warehouseId));
        assertEquals(1L, page.getTotal());
        return page.getItems().get(0);
    }

    private StockValuationRow valuation(Fixture fixture, Long warehouseId) {
        Page<StockValuationRow> page = reports().listValuation(manager, productSearch(fixture, warehouseId));
        assertEquals(1L, page.getTotal());
        return page.getItems().get(0);
    }

    private long physical(Long warehouseId, Long productId, String column) throws Exception {
        assertTrue(Arrays.asList("on_hand", "reserved", "blocked").contains(column));
        String expression = "blocked".equals(column) ? "case when blocked then 1 else 0 end" : column;
        return scalar("select coalesce((select " + expression + " from stock_balance where warehouse_id="
                + warehouseId + " and product_id=?),0)", productId);
    }

    private long scalar(String sql, Long id) throws Exception {
        DataSource source = context.getBean("dataSource", DataSource.class);
        try (Connection connection = source.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private void update(String sql, Long id) throws Exception {
        DataSource source = context.getBean("dataSource", DataSource.class);
        try (Connection connection = source.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private List<StockTransfer> race(final Callable<StockTransfer> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<StockTransfer>> tasks = new ArrayList<Future<StockTransfer>>();
            for (int i = 0; i < 2; i++) {
                tasks.add(executor.submit(new Callable<StockTransfer>() {
                    @Override public StockTransfer call() throws Exception {
                        ready.countDown();
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        return action.call();
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<StockTransfer> result = new ArrayList<StockTransfer>();
            for (Future<StockTransfer> task : tasks) { result.add(task.get(30, TimeUnit.SECONDS)); }
            return result;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private void expect(String code, Runnable action) {
        try {
            action.run();
            fail("Expected " + code);
        } catch (BusinessException failure) {
            assertEquals(code, failure.getCode());
        }
    }
}
