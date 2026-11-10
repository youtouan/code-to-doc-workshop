package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jp.co.tsubame.wholesale.common.BacklogRow;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.common.OrderLineInput;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.PurchasingReceiptCommand;
import jp.co.tsubame.wholesale.common.PurchasingReceiptLineCommand;
import jp.co.tsubame.wholesale.common.ReportFilter;
import jp.co.tsubame.wholesale.common.ReturnLineInput;
import jp.co.tsubame.wholesale.common.SalesSummaryRow;
import jp.co.tsubame.wholesale.common.ShipmentLineInput;
import jp.co.tsubame.wholesale.common.StockActivityRow;
import jp.co.tsubame.wholesale.common.SupplierQualityRow;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.PurchaseOrder;
import jp.co.tsubame.wholesale.entity.PurchaseOrderLine;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesReturn;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.entity.SupplierProduct;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.OperationsReportService;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.PurchasingService;
import jp.co.tsubame.wholesale.service.ShippingService;
import org.junit.Test;
import static org.junit.Assert.*;

public class OperationsReportTest extends PostgresTestSupport {
    @Test
    public void shipmentAndReturnAreRecognizedOnTheirOwnBusinessDates() {
        Fixture f = fixture(20);
        SalesOrder order = allocated(f.order(6));
        Shipment shipment = ship(order, 6, -1);
        ReturnLineInput line = new ReturnLineInput();
        line.setShipmentLineId(shipment.getLines().get(0).getId());
        line.setQuantity(2);
        line.setRestock(true);
        ShippingService shipping = service("shippingService", ShippingService.class);
        SalesReturn requested = shipping.requestReturn(shipment.getId(), "CUSTOMER_CHANGE", "", Arrays.asList(line), sales);
        SalesReturn approved = shipping.approveReturn(requested.getId(), requested.getVersion(), manager);
        shipping.receiveReturn(approved.getId(), approved.getVersion(), Dates.today(), warehouseActor);

        ReportFilter yesterday = filter(f);
        yesterday.setFrom(Dates.addDays(Dates.today(), -1));
        yesterday.setTo(Dates.addDays(Dates.today(), -1));
        SalesSummaryRow shipped = reports().searchSales("CUSTOMER", yesterday, manager).getItems().get(0);
        assertEquals(new BigDecimal("600.00"), shipped.getNetAmount());
        assertEquals(6, shipped.getShippedQuantity());
        assertEquals(0, shipped.getReturnedQuantity());
        assertEquals(1, shipped.getShipmentCount());

        ReportFilter today = filter(f);
        today.setFrom(Dates.today());
        SalesSummaryRow returned = reports().searchSales("CUSTOMER", today, manager).getItems().get(0);
        assertEquals(new BigDecimal("-200.00"), returned.getNetAmount());
        assertEquals(-2, returned.getNetQuantity());
        assertEquals(0, returned.getShipmentCount());
        assertEquals(1, returned.getReturnCount());
        SalesSummaryRow total = reports().searchSales("PRODUCT", filter(f), manager).getItems().get(0);
        assertEquals(new BigDecimal("400.00"), total.getNetAmount());
    }

    @Test
    public void multipleLinesDoNotMultiplyDocumentCounts() {
        Fixture f = fixture(20);
        Product second = newProduct(uniqueCode("R"), 1);
        service("inventoryService", InventoryService.class).receive(uniqueCode("RR"), f.warehouse.getId(), second.getId(),
                10, new BigDecimal("60.00"), Dates.today(), "report fixture", "", warehouseActor);
        OrderInput input = f.input(3);
        OrderLineInput additional = new OrderLineInput();
        additional.setProductId(second.getId());
        additional.setQuantity(4);
        input.getLines().add(additional);
        SalesOrder order = allocated(service("orderService", OrderService.class).saveDraft(null, 0, input, sales));
        List<ShipmentLineInput> lines = new ArrayList<ShipmentLineInput>();
        for (jp.co.tsubame.wholesale.entity.SalesOrderLine line : order.getLines()) {
            ShipmentLineInput entry = new ShipmentLineInput();
            entry.setOrderLineId(line.getId());
            entry.setQuantity(line.getQuantity());
            lines.add(entry);
        }
        ShippingService shipping = service("shippingService", ShippingService.class);
        Shipment instruction = shipping.instruct(order.getId(), order.getVersion(), Dates.today(), "OWN", "", lines, warehouseActor);
        shipping.confirm(instruction.getId(), instruction.getVersion(), Dates.today(), uniqueCode("TR"), warehouseActor);
        ReportFilter filter = filter(f);
        filter.setProductId(null);
        SalesSummaryRow total = reports().searchSales("CUSTOMER", filter, manager).getItems().get(0);
        assertEquals(1, total.getShipmentCount());
        assertEquals(7, total.getShippedQuantity());
        assertEquals(new BigDecimal("700.00"), total.getShippedAmount());
        filter.setSize(1);
        Page<SalesSummaryRow> first = reports().searchSales("PRODUCT", filter, manager);
        assertEquals(2, first.getTotal());
        assertEquals(1, first.getItems().size());
        filter.setPage(2);
        Page<SalesSummaryRow> next = reports().searchSales("PRODUCT", filter, manager);
        assertNotEquals(first.getItems().get(0).getDimensionId(), next.getItems().get(0).getDimensionId());
    }

