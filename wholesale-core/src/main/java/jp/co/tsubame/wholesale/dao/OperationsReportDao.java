package jp.co.tsubame.wholesale.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.co.tsubame.wholesale.common.BacklogRow;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.ReportFilter;
import jp.co.tsubame.wholesale.common.SalesSummaryRow;
import jp.co.tsubame.wholesale.common.StockActivityRow;
import jp.co.tsubame.wholesale.common.SupplierQualityRow;
import org.hibernate.SQLQuery;

public class OperationsReportDao {
    private WholesaleDao dao;

    public void setDao(WholesaleDao dao) {
        this.dao = dao;
    }

    public Page<SalesSummaryRow> sales(String dimension, ReportFilter filter) {
        String key;
        String master;
        if ("CUSTOMER".equals(dimension)) {
            key = "customer_id";
            master = "customer";
        } else if ("PRODUCT".equals(dimension)) {
            key = "product_id";
            master = "product";
        } else {
            Checks.state("WAREHOUSE".equals(dimension), "report.dimension", "得意先・商品・倉庫から集計軸を選択してください。");
            key = "warehouse_id";
            master = "warehouse";
        }
        Map<String, Object> parameters = period(filter);
        String events = "(select o.customer_id,l.product_id,o.warehouse_id,s.shipped_date as event_date,"
                + "s.id as shipment_id,cast(null as bigint) as return_id,sl.quantity as shipped_quantity,"
                + "0 as returned_quantity,sl.quantity*sl.unit_price as shipped_amount,cast(0 as numeric) as returned_amount "
                + "from shipping_shipment s join shipping_shipment_line sl on sl.shipment_id=s.id "
                + "join sales_order o on o.id=s.order_id join sales_order_line l on l.id=sl.order_line_id "
                + "where s.status='CONFIRMED' "
                + "union all "
                + "select o.customer_id,l.product_id,o.warehouse_id,r.received_date,"
                + "cast(null as bigint),r.id,0,rl.quantity,cast(0 as numeric),rl.quantity*rl.unit_price "
                + "from sales_return r join sales_return_line rl on rl.return_id=r.id "
                + "join shipping_shipment s on s.id=r.shipment_id "
                + "join shipping_shipment_line sl on sl.id=rl.shipment_line_id "
                + "join sales_order_line l on l.id=sl.order_line_id join sales_order o on o.id=s.order_id "
                + "where r.status='RECEIVED') e";
        String where = " where e.event_date>=:fromDate and e.event_date<=:toDate";
        where = dimensions(where, parameters, filter, "e");
        where += " and (m.code like :text escape '!' or m.name like :text escape '!')";
        parameters.put("text", filter.getLikeText());
        String grouped = " from " + events + " join " + master + " m on m.id=e." + key + where
                + " group by m.id,m.code,m.name";
        if (filter.isExceptionsOnly()) {
            grouped += " having sum(e.returned_quantity)>0";
        }
        long total = scalar("select count(*) from (select m.id" + grouped + ") totals", parameters);
        List<?> tuples = query("select m.id,m.code,m.name,sum(e.shipped_quantity) as shipped_quantity,"
                + "sum(e.returned_quantity) as returned_quantity,count(distinct e.shipment_id) as shipment_count,"
                + "count(distinct e.return_id) as return_count,sum(e.shipped_amount) as shipped_amount,"
                + "sum(e.returned_amount) as returned_amount"
                + grouped + " order by sum(e.shipped_amount)-sum(e.returned_amount) desc,m.code", parameters)
                .setFirstResult(filter.getOffset()).setMaxResults(filter.getSize()).list();
        List<SalesSummaryRow> rows = new ArrayList<SalesSummaryRow>();
        for (Object tuple : tuples) {
            Object[] row = (Object[]) tuple;
            rows.add(new SalesSummaryRow(number(row[0]), (String) row[1], (String) row[2],
                    number(row[3]), number(row[4]), number(row[5]), number(row[6]),
                    money(row[7]), money(row[8])));
        }
        return new Page<SalesSummaryRow>(rows, total, filter.getPage(), filter.getSize());
    }

