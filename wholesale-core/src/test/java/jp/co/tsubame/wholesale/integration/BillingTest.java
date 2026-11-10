package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.ReturnLineInput;
import jp.co.tsubame.wholesale.common.ShipmentLineInput;
import jp.co.tsubame.wholesale.entity.Invoice;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesReturn;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.service.BillingService;
import jp.co.tsubame.wholesale.service.CreditService;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.ReceivablesService;
import jp.co.tsubame.wholesale.service.ShippingService;
import org.junit.Assert;

public abstract class BillingTest extends PostgresTestSupport {
    protected BillingService billing() { return service("billingService", BillingService.class); }
    protected ReceivablesService receivables() { return service("receivablesService", ReceivablesService.class); }
    protected CreditService credit() { return service("creditService", CreditService.class); }
    protected OrderService orders() { return service("orderService", OrderService.class); }
    protected ShippingService shipping() { return service("shippingService", ShippingService.class); }

    protected Date closingDate() {
        return Dates.monthEnd(Dates.addMonths(Dates.today(), -1));
    }

    protected SalesOrder approve(Fixture fixture, int quantity, Date orderDate) {
        SalesOrder order = fixture.order(quantity, orderDate);
        order = orders().submit(order.getId(), order.getVersion(), sales);
        return orders().approve(order.getId(), order.getVersion(), manager);
    }

    protected Shipment ship(Fixture fixture, int quantity, Date date) {
        SalesOrder order = approve(fixture, quantity, Dates.addDays(date, -2));
        order = orders().allocate(order.getId(), order.getVersion(), warehouseActor);
        return shipAllocated(order, quantity, date);
    }

    protected Shipment shipAllocated(SalesOrder order, int quantity, Date date) {
        ShipmentLineInput input = new ShipmentLineInput();
        input.setOrderLineId(order.getLines().get(0).getId());
        input.setQuantity(quantity);
        Shipment shipment = shipping().instruct(order.getId(), order.getVersion(), date,
                "OWN", "billing test", Arrays.asList(input), warehouseActor);
        return shipping().confirm(shipment.getId(), shipment.getVersion(), date, uniqueCode("BILL"), warehouseActor);
    }

    protected Invoice finalizeFor(Fixture fixture, Date close) {
        Invoice invoice = billing().prepare(fixture.customer.getId(), close, billingActor);
        return billing().finalizeInvoice(invoice.getId(), invoice.getVersion(), billingActor);
    }

    protected SalesReturn returnGoods(Shipment shipment, int quantity) {
        ReturnLineInput input = new ReturnLineInput();
        input.setShipmentLineId(shipment.getLines().get(0).getId());
        input.setQuantity(quantity);
        input.setRestock(false);
        SalesReturn salesReturn = shipping().requestReturn(shipment.getId(), "CUSTOMER_CHANGE",
                "billing credit test", Arrays.asList(input), sales);
        salesReturn = shipping().approveReturn(salesReturn.getId(), salesReturn.getVersion(), manager);
        return shipping().receiveReturn(salesReturn.getId(), salesReturn.getVersion(), Dates.today(), warehouseActor);
    }

    protected void money(String expected, BigDecimal actual) {
        Assert.assertEquals(expected + " != " + actual, 0, new BigDecimal(expected).compareTo(actual));
    }

    protected void expect(String code, Runnable action) {
        try {
            action.run();
            Assert.fail("Expected " + code);
        } catch (BusinessException ex) {
            Assert.assertEquals(code, ex.getCode());
        }
    }
}
