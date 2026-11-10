package jp.co.tsubame.wholesale.web.action;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BacklogRow;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.ReportFilter;
import jp.co.tsubame.wholesale.common.SalesSummaryRow;
import jp.co.tsubame.wholesale.common.StockActivityRow;
import jp.co.tsubame.wholesale.common.SupplierQualityRow;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.CatalogService;
import jp.co.tsubame.wholesale.service.OperationsReportService;
import jp.co.tsubame.wholesale.service.PurchasingService;
import jp.co.tsubame.wholesale.web.HttpFixture;
import jp.co.tsubame.wholesale.web.form.OperationsReportForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import static org.junit.Assert.*;

public class OperationsReportActionTest {
    @Test public void everyReportRejectsPostBeforeServiceAccess() throws Exception {
        for (String mode : new String[] {"list", "sales", "backlog", "supplierQuality", "stockActivity"}) {
            HttpFixture http = new HttpFixture(); http.method = "POST";
            OperationsReportForm form = new OperationsReportForm(); form.setOp(mode);
            new OperationsReportAction().execute(mapping(), form, http.request, http.response);
            assertEquals(mode, 405, http.status); assertEquals("GET", http.headers.get("Allow"));
        }
    }
    @Test public void reportSpecificPermissionsAreEnforcedBeforeReferenceDataLoads() throws Exception {
        String[][] cases = {{"WAREHOUSE", "sales"}, {"BILLING", "backlog"}, {"SALES", "supplierQuality"},
                {"SALES", "stockActivity"}, {"BATCH", "sales"}, {"BATCH", "supplierQuality"}};
        for (String[] entry : cases) {
            HttpFixture http = actor(entry[0]); OperationsReportForm form = new OperationsReportForm(); form.setOp(entry[1]);
            new OperationsReportAction().execute(mapping(), form, http.request, http.response);
            assertEquals(entry[0] + "/" + entry[1], 403, http.status);
        }
    }
    @Test public void defaultForWarehouseOrBatchIsAnAuthorizedBacklogReport() throws Exception {
        for (String role : new String[] {"WAREHOUSE", "BATCH"}) {
            final String expectedRole = role;
            HttpFixture http = fixture(role, new OperationsReportService() {
                public Page<BacklogRow> searchBacklog(ReportFilter filter, Actor actor) {
                    assertTrue(actor.hasRole(expectedRole)); filter.validateBacklogPeriod();
                    return empty(filter);
                }
            });
            OperationsReportForm form = new OperationsReportForm();
            ActionForward forward = new OperationsReportAction().execute(mapping(), form, http.request, http.response);
            assertEquals(200, http.status); assertEquals("backlog", form.getOp());
            assertEquals("/WEB-INF/jsp/reports/backlog.jsp", forward.getPath());
        }
    }
    @Test public void freshSalesReportShowsActualDefaultPeriodAndNegativeNetWithoutClamping() throws Exception {
        HttpFixture http = fixture("SALES", new OperationsReportService() {
            public Page<SalesSummaryRow> searchSales(String dimension, ReportFilter filter, Actor actor) {
                assertEquals("CUSTOMER", dimension);
                assertEquals(Dates.format(Dates.addDays(Dates.today(), -30)), Dates.format(filter.getFrom()));
                assertEquals(Dates.format(Dates.today()), Dates.format(filter.getTo()));
                SalesSummaryRow row = new SalesSummaryRow(1L, "C1", "現在の名称", 5, 7, 1, 1,
                        new BigDecimal("50.00"), new BigDecimal("70.00"));
                return new Page<SalesSummaryRow>(Collections.singletonList(row), 1, 1, 25);
            }
        });
        OperationsReportForm form = new OperationsReportForm();
        new OperationsReportAction().execute(mapping(), form, http.request, http.response);
        assertEquals(200, http.status); assertEquals(new BigDecimal("-20.00"), http.attributes.get("pageNetAmount"));
        assertEquals("/customers.do", http.attributes.get("dimensionRoute")); assertNotNull(http.attributes.get("reportGeneratedAt"));
    }
    @Test public void allThreeSalesDimensionsArePassedExplicitlyToCore() throws Exception {
        String[] dimensions = {"CUSTOMER", "PRODUCT", "WAREHOUSE"};
        String[] routes = {"/customers.do", "/products.do", "/warehouses.do"};
        for (int i = 0; i < dimensions.length; i++) {
            final String dimension = dimensions[i];
            HttpFixture http = fixture("BILLING", new OperationsReportService() {
                public Page<SalesSummaryRow> searchSales(String value, ReportFilter filter, Actor actor) {
                    assertEquals(dimension, value); return empty(filter);
                }
            });
            OperationsReportForm form = new OperationsReportForm(); form.setOp("sales"); form.setDimension(dimension);
            new OperationsReportAction().execute(mapping(), form, http.request, http.response);
            assertEquals(routes[i], http.attributes.get("dimensionRoute"));
        }
    }
    @Test public void actualsRejectFutureDatesButBacklogAcceptsFuturePromisesWithin366Days() {
        final OperationsReportForm form = period(Dates.today(), Dates.addDays(Dates.today(), 366));
        assertEquals(Dates.format(Dates.addDays(Dates.today(), 366)),
                Dates.format(OperationsReportAction.filter(form, "backlog").getTo()));
        for (final String mode : new String[] {"sales", "supplierQuality", "stockActivity"}) {
            invalid(new Runnable() { public void run() { OperationsReportAction.filter(form, mode); } });
        }
        final OperationsReportForm beyond = period(Dates.addDays(Dates.today(), 1), Dates.addDays(Dates.today(), 367));
        invalid(new Runnable() { public void run() { OperationsReportAction.filter(beyond, "backlog"); } });
    }
    @Test public void rangeLimitIs367InclusiveDaysAndReversedRangesAreRejected() {
        OperationsReportForm accepted = period(Dates.addDays(Dates.today(), -366), Dates.today());
        OperationsReportAction.filter(accepted, "sales");
        final OperationsReportForm tooLong = period(Dates.addDays(Dates.today(), -367), Dates.today());
        invalid(new Runnable() { public void run() { OperationsReportAction.filter(tooLong, "sales"); } });
        final OperationsReportForm reversed = period(Dates.today(), Dates.addDays(Dates.today(), -1));
        invalid(new Runnable() { public void run() { OperationsReportAction.filter(reversed, "sales"); } });
    }
    @Test public void supplierQualityUsesSupplierProductAndExceptionFiltersAndRetainsPagination() throws Exception {
        HttpFixture http = fixture("WAREHOUSE", new OperationsReportService() {
            public Page<SupplierQualityRow> searchSupplierQuality(ReportFilter filter, Actor actor) {
                assertEquals(Long.valueOf(5), filter.getSupplierId()); assertEquals(Long.valueOf(7), filter.getWarehouseId());
                assertEquals(Long.valueOf(9), filter.getProductId()); assertTrue(filter.isExceptionsOnly());
                assertEquals(2, filter.getPage()); assertEquals(10, filter.getSize()); return empty(filter);
            }
        });
        OperationsReportForm form = new OperationsReportForm(); form.setOp("supplierQuality");
        form.setSupplierId("5"); form.setWarehouseId("7"); form.setProductId("9");
        form.setExceptionsOnly("true"); form.setPageNumber("2"); form.setSize("10");
        ActionForward forward = new OperationsReportAction().execute(mapping(), form, http.request, http.response);
        assertEquals("/WEB-INF/jsp/reports/quality.jsp", forward.getPath());
        @SuppressWarnings("unchecked")
        Map<String, String> parameters = (Map<String, String>) http.attributes.get("extraPageParameters");
        assertEquals("5", parameters.get("supplierId")); assertEquals("9", parameters.get("productId"));
        assertEquals("true", parameters.get("exceptionsOnly"));
    }
    @Test public void stockActivityPassesRecordedTimestampPeriodAndExactMovementType() throws Exception {
        HttpFixture http = fixture("BATCH", new OperationsReportService() {
            public Page<StockActivityRow> searchStockActivity(ReportFilter filter, Actor actor) {
                assertEquals("TRANSFER_IN", filter.getStatus()); assertEquals(Long.valueOf(9), filter.getProductId());
                filter.validatePeriod(); return empty(filter);
            }
        });
        OperationsReportForm form = new OperationsReportForm(); form.setOp("stockActivity");
        form.setStatus("TRANSFER_IN"); form.setProductId("9");
        ActionForward forward = new OperationsReportAction().execute(mapping(), form, http.request, http.response);
        assertEquals(200, http.status); assertEquals("/WEB-INF/jsp/reports/activity.jsp", forward.getPath());
    }
    @Test public void unsupportedOrMalformedFiltersAreNotSilentlyIgnored() {
        final OperationsReportForm form = period(Dates.today(), Dates.today()); form.setProductId("not-an-id");
        invalid(new Runnable() { public void run() { OperationsReportAction.filter(form, "sales"); } });
        form.setProductId(""); form.setSupplierId("1");
        invalid(new Runnable() { public void run() { OperationsReportAction.filter(form, "sales"); } });
        form.setSupplierId(""); form.setExceptionsOnly("true");
        invalid(new Runnable() { public void run() { OperationsReportAction.filter(form, "stockActivity"); } });
    }
    @Test public void invalidRequestedReportPeriodReturns422AndPreservesInputs() throws Exception {
        HttpFixture http = fixture("BILLING", new OperationsReportService());
        OperationsReportForm form = period(Dates.today(), Dates.addDays(Dates.today(), 1));
        form.setOp("sales"); form.setText("入力保持");
        ActionForward forward = new OperationsReportAction().execute(mapping(), form, http.request, http.response);
        assertEquals(422, http.status); assertEquals("/WEB-INF/jsp/reports/sales.jsp", forward.getPath());
        assertEquals("入力保持", form.getText()); assertNull(http.attributes.get("reportGeneratedAt"));
        assertNotNull(http.attributes.get("fieldErrors")); assertNotNull(http.attributes.get("products"));
    }
    private static <T> Page<T> empty(ReportFilter filter) {
        return new Page<T>(Collections.<T>emptyList(), 0, filter.getPage(), filter.getSize());
    }
    private OperationsReportForm period(Date from, Date to) {
        OperationsReportForm form = new OperationsReportForm(); form.setFrom(Dates.format(from)); form.setTo(Dates.format(to)); return form;
    }
    private void invalid(Runnable run) {
        try { run.run(); fail("Expected rejected report filter"); }
        catch (BusinessException expected) { assertNotNull(expected.getMessage()); }
    }
    private HttpFixture actor(String role) {
        HttpFixture http = new HttpFixture(); http.attributes.put("actor", new Actor(99L, "report-test", "検証", role)); return http;
    }
    private HttpFixture fixture(String role, OperationsReportService reports) {
        HttpFixture http = actor(role);
        StaticWebApplicationContext context = new StaticWebApplicationContext(); context.setServletContext(http.context);
        context.getBeanFactory().registerSingleton("operationsReportService", reports);
        context.getBeanFactory().registerSingleton("catalogService", new CatalogService() {
            public List<Customer> listActiveCustomers(Actor actor) { return Collections.emptyList(); }
            public List<Product> listActiveProducts(Actor actor) { return Collections.emptyList(); }
            public List<Warehouse> listActiveWarehouses(Actor actor) { return Collections.emptyList(); }
        });
        context.getBeanFactory().registerSingleton("purchasingService", new PurchasingService() {
            public List<Supplier> listActiveSuppliers(Actor actor) { return Collections.emptyList(); }
        });
        context.refresh(); http.contextAttributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        return http;
    }
    private ActionMapping mapping() { ActionMapping mapping = new ActionMapping(); mapping.setPath("/operationsReports"); return mapping; }
}
