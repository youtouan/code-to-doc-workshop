package jp.co.tsubame.wholesale.web.action;

import java.math.BigDecimal;
import java.util.List;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.StockCountEntry;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.entity.StockCount;
import jp.co.tsubame.wholesale.entity.StockCountLine;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.StockCountService;
import jp.co.tsubame.wholesale.web.HttpFixture;
import jp.co.tsubame.wholesale.web.form.CountForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import static org.junit.Assert.*;

public class CountWorkflowActionTest {
    @Test public void observationsBelowReservedQuantityReachRecordService() throws Exception {
        final StockCount count = count("COUNTING");
        HttpFixture http = fixture("WAREHOUSE", new StockCountService() {
            public StockCount record(Actor actor, Long id, int version, List<StockCountEntry> entries) {
                assertEquals(Long.valueOf(10), id); assertEquals(4, version);
                assertEquals(1, entries.size()); assertEquals(Long.valueOf(101), entries.get(0).getLineId());
                assertEquals(Integer.valueOf(2), entries.get(0).getCountedQuantity());
                assertTrue(entries.get(0).getCountedQuantity().intValue() < count.getLines().get(0).getSnapshotReserved());
                assertEquals("実測による不足", entries.get(0).getNote());
                return count;
            }
        });
        CountForm form = form("record");
        form.setLineId(new String[] {"101"}); form.setCountedQuantity(new String[] {"2"});
        form.setLineNote(new String[] {"実測による不足"});
        assertNull(new CountAction().execute(mapping(), form, http.request, http.response));
        assertEquals(303, http.status);
        assertEquals("/wholesale/counts.do?op=detail&id=10", http.headers.get("Location"));
    }
    @Test public void shortageDoesNotPreventReviewSubmissionInWeb() throws Exception {
        final StockCount count = count("COUNTING");
        HttpFixture http = fixture("WAREHOUSE", new StockCountService() {
            public StockCount review(Actor actor, Long id, int version) {
                assertEquals(1, count.getReservationShortageLines());
                assertEquals(Integer.valueOf(3), count.getLines().get(0).getReservationShortage());
                count.setStatus("REVIEWED");
                return count;
            }
        });
        assertNull(new CountAction().execute(mapping(), form("review"), http.request, http.response));
        assertEquals(303, http.status); assertEquals("REVIEWED", count.getStatus());
    }
    @Test public void approvalFailureRedisplaysShortageWithoutChangingStockOrHold() throws Exception {
        final StockCount count = count("REVIEWED");
        HttpFixture http = fixture("MANAGER", new StockCountService() {
            public StockCount approve(Actor actor, Long id, int version) {
                throw new BusinessException("inventory.insufficient", "引当済数量を下回るため承認できません。");
            }
            public StockCount get(Actor actor, Long id) { return count; }
        });
        CountForm form = form("approve");
        ActionForward forward = new CountAction().execute(mapping(), form, http.request, http.response);
        assertEquals(422, http.status); assertNull(http.headers.get("Location"));
        assertEquals("/WEB-INF/jsp/control/count-detail.jsp", forward.getPath());
        assertSame(count, http.attributes.get("count")); assertEquals("REVIEWED", count.getStatus());
        StockCountLine line = count.getLines().get(0);
        assertEquals(Integer.valueOf(2), line.getCountedQuantity()); assertEquals(Integer.valueOf(3), line.getReservationShortage());
        assertEquals(10, line.getBalance().getOnHand()); assertEquals(5, line.getBalance().getReserved());
        assertTrue(line.isHolding()); assertTrue(line.getBalance().isBlocked());
        assertNotNull(http.attributes.get("fieldErrors"));
    }
    private static StockCount count(String status) {
        Warehouse warehouse = new Warehouse(); warehouse.setId(1L); warehouse.setName("検証倉庫");
        Product product = new Product(); product.setId(20L); product.setCode("P20"); product.setName("検証商品");
        StockBalance balance = new StockBalance(); balance.setId(30L); balance.setWarehouse(warehouse);
        balance.setProduct(product); balance.setOnHand(10); balance.setReserved(5); balance.setBlocked(true);
        StockCount count = new StockCount(); count.setId(10L); count.setVersion(4); count.setNumber("CNT-TEST");
        count.setWarehouse(warehouse); count.setStatus(status); count.setCreatedById(77L); count.setCreatedBy("counter");
        StockCountLine line = new StockCountLine(); line.setId(101L); line.setStockCount(count); line.setBalance(balance);
        line.setSnapshotOnHand(10); line.setSnapshotReserved(5); line.setCountedQuantity(Integer.valueOf(2));
        line.setUnitCost(new BigDecimal("10.00")); line.setNote("実測による不足"); line.setHolding(true);
        count.getLines().add(line); return count;
    }
    private CountForm form(String op) {
        CountForm form = new CountForm(); form.setOp(op); form.setId("10"); form.setVersion("4"); return form;
    }
    private HttpFixture fixture(String role, StockCountService service) {
        HttpFixture http = new HttpFixture(); http.method = "POST";
        http.attributes.put("actor", new Actor(99L, "test-actor", "検証", role));
        StaticWebApplicationContext context = new StaticWebApplicationContext(); context.setServletContext(http.context);
        context.getBeanFactory().registerSingleton("stockCountService", service); context.refresh();
        http.contextAttributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context); return http;
    }
    private ActionMapping mapping() { ActionMapping mapping = new ActionMapping(); mapping.setPath("/counts"); return mapping; }
}