    public Page<BacklogRow> backlog(ReportFilter filter) {
        Map<String, Object> parameters = period(filter);
        String where = " from sales_order o join sales_order_line l on l.order_id=o.id "
                + "join customer c on c.id=o.customer_id join warehouse w on w.id=o.warehouse_id "
                + "join product p on p.id=l.product_id where o.status in "
                + "('APPROVED','PART_ALLOCATED','ALLOCATED','PART_SHIPPED') "
                + "and l.quantity-l.shipped_quantity-l.cancelled_quantity>0 "
                + "and o.requested_date>=:fromDate and o.requested_date<=:toDate";
        where = orderDimensions(where, parameters, filter);
        where += " and (o.number like :text escape '!' or c.name like :text escape '!' "
                + "or l.product_code like :text escape '!' or l.product_name like :text escape '!')";
        parameters.put("text", filter.getLikeText());
        if (filter.isExceptionsOnly()) {
            where += " and (o.requested_date<:today or c.on_hold=true or c.active=false "
                    + "or w.active=false or p.active=false "
                    + "or l.quantity-l.shipped_quantity-l.cancelled_quantity>l.allocated_quantity)";
            parameters.put("today", Dates.today());
        }
        long total = scalar("select count(*)" + where, parameters);
        List<?> tuples = query("select o.id,o.number,l.line_number,c.code as customer_code,o.customer_name,w.code as warehouse_code,"
                + "l.product_code,l.product_name,o.requested_date,o.status,"
                + "l.quantity-l.shipped_quantity-l.cancelled_quantity as open_quantity,l.allocated_quantity,l.unit_price,"
                + "c.on_hold,(not c.active or not w.active or not p.active) as master_inactive"
                + where + " order by o.requested_date,o.id,l.line_number", parameters)
                .setFirstResult(filter.getOffset()).setMaxResults(filter.getSize()).list();
        List<BacklogRow> rows = new ArrayList<BacklogRow>();
        for (Object tuple : tuples) {
            Object[] row = (Object[]) tuple;
            rows.add(new BacklogRow(number(row[0]), (String) row[1], ((Number) row[2]).intValue(),
                    (String) row[3], (String) row[4], (String) row[5], (String) row[6], (String) row[7],
                    (Date) row[8], (String) row[9], ((Number) row[10]).intValue(), ((Number) row[11]).intValue(),
                    money(row[12]), (Boolean) row[13], (Boolean) row[14]));
        }
        return new Page<BacklogRow>(rows, total, filter.getPage(), filter.getSize());
    }

    public Page<SupplierQualityRow> supplierQuality(ReportFilter filter) {
        Map<String, Object> parameters = period(filter);
        String where = " from purchase_receipt r join purchase_receipt_line rl on rl.receipt_id=r.id "
                + "join purchase_order o on o.id=r.order_id join purchase_order_line ol on ol.id=rl.order_line_id "
                + "join supplier s on s.id=o.supplier_id where r.receipt_date>=:fromDate and r.receipt_date<=:toDate "
                + "and (s.code like :text escape '!' or s.name like :text escape '!')";
        parameters.put("text", filter.getLikeText());
        if (filter.getSupplierId() != null) {
            where += " and s.id=:supplier";
            parameters.put("supplier", filter.getSupplierId());
        }
        if (filter.getWarehouseId() != null) {
            where += " and o.warehouse_id=:warehouse";
            parameters.put("warehouse", filter.getWarehouseId());
        }
        if (filter.getProductId() != null) {
            where += " and ol.product_id=:product";
            parameters.put("product", filter.getProductId());
        }
        String group = where + " group by s.id,s.code,s.name";
        if (filter.isExceptionsOnly()) {
            group += " having sum(rl.rejected_quantity)>0 "
                    + "or sum(case when r.receipt_date>ol.expected_date then rl.accepted_quantity else 0 end)>0";
        }
        long total = scalar("select count(*) from (select s.id" + group + ") totals", parameters);
        List<?> tuples = query("select s.id,s.code,s.name,count(distinct r.id) as receipt_count,"
                + "sum(rl.accepted_quantity) as accepted_quantity,sum(rl.rejected_quantity) as rejected_quantity,"
                + "sum(case when r.receipt_date>ol.expected_date "
                + "then rl.accepted_quantity else 0 end) as late_quantity,sum(rl.accepted_amount) as accepted_amount"
                + group + " order by sum(rl.rejected_quantity) desc,s.code", parameters)
                .setFirstResult(filter.getOffset()).setMaxResults(filter.getSize()).list();
        List<SupplierQualityRow> rows = new ArrayList<SupplierQualityRow>();
        for (Object tuple : tuples) {
            Object[] row = (Object[]) tuple;
            rows.add(new SupplierQualityRow(number(row[0]), (String) row[1], (String) row[2],
                    number(row[3]), number(row[4]), number(row[5]), number(row[6]), money(row[7])));
        }
        return new Page<SupplierQualityRow>(rows, total, filter.getPage(), filter.getSize());
    }

