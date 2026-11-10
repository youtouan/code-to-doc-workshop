package jp.co.tsubame.wholesale.dao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.StockReconciliationRow;
import jp.co.tsubame.wholesale.common.StockValuationRow;
import org.hibernate.SQLQuery;

public class StockControlReportDao {
    private WholesaleDao dao;

    public void setDao(WholesaleDao dao) { this.dao = dao; }

    @SuppressWarnings("unchecked")
    public Page<StockReconciliationRow> reconciliation(Search search) {
        validate(search);
        Checks.state(search.getStatus().length() == 0 || "OK".equals(search.getStatus())
                || "MISMATCH".equals(search.getStatus()), "stockControl.reportStatus",
                "照合状態は未指定・OK・MISMATCHから指定してください。");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        String scoped = "select b.*, w.code warehouse_code,p.code product_code,p.name product_name "
                + "from stock_balance b join warehouse w on w.id=b.warehouse_id "
                + "join product p on p.id=b.product_id where 1=1" + filters(search, parameters, "b");
        // One SQL snapshot compares independent physical, journal and allocation sources.
        String cte = "with scoped as (" + scoped + "), movement_scan as ("
                + "select m.*, sum(m.quantity_change) over(partition by m.balance_id order by m.id "
                + "rows unbounded preceding) running_stock, "
                + "sum(m.reserved_change) over(partition by m.balance_id order by m.id "
                + "rows unbounded preceding) running_reserved "
                + "from stock_movement m join scoped s on s.id=m.balance_id), journal as ("
                + "select balance_id,sum(quantity_change) ledger_stock,sum(reserved_change) ledger_reserved,"
                + "sum(case when on_hand_after<>running_stock or reserved_after<>running_reserved "
                + "then 1 else 0 end) broken_snapshots from movement_scan group by balance_id), reservations as ("
                + "select r.balance_id,sum(r.quantity) reservation_quantity,"
                + "sum(case when r.quantity<>l.allocated_quantity or s.product_id<>l.product_id "
                + "or s.warehouse_id<>o.warehouse_id then 1 else 0 end) invalid_reservations "
                + "from stock_reservation r join scoped s on s.id=r.balance_id "
                + "join sales_order_line l on l.id=r.order_line_id join sales_order o on o.id=l.order_id "
                + "group by r.balance_id), allocations as ("
                + "select s.id balance_id,sum(l.allocated_quantity) allocated_quantity "
                + "from scoped s join sales_order o on o.warehouse_id=s.warehouse_id "
                + "join sales_order_line l on l.order_id=o.id and l.product_id=s.product_id group by s.id), holds as ("
                + "select l.balance_id,count(*) hold_count,"
                + "sum(case when c.status not in ('COUNTING','REVIEWED') then 1 else 0 end) invalid_holds "
                + "from stock_count_line l join scoped s on s.id=l.balance_id "
                + "join stock_count c on c.id=l.stock_count_id where l.holding=true group by l.balance_id), metrics as ("
                + "select s.*,coalesce(j.ledger_stock,0) ledger_stock,coalesce(j.ledger_reserved,0) ledger_reserved,"
                + "coalesce(r.reservation_quantity,0) reservation_quantity,coalesce(a.allocated_quantity,0) allocated_quantity,"
                + "coalesce(j.broken_snapshots,0) broken_snapshots,coalesce(r.invalid_reservations,0) invalid_reservations,"
                + "coalesce(h.hold_count,0) hold_count,coalesce(h.invalid_holds,0) invalid_holds "
                + "from scoped s left join journal j on j.balance_id=s.id left join reservations r on r.balance_id=s.id "
                + "left join allocations a on a.balance_id=s.id left join holds h on h.balance_id=s.id), report as ("
                + "select m.*, (on_hand=ledger_stock and reserved=ledger_reserved and reserved=reservation_quantity "
                + "and reservation_quantity=allocated_quantity and broken_snapshots=0 and invalid_reservations=0 "
                + "and hold_count<=1 and invalid_holds=0 and (hold_count=0 or blocked=true)) consistent from metrics m) ";
        String status = search.getStatus().length() == 0 ? ""
                : ("OK".equals(search.getStatus()) ? " where consistent=true" : " where consistent=false");
        long total = ((Number) query(cte + "select count(*) from report" + status, parameters).uniqueResult()).longValue();
        List<Object[]> data = (List<Object[]>) query(cte + "select id,warehouse_id,warehouse_code,product_id,"
                + "product_code,product_name,on_hand,reserved,blocked,ledger_stock,ledger_reserved,"
                + "reservation_quantity,allocated_quantity,broken_snapshots,invalid_reservations,hold_count,consistent "
                + "from report" + status + " order by warehouse_id,product_id", parameters)
                .setFirstResult(search.getOffset()).setMaxResults(search.getSize()).list();
        List<StockReconciliationRow> rows = new ArrayList<StockReconciliationRow>();
        for (Object[] values : data) { rows.add(new StockReconciliationRow(values)); }
        return new Page<StockReconciliationRow>(rows, total, search.getPage(), search.getSize());
    }

