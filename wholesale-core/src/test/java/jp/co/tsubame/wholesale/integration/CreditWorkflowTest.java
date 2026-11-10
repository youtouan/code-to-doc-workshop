package jp.co.tsubame.wholesale.integration;

import java.math.BigDecimal;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Invoice;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.Shipment;
import org.junit.Test;
import static org.junit.Assert.*;

public class CreditWorkflowTest extends BillingTest {
    @Test
    public void exposureMovesThroughCommitmentShipmentDraftAndPostedInvoiceWithoutDoubleCount() {
        Fixture fixture = fixture(10);
        SalesOrder order = fixture.order(10, Dates.addDays(closingDate(), -2));
        money("0", credit().previewExposure(fixture.customer.getId(), manager));
        order = orders().submit(order.getId(), order.getVersion(), sales);
        money("0", credit().previewExposure(fixture.customer.getId(), manager));
        order = orders().approve(order.getId(), order.getVersion(), manager);
        money("1100", credit().previewExposure(fixture.customer.getId(), manager));
        order = orders().allocate(order.getId(), order.getVersion(), warehouseActor);
        Shipment shipment = shipAllocated(order, 4, closingDate());
        money("1100", credit().previewExposure(fixture.customer.getId(), manager));
        returnGoods(shipment, 1);
        money("990", credit().previewExposure(fixture.customer.getId(), manager));
        Invoice invoice = billing().prepare(fixture.customer.getId(), closingDate(), billingActor);
        money("990", credit().previewExposure(fixture.customer.getId(), manager));
        billing().finalizeInvoice(invoice.getId(), invoice.getVersion(), billingActor);
        money("990", credit().previewExposure(fixture.customer.getId(), manager));
    }

    @Test
    public void zeroCreditLimitIsNotUnlimitedEvenForAdministrator() {
        Fixture fixture = fixture(0);
        SalesOrder draft = fixture.order(1);
        final SalesOrder submitted = orders().submit(draft.getId(), draft.getVersion(), sales);
        Customer customer = catalog.getCustomer(fixture.customer.getId(), manager);
        customer.setCreditLimit(BigDecimal.ZERO);
        catalog.saveCustomer(customer, customer.getVersion(), manager);
        expect("credit.noLimit", new Runnable() {
            public void run() { orders().approve(submitted.getId(), submitted.getVersion(), admin); }
        });
        assertEquals("SUBMITTED", orders().getOrder(submitted.getId(), sales).getStatus());
    }

    @Test
    public void candidateUsesGrossAmountAndDoesNotWaiveExistingCommitments() {
        Fixture fixture = fixture(0);
        Customer customer = catalog.getCustomer(fixture.customer.getId(), manager);
        customer.setCreditLimit(new BigDecimal("150"));
        catalog.saveCustomer(customer, customer.getVersion(), manager);
        SalesOrder first = fixture.order(1);
        first = orders().submit(first.getId(), first.getVersion(), sales);
        orders().approve(first.getId(), first.getVersion(), manager);
        SalesOrder second = fixture.order(1);
        final SalesOrder submitted = orders().submit(second.getId(), second.getVersion(), sales);
        expect("credit.limit", new Runnable() {
            public void run() { orders().approve(submitted.getId(), submitted.getVersion(), manager); }
        });
        money("110", credit().previewExposure(fixture.customer.getId(), billingActor));
    }

    @Test
    public void cancelledRemainingCommitmentsNoLongerConsumeCredit() {
        Fixture fixture = fixture(0);
        SalesOrder approved = approve(fixture, 2, Dates.today());
        money("220", credit().previewExposure(fixture.customer.getId(), manager));
        orders().cancel(approved.getId(), approved.getVersion(), "顧客取消", manager);
        money("0", credit().previewExposure(fixture.customer.getId(), manager));
    }
}
