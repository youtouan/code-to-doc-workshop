package jp.co.tsubame.wholesale.dao;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.DeliverySearch;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.entity.DeliveryAttempt;

public class DeliveryAttemptDao {
    private WholesaleDao dao;
    public void setDao(WholesaleDao dao) { this.dao = dao; }

    public DeliveryAttempt latestRecorded(Long shipmentId, Date asOf) {
        Map<String, Object> parameters = WholesaleDao.params("shipment", shipmentId);
        String where = "a.shipment.id=:shipment" + cutoff("a", asOf, parameters);
        return (DeliveryAttempt) dao.query("from DeliveryAttempt a where " + where + " order by a.sequenceNumber desc",
                parameters).setMaxResults(1).uniqueResult();
    }

    public DeliveryAttempt effective(Long shipmentId, Date asOf) {
        Map<String, Object> parameters = WholesaleDao.params("shipment", shipmentId);
        String where = "a.shipment.id=:shipment and " + surviving("a", "replacement", asOf, parameters);
        return (DeliveryAttempt) dao.query("from DeliveryAttempt a where " + where + " order by a.attemptAt desc,a.id desc",
                parameters).setMaxResults(1).uniqueResult();
    }

    public Page<DeliveryAttempt> history(Long shipmentId, DeliverySearch search) {
        validate(search, false);
        Map<String, Object> parameters = WholesaleDao.params("shipment", shipmentId);
        String filter = " from DeliveryAttempt a where a.shipment.id=:shipment"
                + cutoff("a", search.getAsOfRecordedAt(), parameters) + filters(search, parameters, false);
        return dao.page("select a" + filter + " order by a.sequenceNumber desc",
                "select count(a.id)" + filter, parameters, search);
    }

    public Page<DeliveryAttempt> queue(DeliverySearch search) {
        validate(search, true);
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        Date asOf = search.getAsOfRecordedAt();
        String filter = " from DeliveryAttempt a where " + surviving("a", "replacement", asOf, parameters)
                + " and not exists (select newer.id from DeliveryAttempt newer where newer.shipment=a.shipment and "
                + surviving("newer", "replacedNewer", asOf, parameters)
                + " and (newer.attemptAt>a.attemptAt or (newer.attemptAt=a.attemptAt and newer.id>a.id)))"
                + filters(search, parameters, true);
        return dao.page("select a" + filter + " order by coalesce(a.nextAttemptDate,a.businessDate),a.id",
                "select count(a.id)" + filter, parameters, search);
    }

    private String surviving(String alias, String replacement, Date asOf, Map<String, Object> parameters) {
        // Supersession is permanent: withdrawing a correction must not revive evidence already marked incorrect.
        return alias + ".eventType<>'REVERSAL'" + cutoff(alias, asOf, parameters)
                + " and not exists (select " + replacement + ".id from DeliveryAttempt " + replacement
                + " where " + replacement + ".supersedesId=" + alias + ".id" + cutoff(replacement, asOf, parameters) + ")";
    }

    private String cutoff(String alias, Date asOf, Map<String, Object> parameters) {
        if (asOf == null) { return ""; }
        parameters.put("asOf", asOf);
        return " and " + alias + ".recordedAt<=:asOf";
    }

    private void validate(DeliverySearch search, boolean queue) {
        Checks.state(search != null, "validation.search", "配送履歴の検索条件が必要です。");
        Checks.state(search.getStatus().isEmpty() || java.util.Arrays.asList("DELIVERED","FAILED","RESCHEDULED").contains(search.getStatus())
                || (!queue && "REVERSED".equals(search.getStatus())), "delivery.searchStatus", "配送結果の検索条件が不正です。");
        Checks.state(search.getFrom() == null || search.getTo() == null || !search.getFrom().after(search.getTo()),
                "delivery.searchDates", "検索開始日は終了日以前です。");
    }

    private String filters(DeliverySearch search, Map<String, Object> parameters, boolean queue) {
        String filter = "";
        if (search.getShipmentId() != null) {
            filter += " and a.shipment.id=:filterShipment"; parameters.put("filterShipment", search.getShipmentId());
        }
        if (search.getWarehouseId() != null) {
            filter += " and a.shipment.order.warehouse.id=:warehouse"; parameters.put("warehouse", search.getWarehouseId());
        }
        if (search.getCustomerId() != null) {
            filter += " and a.shipment.order.customer.id=:customer"; parameters.put("customer", search.getCustomerId());
        }
        if (!search.getStatus().isEmpty()) {
            filter += " and a.outcome=:outcome"; parameters.put("outcome", search.getStatus());
        } else if (queue) { filter += " and a.outcome in ('FAILED','RESCHEDULED')"; }
        if (search.getFrom() != null) { filter += " and a.businessDate>=:from"; parameters.put("from", search.getFrom()); }
        if (search.getTo() != null) { filter += " and a.businessDate<=:to"; parameters.put("to", search.getTo()); }
        if (search.getDueOnOrBefore() != null) {
            filter += " and coalesce(a.nextAttemptDate,a.businessDate)<=:due"; parameters.put("due", search.getDueOnOrBefore());
        }
        if (!search.getText().isEmpty()) {
            filter += " and (a.shipment.number like :text escape '!' or a.evidenceReference like :text escape '!'"
                    + " or a.reportingCompany like :text escape '!')";
            parameters.put("text", search.getLikeText());
        }
        return filter;
    }
}