    @Test
    public void backlogIncludesFuturePromisesButExcludesDraftsAndCancelledRemainders() {
        Fixture f = fixture(3);
        f.order(2);
        SalesOrder order = allocated(f.order(8));
        ReportFilter filter = filter(f);
        filter.setTo(Dates.addDays(Dates.today(), 2));
        List<BacklogRow> rows = reports().searchBacklog(filter, warehouseActor).getItems();
        assertEquals(1, rows.size());
        assertEquals(order.getId(), rows.get(0).getOrderId());
        assertEquals(8, rows.get(0).getOpenQuantity());
        assertEquals(3, rows.get(0).getAllocatedQuantity());
        assertEquals(5, rows.get(0).getShortageQuantity());
        assertEquals(0, rows.get(0).getDaysLate());
        service("orderService", OrderService.class).cancel(order.getId(), order.getVersion(), "report close", sales);
        assertEquals(0, reports().searchBacklog(filter, warehouseActor).getTotal());
    }

    @Test
    public void stockActivitySeparatesPhysicalAndReservationChanges() {
        Fixture f = fixture(10);
        SalesOrder order = allocated(f.order(4));
        service("orderService", OrderService.class).cancel(order.getId(), order.getVersion(), "release", sales);
        ReportFilter filter = filter(f);
        filter.setFrom(Dates.today());
        List<StockActivityRow> rows = reports().searchStockActivity(filter, warehouseActor).getItems();
        long physical = 0;
        long reservation = 0;
        long movementCount = 0;
        for (StockActivityRow row : rows) {
            physical += row.getNetQuantity();
            reservation += row.getNetReservationChange();
            movementCount += row.getMovementCount();
        }
        assertEquals(10, physical);
        assertEquals(0, reservation);
        assertEquals(3, movementCount);
    }

    @Test
    public void emptyReportsAreEmptyAndUnknownDimensionCannotBecomeSql() {
        Fixture f = fixture(0);
        assertEquals(0, reports().searchSales("WAREHOUSE", filter(f), manager).getTotal());
        assertEquals(0, reports().searchSupplierQuality(filter(f), manager).getTotal());
        try {
            reports().searchSales("CUSTOMER;drop table product", filter(f), manager);
            fail("Untrusted dimension");
        } catch (BusinessException expected) {
            assertEquals("report.dimension", expected.getCode());
        }
    }

    @Test
    public void reportingPermissionsAreEnforcedBySharedService() {
        Fixture f = fixture(0);
        try {
            reports().searchSales("CUSTOMER", filter(f), warehouseActor);
            fail("Warehouse role must not read sales amounts");
        } catch (BusinessException expected) {
            assertEquals("permission.denied", expected.getCode());
        }
    }

