package jp.co.tsubame.wholesale.batch.service;

import java.util.List;
import jp.co.tsubame.wholesale.batch.ImportFields;
import jp.co.tsubame.wholesale.batch.RowOutcome;
import jp.co.tsubame.wholesale.batch.RowTask;
import jp.co.tsubame.wholesale.batch.entity.BatchRow;
import jp.co.tsubame.wholesale.batch.entity.BatchRun;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Invoice;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.entity.StockReceipt;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.AuthService;
import jp.co.tsubame.wholesale.service.BaseService;
import jp.co.tsubame.wholesale.service.BillingService;
import jp.co.tsubame.wholesale.service.CatalogService;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.OrderService;

/** One real transaction encloses the core business mutation and its SUCCESS row. */
public class BatchRowService extends BaseService {
    private BatchRunService batchRunService;
    private AuthService authService;
    private CatalogService catalogService;
    private InventoryService inventoryService;
    private OrderService orderService;
    private BillingService billingService;
    private OrderImportService orderImportService;

    public void setBatchRunService(BatchRunService value) { batchRunService = value; }
    public void setAuthService(AuthService value) { authService = value; }
    public void setCatalogService(CatalogService value) { catalogService = value; }
    public void setInventoryService(InventoryService value) { inventoryService = value; }
    public void setOrderService(OrderService value) { orderService = value; }
    public void setBillingService(BillingService value) { billingService = value; }
    public void setOrderImportService(OrderImportService value) { orderImportService = value; }

    public BatchRow executeRow(Long runId, RowTask task, Actor authenticatedActor) {
        require(authenticatedActor, "BATCH");
        Actor actor = authService.getCurrentActor(authenticatedActor.getUserId());
        require(actor, "BATCH");
        BatchRun run = batchRunService.lockRunning(runId, actor);
        Checks.state(run.getCommand().equals(task.getCommand()), "batch.commandConflict", "Row command differs from run");
        BatchRow old = batchRunService.findRow(runId, task.getNumber(), actor);
        if (old != null) {
            Checks.state(old.getPayloadHash().equals(task.getHash()), "batch.rowConflict", "Row payload differs");
            return old;
        }
        RowOutcome outcome;
        if ("import-products".equals(task.getCommand())) { outcome = product(task, actor); }
        else if ("import-receipts".equals(task.getCommand())) { outcome = receipt(task, actor); }
        else if ("daily-allocation".equals(task.getCommand())) { outcome = allocation(task, actor); }
        else if ("monthly-billing".equals(task.getCommand())) { outcome = billing(task, actor); }
        else if ("import-orders".equals(task.getCommand())) { outcome = orderImportService.importDraft(task.getOrderGroup(), actor); }
        else { throw new IllegalArgumentException("Unknown row command"); }
        return batchRunService.completeRow(run, task, outcome, actor);
    }

    private RowOutcome product(RowTask task, Actor actor) {
        List<String> f = task.getFields();
        ImportFields.count(f, ImportFields.PRODUCTS.length);
        Product input = new Product();
        input.setCode(f.get(0).trim());
        input.setName(f.get(1));
        input.setUnit(f.get(2));
        input.setTaxCategory(f.get(3));
        input.setListPrice(ImportFields.money(f.get(4), "list_price"));
        input.setStandardCost(ImportFields.money(f.get(5), "standard_cost"));
        input.setPackSize(ImportFields.integer(f.get(6), "pack_size"));
        input.setReorderPoint(ImportFields.integer(f.get(7), "reorder_point"));
        input.setReorderQuantity(ImportFields.integer(f.get(8), "reorder_quantity"));
        input.setActive(ImportFields.bool(f.get(9), "active"));
        input.setNotes(f.get(10));
        dao.lockKey("batch-product-code", input.getCode());
        List<Product> matches = dao.list("from Product where code=:code",
                WholesaleDao.params("code", input.getCode()));
        int version = 0;
        if (!matches.isEmpty()) {
            Product existing = dao.lock(Product.class, matches.get(0).getId());
            input.setId(existing.getId());
            version = existing.getVersion();
        }
        Product saved = catalogService.saveProduct(input, version, actor);
        return new RowOutcome("product.saved", matches.isEmpty() ? "Product created" : "Product updated",
                "Product", saved.getId(), saved.getCode());
    }

    private RowOutcome receipt(RowTask task, Actor actor) {
        List<String> f = task.getFields();
        ImportFields.count(f, ImportFields.RECEIPTS.length);
        Warehouse warehouse = exactWarehouse(f.get(1).trim());
        Product product = exactProduct(f.get(2).trim());
        StockReceipt saved = inventoryService.receive(f.get(0), warehouse.getId(), product.getId(),
                ImportFields.integer(f.get(3), "quantity"), ImportFields.money(f.get(4), "unit_cost"),
                Dates.parse(f.get(5)), f.get(6), f.get(7), actor);
        return new RowOutcome("receipt.received", "Receipt accepted; request_key=" + saved.getRequestKey(),
                "StockReceipt", saved.getId(), saved.getNumber());
    }

    private RowOutcome allocation(RowTask task, Actor actor) {
        SalesOrder before = orderService.getOrder(task.getEntityId(), actor);
        int previous = before.getAllocatedQuantity();
        SalesOrder saved = orderService.allocate(before.getId(), before.getVersion(), actor);
        long shortage = 0;
        for (SalesOrderLine line : saved.getLines()) { shortage += line.getShortageQuantity(); }
        return new RowOutcome(shortage > 0 ? "allocation.shortage" : "allocation.complete",
                "newly_allocated=" + (saved.getAllocatedQuantity() - previous)
                + "; allocated=" + saved.getAllocatedQuantity() + "; shortage=" + shortage
                + "; status=" + saved.getStatus(), "SalesOrder", saved.getId(), saved.getNumber());
    }

    private RowOutcome billing(RowTask task, Actor actor) {
        Invoice invoice = billingService.prepare(task.getEntityId(), task.getDate(), actor);
        if (task.isFinalizeInvoice() && "DRAFT".equals(invoice.getStatus())) {
            invoice = billingService.finalizeInvoice(invoice.getId(), invoice.getVersion(), actor);
        }
        return new RowOutcome("billing." + invoice.getStatus().toLowerCase(java.util.Locale.ROOT),
                "status=" + invoice.getStatus() + "; total=" + invoice.getTotalAmount().toPlainString(),
                "Invoice", invoice.getId(), invoice.getNumber());
    }

    private Product exactProduct(String code) {
        List<Product> found = dao.list("from Product where code=:code", WholesaleDao.params("code", code));
        if (found.isEmpty()) { throw new BusinessException("csv.product", "Unknown product_code: " + code); }
        return found.get(0);
    }

    private Warehouse exactWarehouse(String code) {
        List<Warehouse> found = dao.list("from Warehouse where code=:code", WholesaleDao.params("code", code));
        if (found.isEmpty()) { throw new BusinessException("csv.warehouse", "Unknown warehouse_code: " + code); }
        return found.get(0);
    }
}
