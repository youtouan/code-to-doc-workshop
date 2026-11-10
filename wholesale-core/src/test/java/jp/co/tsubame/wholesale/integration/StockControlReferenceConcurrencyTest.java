package jp.co.tsubame.wholesale.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.StockAdjustmentCommand;
import jp.co.tsubame.wholesale.common.StockControlLineCommand;
import jp.co.tsubame.wholesale.common.StockCountCommand;
import jp.co.tsubame.wholesale.common.StockTransferCommand;
import jp.co.tsubame.wholesale.dao.InventoryLedger;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.StockAdjustmentService;
import jp.co.tsubame.wholesale.service.StockControlSupport;
import jp.co.tsubame.wholesale.service.StockCountService;
import jp.co.tsubame.wholesale.service.StockTransferService;
import org.junit.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.Assert.*;

public class StockControlReferenceConcurrencyTest extends PostgresTestSupport {
    private enum Workflow { TRANSFER, ADJUSTMENT, COUNT }

    @Test public void transferCreationWinsAgainstProductDeactivation() throws Exception {
        creationFirst(Workflow.TRANSFER, false);
    }
    @Test public void productDeactivationWinsAgainstTransferCreation() throws Exception {
        deactivationFirst(Workflow.TRANSFER, false);
    }
    @Test public void transferCreationWinsAgainstDestinationDeactivation() throws Exception {
        creationFirst(Workflow.TRANSFER, true);
    }
    @Test public void destinationDeactivationWinsAgainstTransferCreation() throws Exception {
        deactivationFirst(Workflow.TRANSFER, true);
    }
    @Test public void adjustmentCreationWinsAgainstProductDeactivation() throws Exception {
        creationFirst(Workflow.ADJUSTMENT, false);
    }
    @Test public void productDeactivationWinsAgainstAdjustmentCreation() throws Exception {
        deactivationFirst(Workflow.ADJUSTMENT, false);
    }
    @Test public void adjustmentCreationWinsAgainstWarehouseDeactivation() throws Exception {
        creationFirst(Workflow.ADJUSTMENT, true);
    }
    @Test public void warehouseDeactivationWinsAgainstAdjustmentCreation() throws Exception {
        deactivationFirst(Workflow.ADJUSTMENT, true);
    }
    @Test public void zeroStockCountCreationWinsAgainstProductDeactivation() throws Exception {
        creationFirst(Workflow.COUNT, false);
    }
    @Test public void productDeactivationWinsAgainstZeroStockCountCreation() throws Exception {
        deactivationFirst(Workflow.COUNT, false);
    }
    @Test public void zeroStockCountCreationWinsAgainstWarehouseDeactivation() throws Exception {
        creationFirst(Workflow.COUNT, true);
    }
    @Test public void warehouseDeactivationWinsAgainstZeroStockCountCreation() throws Exception {
        deactivationFirst(Workflow.COUNT, true);
    }