    @SuppressWarnings("unchecked")
    public Page<StockValuationRow> valuation(Search search) {
        validate(search);
        Checks.state(search.getStatus().length() == 0, "stockControl.reportStatus", "評価一覧に状態条件は指定できません。");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        // Destination keys without a physical balance still appear; receipt is not invented to make them visible.
        String cte = "with transit as (select t.source_warehouse_id,t.destination_warehouse_id,l.product_id,"
                + "(l.dispatched_quantity-l.received_quantity-l.lost_quantity) quantity,l.unit_cost "
                + "from stock_transfer_line l join stock_transfer t on t.id=l.transfer_id "
                + "where l.dispatched_quantity>l.received_quantity+l.lost_quantity), keys as ("
                + "select warehouse_id,product_id from stock_balance union "
                + "select source_warehouse_id,product_id from transit union "
                + "select destination_warehouse_id,product_id from transit), outgoing as ("
                + "select source_warehouse_id warehouse_id,product_id,sum(quantity) quantity,"
                + "sum(quantity*unit_cost) value from transit group by source_warehouse_id,product_id), incoming as ("
                + "select destination_warehouse_id warehouse_id,product_id,sum(quantity) quantity,"
                + "sum(quantity*unit_cost) value from transit group by destination_warehouse_id,product_id), report as ("
                + "select k.warehouse_id,w.code warehouse_code,k.product_id,p.code product_code,p.name product_name,"
                + "coalesce(b.on_hand,0) on_hand,coalesce(b.reserved,0) reserved,coalesce(b.blocked,false) blocked,"
                + "p.standard_cost,coalesce(o.quantity,0) outgoing_quantity,coalesce(o.value,cast(0 as numeric)) outgoing_value,"
                + "coalesce(i.quantity,0) incoming_quantity,coalesce(i.value,cast(0 as numeric)) incoming_value "
                + "from keys k join warehouse w on w.id=k.warehouse_id join product p on p.id=k.product_id "
                + "left join stock_balance b on b.warehouse_id=k.warehouse_id and b.product_id=k.product_id "
                + "left join outgoing o on o.warehouse_id=k.warehouse_id and o.product_id=k.product_id "
                + "left join incoming i on i.warehouse_id=k.warehouse_id and i.product_id=k.product_id "
                + "where 1=1" + filters(search, parameters, "k") + ") ";
        long total = ((Number) query(cte + "select count(*) from report", parameters).uniqueResult()).longValue();
        List<Object[]> data = (List<Object[]>) query(cte + "select warehouse_id,warehouse_code,product_id,product_code,"
                + "product_name,on_hand,reserved,blocked,standard_cost,outgoing_quantity,outgoing_value,"
                + "incoming_quantity,incoming_value from report order by warehouse_id,product_id", parameters)
                .setFirstResult(search.getOffset()).setMaxResults(search.getSize()).list();
        List<StockValuationRow> rows = new ArrayList<StockValuationRow>();
        for (Object[] values : data) { rows.add(new StockValuationRow(values)); }
        return new Page<StockValuationRow>(rows, total, search.getPage(), search.getSize());
    }

    private void validate(Search search) {
        Checks.state(search != null, "validation.search", "検索条件が必要です。");
        Checks.state(search.getFrom() == null && search.getTo() == null && search.getCustomerId() == null,
                "stockControl.currentReport", "在庫照合・評価は現在残高です。日付・得意先条件は指定できません。");
    }

    private String filters(Search search, Map<String, Object> parameters, String alias) {
        String filter = "";
        if (search.getWarehouseId() != null) {
            filter += " and " + alias + ".warehouse_id=:warehouse";
            parameters.put("warehouse", search.getWarehouseId());
        }
        if (search.getText().length() > 0) {
            filter += " and (p.code ilike :text escape '!' or p.name ilike :text escape '!')";
            parameters.put("text", search.getLikeText());
        }
        return filter;
    }

    private SQLQuery query(String sql, Map<String, Object> parameters) {
        SQLQuery query = dao.session().createSQLQuery(sql);
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            query.setParameter(parameter.getKey(), parameter.getValue());
        }
        return query;
    }
}