    @Test
    public void supplierQualityUsesAcceptedAndRejectedReceiptFactsWithoutCountingLinesAsReceipts() {
        Fixture f = fixture(0);
        PurchasingService purchasing = service("purchasingService", PurchasingService.class);
        Supplier supplier = new Supplier();
        supplier.setCode(uniqueCode("QS"));
        supplier.setName("架空品質報告仕入先");
        supplier.setDefaultLeadTimeDays(0);
        supplier = purchasing.saveSupplier(supplier, 0, manager);
        SupplierProduct offer = new SupplierProduct();
        offer.setSupplier(supplier);
        offer.setProduct(f.product);
        offer.setValidFrom(Dates.addDays(Dates.today(), -30));
        offer.setMinimumQuantity(1);
        offer.setOrderPackSize(1);
        offer.setUnitCost(new BigDecimal("60.00"));
        offer.setLeadTimeDays(0);
        offer.setPreferred(true);
        purchasing.saveSupplierProduct(offer, 0, manager);
        PurchaseOrder input = new PurchaseOrder();
        input.setSupplier(supplier);
        input.setWarehouse(f.warehouse);
        input.setOrderDate(Dates.addDays(Dates.today(), -2));
        input.setExpectedDate(Dates.addDays(Dates.today(), -1));
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setProduct(f.product);
        line.setQuantity(10);
        input.getLines().add(line);
        PurchaseOrder draft = purchasing.saveOrder(input, 0, warehouseActor);
        PurchaseOrder submitted = purchasing.submitOrder(draft.getId(), draft.getVersion(), warehouseActor);
        PurchaseOrder approved = purchasing.approveOrder(submitted.getId(), submitted.getVersion(), manager);
        PurchasingReceiptCommand receipt = new PurchasingReceiptCommand();
        receipt.setOrderId(approved.getId());
        receipt.setExpectedVersion(approved.getVersion());
        receipt.setRequestKey(uniqueCode("QR"));
        receipt.setReceiptDate(Dates.today());
        receipt.setSupplierDeliveryNumber(uniqueCode("QD"));
        PurchasingReceiptLineCommand receivedLine = new PurchasingReceiptLineCommand();
        receivedLine.setOrderLineId(approved.getLines().get(0).getId());
        receivedLine.setAcceptedQuantity(8);
        receivedLine.setRejectedQuantity(2);
        receivedLine.setRejectionReason("破損");
        receipt.getLines().add(receivedLine);
        purchasing.receive(receipt, warehouseActor);
        ReportFilter filter = filter(f);
        filter.setSupplierId(supplier.getId());
        filter.setExceptionsOnly(true);
        SupplierQualityRow result = reports().searchSupplierQuality(filter, manager).getItems().get(0);
        assertEquals(1, result.getReceiptCount());
        assertEquals(8, result.getAcceptedQuantity());
        assertEquals(2, result.getRejectedQuantity());
        assertEquals(8, result.getLateAcceptedQuantity());
        assertEquals(new BigDecimal("480.00"), result.getAcceptedAmount());
        assertEquals(new BigDecimal("20.00"), result.getRejectionPercent());
        assertEquals(new BigDecimal("100.00"), result.getLateAcceptedPercent());
    }

    private ReportFilter filter(Fixture fixture) {
        ReportFilter filter = new ReportFilter();
        filter.setCustomerId(fixture.customer.getId());
        filter.setWarehouseId(fixture.warehouse.getId());
        filter.setProductId(fixture.product.getId());
        return filter;
    }

    private OperationsReportService reports() {
        return service("operationsReportService", OperationsReportService.class);
    }

    private SalesOrder allocated(SalesOrder draft) {
        OrderService service = service("orderService", OrderService.class);
        SalesOrder submitted = service.submit(draft.getId(), draft.getVersion(), sales);
        SalesOrder approved = service.approve(submitted.getId(), submitted.getVersion(), manager);
        return service.allocate(approved.getId(), approved.getVersion(), warehouseActor);
    }

    private Shipment ship(SalesOrder order, int quantity, int dayOffset) {
        ShippingService shipping = service("shippingService", ShippingService.class);
        ShipmentLineInput line = new ShipmentLineInput();
        line.setOrderLineId(order.getLines().get(0).getId());
        line.setQuantity(quantity);
        Shipment instruction = shipping.instruct(order.getId(), order.getVersion(), Dates.addDays(Dates.today(), dayOffset),
                "OWN", "", Arrays.asList(line), warehouseActor);
        return shipping.confirm(instruction.getId(), instruction.getVersion(), Dates.addDays(Dates.today(), dayOffset),
                uniqueCode("TR"), warehouseActor);
    }
}
