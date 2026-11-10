package jp.co.tsubame.wholesale.web.action;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.StockReconciliationRow;
import jp.co.tsubame.wholesale.common.StockValuationRow;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.CatalogService;
import jp.co.tsubame.wholesale.service.StockControlReportService;
import jp.co.tsubame.wholesale.web.HttpFixture;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import static org.junit.Assert.*;

public class StockReportActionTest {
    @Test public void reportEndpointsAreReadOnly() throws Exception {
        for (String op : new String[] {"list", "reconciliation", "valuation"}) {
            HttpFixture http = new HttpFixture(); http.method = "POST";
            BaseForm form = new BaseForm(); form.setOp(op);
            new StockReportAction().execute(mapping(), form, http.request, http.response);
            assertEquals(405, http.status); assertEquals("GET", http.headers.get("Allow"));
        }
    }
    @Test public void defaultReportCallsActorFirstReconciliationApi() throws Exception {
        HttpFixture http = fixture(new StockControlReportService() {
            public Page<StockReconciliationRow> listReconciliation(Actor actor, Search search) {
                assertEquals("warehouse-test", actor.getLogin());
                assertEquals(Long.valueOf(1), search.getWarehouseId()); assertEquals("P10", search.getText());
                StockReconciliationRow consistent = reconciliation(true);
                StockReconciliationRow inconsistent = reconciliation(false);
                return new Page<StockReconciliationRow>(Arrays.asList(consistent, inconsistent), 30, 1, 25);
            }
        });
        BaseForm form = new BaseForm(); form.setWarehouseId("1"); form.setText("P10");
        ActionForward forward = new StockReportAction().execute(mapping(), form, http.request, http.response);
        assertEquals("/WEB-INF/jsp/control/reports.jsp", forward.getPath());
        assertEquals(Boolean.FALSE, http.attributes.get("valuation"));
        assertEquals(Integer.valueOf(1), http.attributes.get("pageMismatches"));
        assertNotNull(http.attributes.get("reportAsOf"));
    }
    @Test public void valuationSeparatesCurrentCostFromTransitSnapshotsWithoutDoubleCounting() throws Exception {
        HttpFixture http = fixture(new StockControlReportService() {
            public Page<StockValuationRow> listValuation(Actor actor, Search search) {
                StockValuationRow row = new StockValuationRow(new Object[] {
                    1L, "W1", 10L, "P10", "評価商品", 10L, 2L, Boolean.FALSE, new BigDecimal("5.00"),
                    3L, new BigDecimal("12.00"), 4L, new BigDecimal("17.00")
                });
                return new Page<StockValuationRow>(Collections.singletonList(row), 250, 1, 25);
            }
        });
        BaseForm form = new BaseForm(); form.setOp("valuation");
        new StockReportAction().execute(mapping(), form, http.request, http.response);
        assertEquals(Boolean.TRUE, http.attributes.get("valuation"));
        assertEquals(new BigDecimal("50.00"), http.attributes.get("pagePhysicalValue"));
        assertEquals(new BigDecimal("12.00"), http.attributes.get("pageOutgoingValue"));
        assertEquals(new BigDecimal("62.00"), http.attributes.get("pageAttributedValue"));
    }
    @Test public void businessFilterFailureKeepsQueryAndReportType() throws Exception {
        HttpFixture http = fixture(new StockControlReportService() {
            public Page<StockValuationRow> listValuation(Actor actor, Search search) {
                throw new BusinessException("stockControl.reportStatus", "評価一覧に状態条件は指定できません。");
            }
        });
        BaseForm form = new BaseForm(); form.setOp("valuation"); form.setStatus("MISMATCH"); form.setText("入力保持");
        ActionForward forward = new StockReportAction().execute(mapping(), form, http.request, http.response);
        assertEquals(422, http.status); assertEquals("/WEB-INF/jsp/control/reports.jsp", forward.getPath());
        assertEquals(Boolean.TRUE, http.attributes.get("valuation")); assertEquals("入力保持", form.getText());
        assertNotNull(http.attributes.get("warehouses")); assertNull(http.attributes.get("reportAsOf"));
    }
    private static StockReconciliationRow reconciliation(boolean consistent) {
        return new StockReconciliationRow(new Object[] {
            9L, 1L, "W1", 10L, "P10", "照合商品", 10L, 2L, Boolean.FALSE,
            10L, 2L, 2L, 2L, consistent ? 0L : 1L, 0L, 0L, Boolean.valueOf(consistent)
        });
    }
    private HttpFixture fixture(StockControlReportService reports) {
        HttpFixture http = new HttpFixture();
        http.attributes.put("actor", new Actor(99L, "warehouse-test", "倉庫担当", "WAREHOUSE"));
        StaticWebApplicationContext context = new StaticWebApplicationContext(); context.setServletContext(http.context);
        context.getBeanFactory().registerSingleton("stockControlReportService", reports);
        context.getBeanFactory().registerSingleton("catalogService", new CatalogService() {
            public List<Warehouse> listActiveWarehouses(Actor actor) { return Collections.emptyList(); }
        });
        context.refresh(); http.contextAttributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        return http;
    }
    private ActionMapping mapping() {
        ActionMapping mapping = new ActionMapping(); mapping.setPath("/stockReports"); return mapping;
    }
}
