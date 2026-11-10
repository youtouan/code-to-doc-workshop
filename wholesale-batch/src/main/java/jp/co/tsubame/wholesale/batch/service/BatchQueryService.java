package jp.co.tsubame.wholesale.batch.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import jp.co.tsubame.wholesale.batch.ExportDefinition;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.service.BaseService;
import jp.co.tsubame.wholesale.service.BillingService;

/** Bounded keyset queries return scalar values; no detached lazy associations escape. */
public class BatchQueryService extends BaseService {
    private BillingService billingService;
    public void setBillingService(BillingService value) { billingService = value; }

    public String getHealth(Actor actor) {
        require(actor, "BATCH");
        Number result = (Number) dao.session().createSQLQuery("select 1").uniqueResult();
        Checks.state(result.intValue() == 1, "batch.health", "Database health check failed");
        dao.query("select id from BatchRun", Collections.<String, Object>emptyMap()).setMaxResults(1).list();
        return "OK database=reachable schema=validated batch_tables=reachable";
    }

    public List<Long> listAllocationCandidates(Date through, int limit, Actor actor) {
        require(actor, "BATCH");
        Checks.state(limit > 0 && limit <= 1001, "batch.limit", "Candidate limit must be 1..1001");
        List<?> rows = dao.query("select distinct o.id,o.requestedDate from SalesOrder o join o.lines l "
                + "where o.status in ('APPROVED','PART_ALLOCATED','PART_SHIPPED') and o.requestedDate<=:date "
                + "and l.quantity-l.shippedQuantity-l.cancelledQuantity>l.allocatedQuantity "
                + "order by o.requestedDate,o.id", WholesaleDao.params("date", through)).setMaxResults(limit).list();
        List<Long> ids = new ArrayList<Long>();
        for (Object row : rows) { ids.add((Long) ((Object[]) row)[0]); }
        return ids;
    }

    public List<Long> listBillingCandidates(Date end, int limit, Actor actor) {
        require(actor, "BATCH");
        Checks.state(limit > 0 && limit <= 1001, "batch.limit", "Candidate limit must be 1..1001");
        List<Long> ids = new ArrayList<Long>();
        Long after = null;
        while (ids.size() < limit) {
            int take = Math.min(200, limit - ids.size());
            List<Customer> customers = billingService.listClosingCustomers(end, after, take, actor);
            for (Customer customer : customers) {
                ids.add(customer.getId());
                after = customer.getId();
            }
            if (customers.size() < take) { break; }
        }
        return ids;
    }

    public List<List<Object>> listExport(String command, long afterId, int limit, Actor actor) {
        return listExport(command, afterId, limit, null, null, null, actor);
    }

    public List<List<Object>> listExport(String command, long afterId, int limit, Date from, Date to, String status, Actor actor) {
        require(actor, "BATCH");
        Checks.state(limit > 0 && limit <= 501, "batch.limit", "Export page limit must be 1..501");
        ExportDefinition definition = ExportDefinition.get(command);
        Map<String, Object> parameters = WholesaleDao.params("after", afterId);
        String select = definition.query(from, to, status, parameters);
        List<?> rows = dao.query(select, parameters).setMaxResults(limit).list();
        List<List<Object>> result = new ArrayList<List<Object>>();
        for (Object raw : rows) {
            List<Object> row = new ArrayList<Object>(Arrays.asList((Object[]) raw));
            definition.complete(row);
            result.add(row);
        }
        return result;
    }

    public static List<String> exportHeader(String command) {
        return ExportDefinition.get(command).getHeaders();
    }
}
