package jp.co.tsubame.wholesale.web.action;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.OrderAmendmentCommand;
import jp.co.tsubame.wholesale.common.QuotationCommand;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.OrderAmendment;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Quotation;
import jp.co.tsubame.wholesale.entity.QuotationLine;
import jp.co.tsubame.wholesale.entity.QuotationRevision;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.CatalogService;
import jp.co.tsubame.wholesale.service.OrderAmendmentService;
import jp.co.tsubame.wholesale.service.QuotationService;
import jp.co.tsubame.wholesale.service.ShippingService;
import jp.co.tsubame.wholesale.web.HttpFixture;
import jp.co.tsubame.wholesale.web.form.OrderAmendmentForm;
import jp.co.tsubame.wholesale.web.form.QuotationForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import static org.junit.Assert.*;

public class QuotationAmendmentActionsTest {
    @Test public void salesCanSubmitNegotiatedProposalWithoutOrderManagerOverride() throws Exception {
        HttpFixture http = actor("SALES"); http.method = "POST";
        context(http, "quotationService", new QuotationService() {
            public Quotation saveDraft(Long id, int version, QuotationCommand command, Actor actor) {
                assertNull(id); assertTrue(actor.hasRole("SALES")); assertEquals(new BigDecimal("40.00"), command.getLines().get(0).getNegotiatedUnitPrice());
                assertEquals("数量契約", command.getLines().get(0).getNegotiationReason()); return quote();
            }
        });
        QuotationForm form = form(); form.setOp("save"); form.getNegotiatedUnitPrice()[0] = "40.00";
        form.getNegotiationReason()[0] = "数量契約";
        new QuotationAction().execute(mapping("/quotations"), form, http.request, http.response);
        assertEquals(303, http.status); assertEquals("/wholesale/quotations.do?op=detail&id=8", http.headers.get("Location"));
    }
    @Test public void ordinarySalesCannotApproveQuotesOrAmendments() throws Exception {
        HttpFixture quoteHttp = actor("SALES"); quoteHttp.method = "POST"; QuotationForm quote = form(); quote.setOp("approve");
        new QuotationAction().execute(mapping("/quotations"), quote, quoteHttp.request, quoteHttp.response);
        assertEquals(403, quoteHttp.status);
        HttpFixture amendmentHttp = actor("SALES"); amendmentHttp.method = "POST";
        OrderAmendmentForm amendment = new OrderAmendmentForm(); amendment.setOp("approve");
        new OrderAmendmentAction().execute(mapping("/orderAmendments"), amendment, amendmentHttp.request, amendmentHttp.response);
        assertEquals(403, amendmentHttp.status);
    }
    @Test public void editPreservesExplicitQuotedNegotiationButDoesNotInjectCatalogPrice() throws Exception {
        HttpFixture http = actor("SALES");
        context(http, "quotationService", new QuotationService() {
            public Quotation getQuotation(Long id, Actor actor) { return quote(); }
        });
        QuotationForm form = new QuotationForm(); form.setOp("edit"); form.setId("8");
        new QuotationAction().execute(mapping("/quotations"), form, http.request, http.response);
        assertEquals("40.00", form.getNegotiatedUnitPrice()[0]); assertEquals("数量契約", form.getNegotiationReason()[0]);
        assertEquals("save", form.getSaveMode());
    }
    @Test public void conversionDelegatesExactlyOnceAndDoesNotRebuildOrRepriceOrder() throws Exception {
        HttpFixture http = actor("SALES"); http.method = "POST";
        context(http, "quotationService", new QuotationService() {
            public SalesOrder convertToOrder(Long id, int version, Actor actor) {
                assertEquals(Long.valueOf(8), id); assertEquals(3, version);
                SalesOrder order = new SalesOrder(); order.setId(90L); order.setStatus("DRAFT"); return order;
            }
        });
        QuotationForm form = new QuotationForm(); form.setOp("convert"); form.setId("8"); form.setVersion("3");
        new QuotationAction().execute(mapping("/quotations"), form, http.request, http.response);
        assertEquals(303, http.status); assertEquals("/wholesale/orders.do?op=detail&id=90", http.headers.get("Location"));
    }
    @Test public void revisionLookupUsesExplicitRevisionNumberAndIsReadOnly() throws Exception {
        HttpFixture http = actor("BILLING");
        context(http, "quotationService", new QuotationService() {
            public Quotation getQuotation(Long id, Actor actor) { return quote(); }
            public QuotationRevision getRevision(Long id, int number, Actor actor) {
                assertEquals(2, number); QuotationRevision revision = quote().getCurrentRevision(); revision.setRevisionNumber(2); return revision;
            }
        });
        QuotationForm form = new QuotationForm(); form.setOp("revision"); form.setId("8"); form.setRevisionNumber("2");
        ActionForward forward = new QuotationAction().execute(mapping("/quotations"), form, http.request, http.response);
        assertEquals(200, http.status); assertEquals("/WEB-INF/jsp/quotations/revision.jsp", forward.getPath());
    }
    @Test public void amendmentRequestPassesExpectedOrderVersionAndOnlyTargets() throws Exception {
        HttpFixture http = actor("SALES"); http.method = "POST";
        context(http, "orderAmendmentService", new OrderAmendmentService() {
            public OrderAmendment request(OrderAmendmentCommand command, Actor actor) {
                assertEquals(Long.valueOf(5), command.getOrderId()); assertEquals(7, command.getExpectedOrderVersion());
                assertEquals(24, command.getLines().get(0).getTargetQuantity()); assertNull(command.getRequestedDate());
                OrderAmendment amendment = new OrderAmendment(); amendment.setId(50L); return amendment;
            }
        });
        OrderAmendmentForm form = new OrderAmendmentForm(); form.setOp("request"); form.setOrderId("5");
        form.setExpectedOrderVersion("7"); form.setReason("数量変更"); form.setOrderLineId(new String[] {"10"}); form.setTargetQuantity(new String[] {"24"});
        new OrderAmendmentAction().execute(mapping("/orderAmendments"), form, http.request, http.response);
        assertEquals(303, http.status); assertEquals("/wholesale/orderAmendments.do?op=detail&id=50", http.headers.get("Location"));
    }
    @Test public void openInstructionsBlockIsDisplayedWithoutAutomaticShipmentCancellation() throws Exception {
        HttpFixture http = actor("MANAGER"); http.method = "POST";
        context(http, "orderAmendmentService", new OrderAmendmentService() {
            public OrderAmendment approve(Long id, int version, Actor actor) {
                throw new BusinessException("amendment.instructions", "出荷指示を取り消してから承認してください。");
            }
            public OrderAmendment getAmendment(Long id, Actor actor) {
                SalesOrder order = new SalesOrder(); order.setId(5L);
                OrderAmendment amendment = new OrderAmendment(); amendment.setId(id); amendment.setOrder(order); amendment.setNumber("OA50"); return amendment;
            }
        });
        OrderAmendmentForm form = new OrderAmendmentForm(); form.setOp("approve"); form.setId("50"); form.setVersion("3");
        ActionForward forward = new OrderAmendmentAction().execute(mapping("/orderAmendments"), form, http.request, http.response);
        assertEquals(422, http.status); assertEquals("/WEB-INF/jsp/amendments/detail.jsp", forward.getPath());
        assertEquals(1, ((List<?>) http.attributes.get("openInstructions")).size()); assertNull(http.headers.get("Location"));
    }
    private static Quotation quote() {
        Customer customer = new Customer(); customer.setId(1L); Warehouse warehouse = new Warehouse(); warehouse.setId(2L);
        Product product = new Product(); product.setId(4L);
        Quotation quote = new Quotation(); quote.setId(8L); quote.setVersion(3); quote.setNumber("QT8");
        quote.setCustomer(customer); quote.setWarehouse(warehouse); quote.setRevisionNumber(1);
        QuotationRevision revision = new QuotationRevision(); revision.setRevisionNumber(1); revision.setWarehouse(warehouse);
        revision.setQuoteDate(Dates.today()); revision.setValidUntil(Dates.addDays(Dates.today(), 30)); revision.setRequestedDate(Dates.addDays(Dates.today(), 7));
        QuotationLine line = new QuotationLine(); line.setProduct(product); line.setQuantity(12);
        line.setNegotiated(true); line.setUnitPrice(new BigDecimal("40.00")); line.setCatalogUnitPrice(new BigDecimal("50.00"));
        line.setNegotiationReason("数量契約"); revision.getLines().add(line); quote.getRevisions().add(revision); return quote;
    }
    private QuotationForm form() {
        QuotationForm form = new QuotationForm(); form.rows(1); form.setCustomerId("1"); form.setWarehouseId("2");
        form.setQuoteDate(Dates.format(Dates.today())); form.setValidUntil(Dates.format(Dates.addDays(Dates.today(), 30)));
        form.setRequestedDate(Dates.format(Dates.addDays(Dates.today(), 7))); form.getProductId()[0] = "4"; form.getQuantity()[0] = "12"; return form;
    }
    private HttpFixture actor(String role) { HttpFixture http = new HttpFixture(); http.attributes.put("actor", new Actor(99L, "test", "検証", role)); return http; }
    private ActionMapping mapping(String path) { ActionMapping mapping = new ActionMapping(); mapping.setPath(path); return mapping; }
    private void context(HttpFixture http, String name, Object bean) {
        StaticWebApplicationContext context = new StaticWebApplicationContext(); context.setServletContext(http.context);
        context.getBeanFactory().registerSingleton(name, bean);
        context.getBeanFactory().registerSingleton("catalogService", new CatalogService() {
            public List<Customer> listActiveCustomers(Actor actor) { return Collections.emptyList(); }
            public List<Product> listActiveProducts(Actor actor) { return Collections.emptyList(); }
            public List<Warehouse> listActiveWarehouses(Actor actor) { return Collections.emptyList(); }
        });
        context.getBeanFactory().registerSingleton("shippingService", new ShippingService() {
            public List<Shipment> listOrderShipments(Long id, Actor actor) {
                Shipment shipment = new Shipment(); shipment.setId(6L); shipment.setStatus("INSTRUCTED"); return Collections.singletonList(shipment);
            }
            public Shipment cancelInstruction(Long id, int version, String reason, Actor actor) {
                fail("Amendment UI must not cancel instructions automatically"); return null;
            }
        });
        context.refresh(); http.contextAttributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
    }
}
