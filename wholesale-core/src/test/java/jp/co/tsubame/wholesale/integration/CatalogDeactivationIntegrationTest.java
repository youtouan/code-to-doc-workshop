package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.Collections;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.common.ReturnLineInput;
import jp.co.tsubame.wholesale.common.ShipmentLineInput;
import jp.co.tsubame.wholesale.common.StockAdjustmentCommand;
import jp.co.tsubame.wholesale.common.StockControlLineCommand;
import jp.co.tsubame.wholesale.common.StockCountCommand;
import jp.co.tsubame.wholesale.common.StockTransferCommand;
import jp.co.tsubame.wholesale.common.StockTransferReceiptCommand;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.PurchaseOrder;
import jp.co.tsubame.wholesale.entity.PurchaseOrderLine;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesReturn;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.StockAdjustment;
import jp.co.tsubame.wholesale.entity.StockCount;
import jp.co.tsubame.wholesale.entity.StockTransfer;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.entity.SupplierProduct;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.PurchasingService;
import jp.co.tsubame.wholesale.service.ShippingService;
import jp.co.tsubame.wholesale.service.StockAdjustmentService;
import jp.co.tsubame.wholesale.service.StockCountService;
import jp.co.tsubame.wholesale.service.StockTransferService;
import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogDeactivationIntegrationTest extends PostgresTestSupport {
    @Test
    public void unusedProductAndEmptyWarehouseCanBeDeactivatedAndReactivated() {
        Product product = newProduct(uniqueCode("IN"), 1);
        Warehouse warehouse = warehouse();
        deactivateProduct(product);
        deactivateWarehouse(warehouse);
        product = catalog.getProduct(product.getId(), manager);
        warehouse = catalog.getWarehouse(warehouse.getId(), manager);
        assertFalse(product.isActive());
        assertFalse(warehouse.isActive());
        product.setActive(true);
        warehouse.setActive(true);
        assertTrue(catalog.saveProduct(product, product.getVersion(), manager).isActive());
        assertTrue(catalog.saveWarehouse(warehouse, warehouse.getVersion(), manager).isActive());
    }

    @Test
    public void draftTransferPreventsDeactivationUntilCancelled() {
        final Product product = newProduct(uniqueCode("TD"), 1);
        final Warehouse source = warehouse();
        final Warehouse destination = warehouse();
        StockTransfer transfer = transfers().saveDraft(warehouseActor, transfer(product, source, destination));
        expect("catalog.inUse.transfer", new Runnable() {
            public void run() { deactivateProduct(product); }
        });
        expect("catalog.inUse.transfer", new Runnable() {
            public void run() { deactivateWarehouse(source); }
        });
        expect("catalog.inUse.transfer", new Runnable() {
            public void run() { deactivateWarehouse(destination); }
        });
        transfers().cancel(warehouseActor, transfer.getId(), transfer.getVersion(), "架空移動計画取消");
        deactivateProduct(product);
        deactivateWarehouse(destination);
        assertFalse(catalog.getProduct(product.getId(), manager).isActive());
        assertFalse(catalog.getWarehouse(destination.getId(), manager).isActive());
    }

    @Test
    public void inTransitGoodsProtectProductAndDestinationEvenWithoutAnyDestinationBalance() {
        final Product product = newProduct(uniqueCode("TI"), 1);
        final Warehouse source = warehouse();
        final Warehouse destination = warehouse();
        inventory().receive(uniqueCode("OPEN"), source.getId(), product.getId(), 5,
                new BigDecimal("60.00"), Dates.today(), "架空移動準備", "", warehouseActor);
        StockTransfer transfer = transfers().saveDraft(warehouseActor, transfer(product, source, destination));
        transfer = transfers().submit(warehouseActor, transfer.getId(), transfer.getVersion());
        transfer = transfers().approve(manager, transfer.getId(), transfer.getVersion());
        transfer = transfers().dispatch(warehouseActor, transfer.getId(), transfer.getVersion());
        assertEquals(5L, transfer.getInTransitQuantity());
        expect("catalog.inUse.transfer", new Runnable() {
            public void run() { deactivateProduct(product); }
        });
        expect("catalog.inUse.transfer", new Runnable() {
            public void run() { deactivateWarehouse(destination); }
        });
        StockTransferReceiptCommand loss = new StockTransferReceiptCommand();
        loss.setRequestKey(uniqueCode("LOSS"));
        loss.setNote("架空輸送事故の全量損失を確認");
        loss.getLines().add(new StockControlLineCommand(product.getId(), 5));
        transfer = transfers().reconcileLoss(manager, transfer.getId(), transfer.getVersion(), loss);
        assertEquals("RECONCILED", transfer.getStatus());
        deactivateProduct(product);
        deactivateWarehouse(source);
        deactivateWarehouse(destination);
    }

    @Test
    public void proposedAdjustmentBlocksDeactivationUntilTheProposalIsCancelled() {
        final Product product = newProduct(uniqueCode("AD"), 1);
        final Warehouse warehouse = warehouse();
        StockAdjustment adjustment = adjustments().propose(warehouseActor, adjustment(product, warehouse, 1));
        expect("catalog.inUse.adjustment", new Runnable() {
            public void run() { deactivateProduct(product); }
        });
        expect("catalog.inUse.adjustment", new Runnable() {
            public void run() { deactivateWarehouse(warehouse); }
        });
        adjustments().cancel(warehouseActor, adjustment.getId(), adjustment.getVersion(), "架空差異を再確認");
        deactivateProduct(product);
        deactivateWarehouse(warehouse);
    }

    @Test
    public void anActiveCountProtectsZeroBalanceReferencesAndCancellationReleasesThem() {
        final Product product = newProduct(uniqueCode("CT"), 1);
        final Warehouse warehouse = warehouse();
        inventory().receive(uniqueCode("OPEN"), warehouse.getId(), product.getId(), 1,
                new BigDecimal("60.00"), Dates.today(), "架空棚卸準備", "", warehouseActor);
        StockAdjustment adjustment = adjustments().propose(warehouseActor, adjustment(product, warehouse, -1));
        adjustments().approve(manager, adjustment.getId(), adjustment.getVersion(), "架空在庫差異確認");
        StockCountCommand input = new StockCountCommand();
        input.setWarehouseId(warehouse.getId());
        input.getProductIds().add(product.getId());
        StockCount count = counts().begin(warehouseActor, input);
        assertEquals(0, count.getLines().get(0).getSnapshotOnHand());
        expect("catalog.inUse.count", new Runnable() {
            public void run() { deactivateProduct(product); }
        });
        expect("catalog.inUse.count", new Runnable() {
            public void run() { deactivateWarehouse(warehouse); }
        });
        counts().cancel(warehouseActor, count.getId(), count.getVersion(), "架空棚卸取消");
        deactivateProduct(product);
        deactivateWarehouse(warehouse);
    }

    @Test
    public void physicalInventoryPreventsDeactivationWithoutOpenStockDocuments() {
        final Product product = newProduct(uniqueCode("ST"), 1);
        final Warehouse warehouse = warehouse();
        inventory().receive(uniqueCode("OPEN"), warehouse.getId(), product.getId(), 1,
                new BigDecimal("60.00"), Dates.today(), "架空在庫", "", warehouseActor);
        expect("catalog.inUse.stock", new Runnable() {
            public void run() { deactivateProduct(product); }
        });
        expect("catalog.inUse.stock", new Runnable() {
            public void run() { deactivateWarehouse(warehouse); }
        });
        assertTrue(catalog.getProduct(product.getId(), manager).isActive());
        assertTrue(catalog.getWarehouse(warehouse.getId(), manager).isActive());
    }

    @Test
    public void openPurchaseProtectsAnEmptyReceivingWarehouseAndItsProduct() {
        final Product product = newProduct(uniqueCode("PO"), 1);
        final Warehouse warehouse = warehouse();
        PurchasingService purchasing = service("purchasingService", PurchasingService.class);
        Supplier supplier = new Supplier();
        supplier.setCode(uniqueCode("S"));
        supplier.setName("架空停止検証仕入先");
        supplier = purchasing.saveSupplier(supplier, 0, manager);
        SupplierProduct offer = new SupplierProduct();
        offer.setSupplier(supplier);
        offer.setProduct(product);
        offer.setUnitCost(new BigDecimal("60.00"));
        offer.setLeadTimeDays(0);
        offer.setValidFrom(Dates.parse("2020-01-01"));
        purchasing.saveSupplierProduct(offer, 0, manager);
        PurchaseOrder input = new PurchaseOrder();
        input.setSupplier(supplier);
        input.setWarehouse(warehouse);
        input.setOrderDate(Dates.today());
        input.setExpectedDate(Dates.today());
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setProduct(product);
        line.setQuantity(1);
        input.getLines().add(line);
        PurchaseOrder order = purchasing.saveOrder(input, 0, warehouseActor);
        expect("catalog.inUse.purchasing", new Runnable() {
            public void run() { deactivateProduct(product); }
        });
        expect("catalog.inUse.purchasing", new Runnable() {
            public void run() { deactivateWarehouse(warehouse); }
        });
        purchasing.cancelOrder(order.getId(), order.getVersion(), "架空発注取消", warehouseActor);
        deactivateProduct(product);
        deactivateWarehouse(warehouse);
    }

    @Test
    public void openSalesOrderProtectsReferencesUntilCancelled() {
        final Fixture fixture = fixture(0);
        final Warehouse warehouse = warehouse();
        OrderInput input = fixture.input(1);
        input.setWarehouseId(warehouse.getId());
        OrderService orders = service("orderService", OrderService.class);
        SalesOrder order = orders.saveDraft(null, 0, input, sales);
        expect("catalog.inUse.sales", new Runnable() {
            public void run() { deactivateProduct(fixture.product); }
        });
        expect("catalog.inUse.sales", new Runnable() {
            public void run() { deactivateWarehouse(warehouse); }
        });
        orders.cancel(order.getId(), order.getVersion(), "架空受注取消", sales);
        deactivateProduct(fixture.product);
        deactivateWarehouse(warehouse);
    }

    @Test
    public void requestedAndApprovedRestockingReturnsProtectEmptyWarehouseAndProduct() {
        final Fixture fixture = fixture(0);
        final Warehouse warehouse = warehouse();
        Shipment shipment = shipOne(fixture, warehouse);
        SalesReturn requested = requestReturn(shipment, true);
        expect("catalog.inUse.return", new Runnable() {
            public void run() { deactivateProduct(fixture.product); }
        });
        expect("catalog.inUse.return", new Runnable() {
            public void run() { deactivateWarehouse(warehouse); }
        });
        SalesReturn approved = shipping().approveReturn(requested.getId(), requested.getVersion(), manager);
        expect("catalog.inUse.return", new Runnable() {
            public void run() { deactivateProduct(fixture.product); }
        });
        expect("catalog.inUse.return", new Runnable() {
            public void run() { deactivateWarehouse(warehouse); }
        });
        shipping().cancelReturn(approved.getId(), approved.getVersion(), "架空返品取消", sales);
        deactivateProduct(fixture.product);
        deactivateWarehouse(warehouse);
    }

    @Test
    public void nonRestockingReturnsDoNotPreventDeactivationOrRequireActiveReferences() {
        Fixture fixture = fixture(0);
        Warehouse warehouse = warehouse();
        Shipment shipment = shipOne(fixture, warehouse);
        SalesReturn requested = requestReturn(shipment, false);
        deactivateProduct(fixture.product);
        deactivateWarehouse(warehouse);
        shipping().cancelReturn(requested.getId(), requested.getVersion(), "架空申請の再作成", sales);
        SalesReturn inactiveRequest = requestReturn(shipment, false);
        assertEquals("REQUESTED", inactiveRequest.getStatus());
        assertFalse(inactiveRequest.getLines().get(0).isRestock());
    }

    private Shipment shipOne(Fixture fixture, Warehouse warehouse) {
        inventory().receive(uniqueCode("RET"), warehouse.getId(), fixture.product.getId(), 1,
                new BigDecimal("60.00"), Dates.today(), "架空返品停止検証", "", warehouseActor);
        OrderInput input = fixture.input(1);
        input.setWarehouseId(warehouse.getId());
        OrderService orders = service("orderService", OrderService.class);
        SalesOrder order = orders.saveDraft(null, 0, input, sales);
        order = orders.submit(order.getId(), order.getVersion(), sales);
        order = orders.approve(order.getId(), order.getVersion(), manager);
        order = orders.allocate(order.getId(), order.getVersion(), warehouseActor);
        ShipmentLineInput line = new ShipmentLineInput();
        line.setOrderLineId(order.getLines().get(0).getId());
        line.setQuantity(1);
        Shipment instruction = shipping().instruct(order.getId(), order.getVersion(), Dates.today(),
                "OWN", "", Collections.singletonList(line), warehouseActor);
        return shipping().confirm(instruction.getId(), instruction.getVersion(), Dates.today(),
                uniqueCode("TRACK"), warehouseActor);
    }

    private SalesReturn requestReturn(Shipment shipment, boolean restock) {
        ReturnLineInput line = new ReturnLineInput();
        line.setShipmentLineId(shipment.getLines().get(0).getId());
        line.setQuantity(1);
        line.setRestock(restock);
        return shipping().requestReturn(shipment.getId(), "CUSTOMER_CHANGE", "",
                Collections.singletonList(line), sales);
    }

    private StockTransferCommand transfer(Product product, Warehouse source, Warehouse destination) {
        StockTransferCommand command = new StockTransferCommand();
        command.setSourceWarehouseId(source.getId());
        command.setDestinationWarehouseId(destination.getId());
        command.getLines().add(new StockControlLineCommand(product.getId(), 5));
        return command;
    }

    private StockAdjustmentCommand adjustment(Product product, Warehouse warehouse, int change) {
        StockAdjustmentCommand command = new StockAdjustmentCommand();
        command.setProductId(product.getId());
        command.setWarehouseId(warehouse.getId());
        command.setQuantityChange(change);
        command.setReason("架空検証用調整");
        return command;
    }

    private Warehouse warehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(uniqueCode("W"));
        warehouse.setName("架空マスタ停止試験倉庫");
        return catalog.saveWarehouse(warehouse, 0, manager);
    }

    private void deactivateProduct(Product reference) {
        Product product = catalog.getProduct(reference.getId(), manager);
        product.setActive(false);
        catalog.saveProduct(product, product.getVersion(), manager);
    }

    private void deactivateWarehouse(Warehouse reference) {
        Warehouse warehouse = catalog.getWarehouse(reference.getId(), manager);
        warehouse.setActive(false);
        catalog.saveWarehouse(warehouse, warehouse.getVersion(), manager);
    }

    private InventoryService inventory() { return service("inventoryService", InventoryService.class); }
    private StockTransferService transfers() { return service("stockTransferService", StockTransferService.class); }
    private StockAdjustmentService adjustments() { return service("stockAdjustmentService", StockAdjustmentService.class); }
    private StockCountService counts() { return service("stockCountService", StockCountService.class); }
    private ShippingService shipping() { return service("shippingService", ShippingService.class); }

    private void expect(String code, Runnable operation) {
        try {
            operation.run();
            fail("Expected " + code);
        } catch (BusinessException ex) {
            assertEquals(code, ex.getCode());
        }
    }
}