    private void creationFirst(final Workflow workflow, final boolean warehouse) throws Exception {
        final References references = references(workflow);
        final Gate gate = new Gate(warehouse, references.targetWarehouse(workflow).getId(), references.product.getId());
        final CountDownLatch maintenanceStarted = new CountDownLatch(1);
        final AtomicInteger maintenancePid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> creation = executor.submit(new Callable<String>() {
                @Override public String call() {
                    return transaction(new TransactionCallback<String>() {
                        @Override public String doInTransaction(TransactionStatus status) {
                            create(workflow, references, gate);
                            return "CREATED";
                        }
                    });
                }
            });
            assertTrue("Creation must reach its active-reference check", gate.checked.await(15, TimeUnit.SECONDS));
            Future<String> maintenance = executor.submit(new Callable<String>() {
                @Override public String call() {
                    return outcome(new TransactionCallback<String>() {
                        @Override public String doInTransaction(TransactionStatus status) {
                            maintenancePid.set(backendPid());
                            maintenanceStarted.countDown();
                            deactivate(workflow, references, warehouse);
                            return "DEACTIVATED";
                        }
                    });
                }
            });
            assertTrue(maintenanceStarted.await(10, TimeUnit.SECONDS));
            assertDatabaseLockWait(maintenancePid.get(), maintenance);
            gate.release.countDown();
            assertEquals("CREATED", creation.get(20, TimeUnit.SECONDS));
            assertEquals("catalog.inUse." + workflow.name().toLowerCase(java.util.Locale.ROOT),
                    maintenance.get(20, TimeUnit.SECONDS));
            assertTrue(isActive(workflow, references, warehouse));
            assertEquals(1L, documents(workflow, references));
        } finally {
            gate.release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private void deactivationFirst(final Workflow workflow, final boolean warehouse) throws Exception {
        final References references = references(workflow);
        final CountDownLatch deactivated = new CountDownLatch(1);
        final CountDownLatch commit = new CountDownLatch(1);
        final CountDownLatch creationStarted = new CountDownLatch(1);
        final AtomicInteger creationPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> maintenance = executor.submit(new Callable<String>() {
                @Override public String call() {
                    return transaction(new TransactionCallback<String>() {
                        @Override public String doInTransaction(TransactionStatus status) {
                            deactivate(workflow, references, warehouse);
                            deactivated.countDown();
                            await(commit);
                            return "DEACTIVATED";
                        }
                    });
                }
            });
            assertTrue(deactivated.await(15, TimeUnit.SECONDS));
            Future<String> creation = executor.submit(new Callable<String>() {
                @Override public String call() {
                    return outcome(new TransactionCallback<String>() {
                        @Override public String doInTransaction(TransactionStatus status) {
                            creationPid.set(backendPid());
                            creationStarted.countDown();
                            create(workflow, references, null);
                            return "CREATED";
                        }
                    });
                }
            });
            assertTrue(creationStarted.await(10, TimeUnit.SECONDS));
            assertDatabaseLockWait(creationPid.get(), creation);
            commit.countDown();
            assertEquals("DEACTIVATED", maintenance.get(20, TimeUnit.SECONDS));
            assertEquals(warehouse ? "stockControl.warehouseInactive" : "stockControl.productInactive",
                    creation.get(20, TimeUnit.SECONDS));
            assertFalse(isActive(workflow, references, warehouse));
            assertEquals(0L, documents(workflow, references));
        } finally {
            commit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private References references(Workflow workflow) {
        final References references = new References();
        references.product = newProduct(uniqueCode("REFP"), 1);
        references.source = warehouse();
        references.destination = warehouse();
        if (workflow == Workflow.COUNT) {
            transaction(new TransactionCallback<Void>() {
                @Override public Void doInTransaction(TransactionStatus status) {
                    dao().lockReferences(Collections.singleton(references.source.getId()),
                            Collections.singleton(references.product.getId()));
                    context.getBean("inventoryLedger", InventoryLedger.class)
                            .lock(references.source.getId(), references.product.getId());
                    return null;
                }
            });
        }
        return references;
    }

    private Warehouse warehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(uniqueCode("REFW"));
        warehouse.setName("Reference-lock concurrency fixture");
        warehouse.setAddress("Isolated test");
        return catalog.saveWarehouse(warehouse, 0, manager);
    }

    private void create(Workflow workflow, References references, Gate gate) {
        if (workflow == Workflow.TRANSFER) {
            StockTransferService service = gate == null
                    ? service("stockTransferService", StockTransferService.class)
                    : initialize(new PausedTransferService(gate));
            StockTransferCommand command = new StockTransferCommand();
            command.setSourceWarehouseId(references.source.getId());
            command.setDestinationWarehouseId(references.destination.getId());
            command.getLines().add(new StockControlLineCommand(references.product.getId(), 1));
            service.saveDraft(warehouseActor, command);
        } else if (workflow == Workflow.ADJUSTMENT) {
            StockAdjustmentService service = gate == null
                    ? service("stockAdjustmentService", StockAdjustmentService.class)
                    : initialize(new PausedAdjustmentService(gate));
            StockAdjustmentCommand command = new StockAdjustmentCommand();
            command.setWarehouseId(references.source.getId());
            command.setProductId(references.product.getId());
            command.setQuantityChange(1);
            command.setReason("Independent stock review");
            service.propose(warehouseActor, command);
        } else {
            StockCountService service = gate == null
                    ? service("stockCountService", StockCountService.class)
                    : initialize(new PausedCountService(gate));
            StockCountCommand command = new StockCountCommand();
            command.setWarehouseId(references.source.getId());
            command.setProductIds(Arrays.asList(references.product.getId()));
            service.begin(warehouseActor, command);
        }
    }

    private void deactivate(Workflow workflow, References references, boolean warehouse) {
        if (warehouse) {
            Warehouse inactive = references.targetWarehouse(workflow);
            inactive.setActive(false);
            catalog.saveWarehouse(inactive, inactive.getVersion(), manager);
        } else {
            Product inactive = references.product;
            inactive.setActive(false);
            catalog.saveProduct(inactive, inactive.getVersion(), manager);
        }
    }

    private boolean isActive(Workflow workflow, References references, boolean warehouse) {
        return warehouse ? catalog.getWarehouse(references.targetWarehouse(workflow).getId(), manager).isActive()
                : catalog.getProduct(references.product.getId(), manager).isActive();
    }

    private long documents(final Workflow workflow, final References references) {
        return transaction(new TransactionCallback<Long>() {
            @Override public Long doInTransaction(TransactionStatus status) {
                String hql = workflow == Workflow.TRANSFER
                        ? "select count(l.id) from StockTransferLine l where l.product.id=:product"
                        : workflow == Workflow.ADJUSTMENT
                        ? "select count(a.id) from StockAdjustment a where a.product.id=:product"
                        : "select count(l.id) from StockCountLine l where l.balance.product.id=:product";
                return dao().count(hql, WholesaleDao.params("product", references.product.getId()));
            }
        });
    }

    private <T extends StockControlSupport> T initialize(T service) {
        service.setDao(dao());
        service.setLedger(context.getBean("inventoryLedger", InventoryLedger.class));
        return service;
    }

    private WholesaleDao dao() { return context.getBean("wholesaleDao", WholesaleDao.class); }

    private int backendPid() {
        return ((Number) dao().session().createSQLQuery("select pg_backend_pid()").uniqueResult()).intValue();
    }

    private <T> T transaction(TransactionCallback<T> callback) {
        return new TransactionTemplate(context.getBean("transactionManager", PlatformTransactionManager.class))
                .execute(callback);
    }

    private String outcome(TransactionCallback<String> callback) {
        try {
            return transaction(callback);
        } catch (BusinessException failure) {
            return failure.getCode();
        }
    }

    private void assertDatabaseLockWait(int pid, Future<?> waiter) throws Exception {
        DataSource source = context.getBean("dataSource", DataSource.class);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try (Connection connection = source.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "select wait_event_type from pg_stat_activity where pid=?")) {
                statement.setInt(1, pid);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next() && "Lock".equals(result.getString(1))) { return; }
                }
            }
            assertFalse("Reference contender completed instead of waiting for the master row lock", waiter.isDone());
            Thread.sleep(20);
        }
        fail("No actual PostgreSQL master-row lock wait observed for backend " + pid);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue("Timed out waiting for controlled transaction ordering", latch.await(20, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static final class References {
        private Product product;
        private Warehouse source;
        private Warehouse destination;
        private Warehouse targetWarehouse(Workflow workflow) {
            return workflow == Workflow.TRANSFER ? destination : source;
        }
    }

    private static final class Gate {
        private final boolean warehouse;
        private final Long warehouseId;
        private final Long productId;
        private final CountDownLatch checked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private Gate(boolean warehouse, Long warehouseId, Long productId) {
            this.warehouse = warehouse;
            this.warehouseId = warehouseId;
            this.productId = productId;
        }
        private void after(boolean warehouseCheck, Long id) {
            if (warehouse == warehouseCheck && (warehouseCheck ? warehouseId : productId).equals(id)) {
                checked.countDown();
                await(release);
            }
        }
    }

    // Pause after the real active check, before insert: without master locks maintenance could slip through this window.
    private static final class PausedTransferService extends StockTransferService {
        private final Gate gate;
        private PausedTransferService(Gate gate) { this.gate = gate; }
        @Override protected Warehouse activeWarehouse(Long id) {
            Warehouse result = super.activeWarehouse(id); gate.after(true, id); return result;
        }
        @Override protected Product activeProduct(Long id) {
            Product result = super.activeProduct(id); gate.after(false, id); return result;
        }
    }

    private static final class PausedAdjustmentService extends StockAdjustmentService {
        private final Gate gate;
        private PausedAdjustmentService(Gate gate) { this.gate = gate; }
        @Override protected Warehouse activeWarehouse(Long id) {
            Warehouse result = super.activeWarehouse(id); gate.after(true, id); return result;
        }
        @Override protected Product activeProduct(Long id) {
            Product result = super.activeProduct(id); gate.after(false, id); return result;
        }
    }

    private static final class PausedCountService extends StockCountService {
        private final Gate gate;
        private PausedCountService(Gate gate) { this.gate = gate; }
        @Override protected Warehouse activeWarehouse(Long id) {
            Warehouse result = super.activeWarehouse(id); gate.after(true, id); return result;
        }
        @Override protected Product activeProduct(Long id) {
            Product result = super.activeProduct(id); gate.after(false, id); return result;
        }
    }
}
