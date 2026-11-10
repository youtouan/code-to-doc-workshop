package jp.co.tsubame.wholesale.web.action;

import java.util.Collections;
import java.util.List;
import jp.co.tsubame.wholesale.common.APMatchInput;
import jp.co.tsubame.wholesale.common.APPaymentInput;
import jp.co.tsubame.wholesale.common.APSearch;
import jp.co.tsubame.wholesale.common.APOpenItem;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.APPaymentVoucher;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.service.APInvoiceService;
import jp.co.tsubame.wholesale.service.APReportService;
import jp.co.tsubame.wholesale.service.APSettlementService;
import jp.co.tsubame.wholesale.service.CatalogService;
import jp.co.tsubame.wholesale.service.PurchasingService;
import jp.co.tsubame.wholesale.web.HttpFixture;
import jp.co.tsubame.wholesale.web.form.APForm;
import jp.co.tsubame.wholesale.web.form.APInvoiceForm;
import jp.co.tsubame.wholesale.web.form.APMatchForm;
import jp.co.tsubame.wholesale.web.form.APPaymentForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import static org.junit.Assert.*;

public class APActionsTest {
    @Test public void warehouseCannotReadPayablesAndManagerCannotWriteInvoices() throws Exception {
        HttpFixture warehouse = actor("WAREHOUSE"); APForm read = new APForm();
        new APReportAction().execute(mapping("/apReports"), read, warehouse.request, warehouse.response);
        assertEquals(403, warehouse.status);
        HttpFixture manager = actor("MANAGER"); manager.method = "POST";
        APInvoiceForm write = new APInvoiceForm(); write.setOp("save");
        new APInvoiceAction().execute(mapping("/apInvoices"), write, manager.request, manager.response);
        assertEquals(403, manager.status);
    }
    @Test public void clearMatchingUsesExplicitEmptyReplacementAndExpectedInvoiceVersion() throws Exception {
        HttpFixture http = actor("BILLING"); http.method = "POST";
        context(http, new APInvoiceService() {
            public APInvoice replaceMatches(Long id, int version, List<APMatchInput> lines, Actor actor) {
                assertEquals(Long.valueOf(5), id); assertEquals(8, version); assertTrue(lines.isEmpty());
                APInvoice result = new APInvoice(); result.setId(id); return result;
            }
        }, null);
        APMatchForm form = new APMatchForm(); form.setOp("clear"); form.setId("5"); form.setVersion("8");
        assertNull(new APMatchAction().execute(mapping("/apMatches"), form, http.request, http.response));
        assertEquals(303, http.status); assertEquals("/wholesale/apInvoices.do?op=detail&id=5", http.headers.get("Location"));
    }
    @Test public void paymentCoreFailurePreservesRetryKeyAndAllEnteredAmounts() throws Exception {
        HttpFixture http = actor("BILLING"); http.method = "POST";
        context(http, null, new APSettlementService() {
            public APPaymentVoucher pay(APPaymentInput input, Actor actor) {
                assertEquals("same-key", input.getRequestKey());
                throw new BusinessException("ap.overpayment", "未払残高を超えています。");
            }
        });
        APPaymentForm form = payment(); String[] allocations = form.getAllocationAmount();
        ActionForward forward = new APPaymentAction().execute(mapping("/apPayments"), form, http.request, http.response);
        assertEquals(422, http.status); assertEquals("/WEB-INF/jsp/ap/payment-edit.jsp", forward.getPath());
        assertEquals("same-key", form.getRequestKey()); assertSame(allocations, form.getAllocationAmount());
        assertNull(http.headers.get("Location")); assertNotNull(http.attributes.get("openItems"));
    }
    @Test public void successfulPaymentRedirectsToActualVoucherWithoutSeparateAllocationCalls() throws Exception {
        HttpFixture http = actor("BILLING"); http.method = "POST";
        context(http, null, new APSettlementService() {
            public APPaymentVoucher pay(APPaymentInput input, Actor actor) {
                assertEquals(1, input.getLines().size());
                APPaymentVoucher voucher = new APPaymentVoucher(); voucher.setId(123L); return voucher;
            }
        });
        new APPaymentAction().execute(mapping("/apPayments"), payment(), http.request, http.response);
        assertEquals(303, http.status); assertEquals("/wholesale/apPayments.do?op=detail&id=123", http.headers.get("Location"));
    }
    @Test public void reportPostNeverCallsCore() throws Exception {
        HttpFixture http = new HttpFixture(); http.method = "POST";
        APForm form = new APForm(); form.setOp("statement");
        new APReportAction().execute(mapping("/apReports"), form, http.request, http.response);
        assertEquals(405, http.status);
    }
    @Test public void inactiveSupplierCanOpenHistoricalInvoiceWithoutReactivation() throws Exception {
        HttpFixture http = actor("BILLING");
        final Supplier historical = new Supplier(); historical.setId(2L); historical.setCode("OLD");
        historical.setName("過去取引仕入先"); historical.setActive(false);
        StaticWebApplicationContext context = new StaticWebApplicationContext(); context.setServletContext(http.context);
        context.getBeanFactory().registerSingleton("purchasingService", new PurchasingService() {
            public List<Supplier> listActiveSuppliers(Actor actor) { return Collections.emptyList(); }
            public Supplier getSupplier(Long id, Actor actor) { assertEquals(Long.valueOf(2), id); return historical; }
        });
        context.getBeanFactory().registerSingleton("catalogService", new CatalogService() {
            public List<Product> listActiveProducts(Actor actor) { return Collections.emptyList(); }
        });
        context.refresh(); http.contextAttributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        APInvoiceForm form = new APInvoiceForm(); form.setOp("new"); form.setSupplierId("2");
        ActionForward forward = new APInvoiceAction().execute(mapping("/apInvoices"), form, http.request, http.response);
        assertEquals(200, http.status); assertEquals("/WEB-INF/jsp/ap/invoice-edit.jsp", forward.getPath());
        assertSame(historical, http.attributes.get("selectedSupplier")); assertFalse(historical.isActive());
    }
    private APPaymentForm payment() {
        APPaymentForm form = new APPaymentForm(); form.setOp("pay"); form.setSupplierId("1");
        form.setRequestKey("same-key"); form.setPaymentDate("2020-01-31"); form.setAmount("10");
        form.setInvoiceId(new String[] {"9"}); form.setAllocationAmount(new String[] {"10"}); return form;
    }
    private HttpFixture actor(String role) {
        HttpFixture http = new HttpFixture(); http.attributes.put("actor", new Actor(99L, "test", "検証担当", role)); return http;
    }
    private ActionMapping mapping(String path) { ActionMapping mapping = new ActionMapping(); mapping.setPath(path); return mapping; }
    private void context(HttpFixture http, APInvoiceService invoices, APSettlementService settlements) {
        StaticWebApplicationContext context = new StaticWebApplicationContext(); context.setServletContext(http.context);
        if (invoices != null) { context.getBeanFactory().registerSingleton("apInvoiceService", invoices); }
        if (settlements != null) { context.getBeanFactory().registerSingleton("apSettlementService", settlements); }
        context.getBeanFactory().registerSingleton("purchasingService", new PurchasingService() {
            public List<Supplier> listActiveSuppliers(Actor actor) {
                Supplier supplier = new Supplier(); supplier.setId(1L); supplier.setCode("S1"); supplier.setName("検証仕入先");
                return Collections.singletonList(supplier);
            }
        });
        context.getBeanFactory().registerSingleton("apReportService", new APReportService() {
            public Page<APOpenItem> searchOpenItems(APSearch search, java.util.Date asOf, boolean overdue, Actor actor) {
                return new Page<APOpenItem>(Collections.<APOpenItem>emptyList(), 0, 1, 100);
            }
        });
        context.refresh(); http.contextAttributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
    }
}