    public Page<StockActivityRow> stockActivity(ReportFilter filter) {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("fromDate", filter.getFrom());
        parameters.put("toExclusive", Dates.addDays(filter.getTo(), 1));
        parameters.put("text", filter.getLikeText());
        String where = " from stock_movement m join stock_balance b on b.id=m.balance_id "
                + "join warehouse w on w.id=b.warehouse_id join product p on p.id=b.product_id "
                + "where m.occurred_at>=:fromDate and m.occurred_at<:toExclusive "
                + "and (p.code like :text escape '!' or p.name like :text escape '!')";
        if (filter.getWarehouseId() != null) {
            where += " and w.id=:warehouse";
            parameters.put("warehouse", filter.getWarehouseId());
        }
        if (filter.getProductId() != null) {
            where += " and p.id=:product";
            parameters.put("product", filter.getProductId());
        }
        if (filter.getStatus().length() != 0) {
            where += " and m.movement_type=:movementType";
            parameters.put("movementType", filter.getStatus());
        }
        String group = where + " group by cast(m.occurred_at as date),w.code,m.movement_type";
        long total = scalar("select count(*) from (select w.code" + group + ") totals", parameters);
        List<?> tuples = query("select cast(m.occurred_at as date) as recorded_date,w.code,m.movement_type,count(*) as movement_count,"
                + "sum(case when m.quantity_change>0 then m.quantity_change else 0 end) as inbound_quantity,"
                + "sum(case when m.quantity_change<0 then -cast(m.quantity_change as bigint) else 0 end) as outbound_quantity,"
                + "sum(case when m.reserved_change>0 then m.reserved_change else 0 end) as reservation_increase,"
                + "sum(case when m.reserved_change<0 then -cast(m.reserved_change as bigint) else 0 end) as reservation_decrease"
                + group + " order by cast(m.occurred_at as date) desc,w.code,m.movement_type", parameters)
                .setFirstResult(filter.getOffset()).setMaxResults(filter.getSize()).list();
        List<StockActivityRow> rows = new ArrayList<StockActivityRow>();
        for (Object tuple : tuples) {
            Object[] row = (Object[]) tuple;
            rows.add(new StockActivityRow((Date) row[0], (String) row[1], (String) row[2],
                    number(row[3]), number(row[4]), number(row[5]), number(row[6]), number(row[7])));
        }
        return new Page<StockActivityRow>(rows, total, filter.getPage(), filter.getSize());
    }

    private String dimensions(String where, Map<String, Object> parameters, ReportFilter filter, String alias) {
        if (filter.getCustomerId() != null) {
            where += " and " + alias + ".customer_id=:customer";
            parameters.put("customer", filter.getCustomerId());
        }
        if (filter.getWarehouseId() != null) {
            where += " and " + alias + ".warehouse_id=:warehouse";
            parameters.put("warehouse", filter.getWarehouseId());
        }
        if (filter.getProductId() != null) {
            where += " and " + alias + ".product_id=:product";
            parameters.put("product", filter.getProductId());
        }
        return where;
    }

    private String orderDimensions(String where, Map<String, Object> parameters, ReportFilter filter) {
        if (filter.getCustomerId() != null) {
            where += " and o.customer_id=:customer";
            parameters.put("customer", filter.getCustomerId());
        }
        if (filter.getWarehouseId() != null) {
            where += " and o.warehouse_id=:warehouse";
            parameters.put("warehouse", filter.getWarehouseId());
        }
        if (filter.getProductId() != null) {
            where += " and l.product_id=:product";
            parameters.put("product", filter.getProductId());
        }
        return where;
    }

    private Map<String, Object> period(ReportFilter filter) {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("fromDate", filter.getFrom());
        parameters.put("toDate", filter.getTo());
        return parameters;
    }

    private SQLQuery query(String sql, Map<String, Object> parameters) {
        SQLQuery query = dao.session().createSQLQuery(sql);
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        return query;
    }

    private long scalar(String sql, Map<String, Object> parameters) {
        return ((Number) query(sql, parameters).uniqueResult()).longValue();
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private BigDecimal money(Object value) {
        return ((BigDecimal) value).setScale(2);
    }
}
