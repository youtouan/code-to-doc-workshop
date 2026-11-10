package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Collections;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.entity.Warehouse;
import org.junit.Test;
import static org.junit.Assert.*;

public class OrderAmendmentRulesTest {
    @Test
    public void exposureUsesOnlyRemainingUnitsAndOriginalTaxSnapshots() {
        SalesOrder order = order();
        order.getLines().get(0).setShippedQuantity(4);
        order.getLines().get(0).setCancelledQuantity(1);
        assertEquals(new BigDecimal("550.00"), OrderAmendmentRules.remainingExposure(order,
                Collections.<Long, Integer>emptyMap()));
        assertEquals(new BigDecimal("880.00"), OrderAmendmentRules.remainingExposure(order,
                Collections.singletonMap(1L, Integer.valueOf(13))));
        assertEquals(new BigDecimal("1430.00"), OrderAmendmentRules.totals(order,
                Collections.singletonMap(1L, Integer.valueOf(13))).getTotalAmount());
    }

    @Test(expected = BusinessException.class)
    public void fulfilledAndCancelledUnitsAreAFloor() {
        SalesOrder order = order();
        order.getLines().get(0).setShippedQuantity(4);
        order.getLines().get(0).setCancelledQuantity(2);
        OrderAmendmentRules.validateTargets(order, Collections.singletonMap(1L, Integer.valueOf(5)));
    }

    @Test(expected = BusinessException.class)
    public void amendmentsKeepOriginalPackRules() {
        SalesOrder order = order();
        order.getLines().get(0).setPackSize(2);
        OrderAmendmentRules.validateTargets(order, Collections.singletonMap(1L, Integer.valueOf(7)));
    }

    @Test(expected = BusinessException.class)
    public void foreignLineIdsAreRejected() {
        OrderAmendmentRules.validateTargets(order(), Collections.singletonMap(99L, Integer.valueOf(5)));
    }

    @Test
    public void fingerprintDetectsLineOnlyChangesWithoutAHeaderVersionChange() {
        SalesOrder order = order();
        String initial = OrderAmendmentRules.fingerprint(order);
        order.getLines().get(0).setAllocatedQuantity(3);
        assertFalse(initial.equals(OrderAmendmentRules.fingerprint(order)));
        assertEquals(0, order.getVersion());
    }

    @Test(expected = BusinessException.class)
    public void zeroTargetsUseTheExistingCancellationWorkflowInstead() {
        OrderAmendmentRules.validateTargets(order(), Collections.singletonMap(1L, Integer.valueOf(0)));
    }

    private SalesOrder order() {
        SalesOrder order = new SalesOrder();
        order.setId(20L);
        Customer customer = new Customer();
        customer.setId(1L);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(21L);
        order.setCustomer(customer);
        order.setWarehouse(warehouse);
        order.setTaxRounding("DOWN");
        order.setStatus("APPROVED");
        order.setOrderDate(Dates.today());
        order.setRequestedDate(Dates.addDays(Dates.today(), 3));
        SalesOrderLine line = new SalesOrderLine();
        line.setId(1L);
        line.setOrder(order);
        Product product = new Product();
        product.setId(11L);
        line.setProduct(product);
        line.setQuantity(10);
        line.setPackSize(1);
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setTaxRate(new BigDecimal("0.1000"));
        order.getLines().add(line);
        return order;
    }
}
