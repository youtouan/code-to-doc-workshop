package jp.co.tsubame.wholesale.web.action;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Invoice;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.BillingService;
import jp.co.tsubame.wholesale.service.CatalogService;
import jp.co.tsubame.wholesale.web.HttpFixture;
import jp.co.tsubame.wholesale.web.form.InvoiceForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import static org.junit.Assert.*;

public class InvoiceActionTest {
    @Test public void defaultIsLatestNonFutureClosingDayIncludingMonthAndYearBoundaries() {
        String[][] cases = {
            {"2026-09-08", "2026-08-31"}, {"2026-09-10", "2026-09-10"},
            {"2026-09-19", "2026-09-10"}, {"2026-09-20", "2026-09-20"},
            {"2026-09-29", "2026-09-20"}, {"2026-09-30", "2026-09-30"},
            {"2026-10-01", "2026-09-30"}, {"2026-01-01", "2025-12-31"},
            {"2026-02-28", "2026-02-28"}, {"2024-02-28", "2024-02-20"},
            {"2024-02-29", "2024-02-29"}, {"2024-03-01", "2024-02-29"}
        };
        for (String[] value : cases) {
            Date today = Dates.parse(value[0]);
            Date selected = InvoiceAction.defaultPeriodEnd(today);
            assertEquals(value[0], value[1], Dates.format(selected));
            assertFalse(selected.after(today));
        }
    }
    @Test public void freshNewGetIs200AndDoesNotPrepareAnInvoice() throws Exception {
        HttpFixture http = fixture(new BillingService() {
            public List<Customer> listClosingCustomers(Date date, Actor actor) {
                requireClosingDay(date);
                assertFalse(date.after(Dates.today()));
                return Collections.emptyList();
            }
            public Invoice prepare(Long customerId, Date periodEnd, Actor actor) {
                fail("Opening a form must not prepare an invoice"); return null;
            }
        });
        InvoiceForm form = new InvoiceForm(); form.setOp("new");
        ActionForward forward = new InvoiceAction().execute(mapping(), form, http.request, http.response);
        assertEquals(200, http.status); assertEquals("/WEB-INF/jsp/billing/prepare.jsp", forward.getPath());
        assertEquals(Dates.format(InvoiceAction.defaultPeriodEnd(Dates.today())), form.getPeriodEnd());
        assertNull(http.attributes.get("fieldErrors")); assertNotNull(http.attributes.get("closingCustomers"));
    }
    @Test public void explicitlyRequestedInvalidLookupDateStillFailsAndIsPreserved() throws Exception {
        HttpFixture http = fixture(new BillingService() {
            public List<Customer> listClosingCustomers(Date date, Actor actor) {
                requireClosingDay(date); return Collections.emptyList();
            }
        });
        InvoiceForm form = new InvoiceForm(); form.setOp("new"); form.setPeriodEnd("2020-01-08");
        ActionForward forward = new InvoiceAction().execute(mapping(), form, http.request, http.response);
        assertEquals(422, http.status); assertEquals("/WEB-INF/jsp/billing/prepare.jsp", forward.getPath());
        assertEquals("2020-01-08", form.getPeriodEnd()); assertNotNull(http.attributes.get("fieldErrors"));
    }
    @Test public void submittedDateIsNotReplacedAndCoreBillingValidationRemainsAuthoritative() throws Exception {
        HttpFixture http = fixture(new BillingService() {
            public Invoice prepare(Long customerId, Date periodEnd, Actor actor) {
                assertEquals(Long.valueOf(1), customerId); assertEquals("2020-01-08", Dates.format(periodEnd));
                throw new BusinessException("billing.closingDate", "顧客の締日と一致していません。");
            }
        });
        http.method = "POST";
        InvoiceForm form = new InvoiceForm(); form.setOp("prepare"); form.setCustomerId("1"); form.setPeriodEnd("2020-01-08");
        new InvoiceAction().execute(mapping(), form, http.request, http.response);
        assertEquals(422, http.status); assertNull(http.headers.get("Location")); assertEquals("2020-01-08", form.getPeriodEnd());
    }
    private static void requireClosingDay(Date date) {
        int day = Dates.calendar(date).get(Calendar.DAY_OF_MONTH);
        if (day != 10 && day != 20 && !Dates.sameDay(date, Dates.monthEnd(date))) {
            throw new BusinessException("billing.closingDate", "締日は10日・20日・末日です。");
        }
    }
    private HttpFixture fixture(BillingService billing) {
        HttpFixture http = new HttpFixture(); http.attributes.put("actor", new Actor(99L, "billing-test", "請求担当", "BILLING"));
        StaticWebApplicationContext context = new StaticWebApplicationContext(); context.setServletContext(http.context);
        context.getBeanFactory().registerSingleton("billingService", billing);
        context.getBeanFactory().registerSingleton("catalogService", new CatalogService() {
            public List<Customer> listActiveCustomers(Actor actor) { return Collections.emptyList(); }
            public List<Product> listActiveProducts(Actor actor) { return Collections.emptyList(); }
            public List<Warehouse> listActiveWarehouses(Actor actor) { return Collections.emptyList(); }
        });
        context.refresh(); http.contextAttributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        return http;
    }
    private ActionMapping mapping() { ActionMapping mapping = new ActionMapping(); mapping.setPath("/invoices"); return mapping; }
}
