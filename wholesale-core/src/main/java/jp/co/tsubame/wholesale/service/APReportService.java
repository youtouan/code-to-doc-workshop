package jp.co.tsubame.wholesale.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import jp.co.tsubame.wholesale.common.APOpenItem;
import jp.co.tsubame.wholesale.common.APReceiptAvailability;
import jp.co.tsubame.wholesale.common.APSearch;
import jp.co.tsubame.wholesale.common.APStatement;
import jp.co.tsubame.wholesale.common.APStatementEntry;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Money;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.dao.APLedger;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.Supplier;
import org.hibernate.SQLQuery;

public class APReportService extends BaseService {
    public Page<APReceiptAvailability> searchAvailableReceipts(APSearch search, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        requireSearch(search);
        String source = "(select l.id,r.number as receipt_number,o.number as order_number,o.supplier_id,"
                + "ol.product_id,ol.product_code,ol.product_name,r.receipt_date,l.accepted_quantity,l.rejected_quantity,l.unit_cost,"
                + "coalesce((select sum(m.quantity) from ap_match m join ap_invoice_line il on il.id=m.invoice_line_id "
                + "join ap_invoice i on i.id=il.invoice_id where m.receipt_line_id=l.id and i.status<>'CANCELLED'),0) as matched "
                + "from purchase_receipt_line l join purchase_receipt r on r.id=l.receipt_id "
                + "join purchase_order o on o.id=r.order_id join purchase_order_line ol on ol.id=l.order_line_id "
                + "where l.accepted_quantity>0 and l.stock_receipt_id is not null) a";
        Map<String, Object> parameters = WholesaleDao.params("text", search.getLikeText());
        String where = " where accepted_quantity>matched and (receipt_number like :text escape '!' "
                + "or order_number like :text escape '!' or product_code like :text escape '!')";
        if (search.getSupplierId() != null) { where += " and supplier_id=:supplier"; parameters.put("supplier", search.getSupplierId()); }
        if (search.getFrom() != null) { where += " and receipt_date>=:from"; parameters.put("from", search.getFrom()); }
        if (search.getTo() != null) { where += " and receipt_date<=:to"; parameters.put("to", search.getTo()); }
        long total = countSql(source + where, parameters);
        List<Object[]> rows = rows("select id,receipt_number,order_number,product_id,product_code,product_name,receipt_date,"
                + "accepted_quantity,rejected_quantity,matched,unit_cost from " + source + where + " order by receipt_date,id",
                parameters, search);
        List<APReceiptAvailability> result = new ArrayList<APReceiptAvailability>();
        for (Object[] row : rows) {
            result.add(new APReceiptAvailability(id(row[0]), (String) row[1], (String) row[2], id(row[3]),
                    (String) row[4], (String) row[5], (Date) row[6], ((Number) row[7]).intValue(),
                    ((Number) row[8]).intValue(), ((Number) row[9]).longValue(), (BigDecimal) row[10]));
        }
        return new Page<APReceiptAvailability>(result, total, search.getPage(), search.getSize());
    }

    public Page<APOpenItem> searchOpenItems(APSearch search, Date asOf, boolean overdueOnly, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        requireSearch(search);
        Date date = APLedger.pastDate(asOf, "基準日");
        String source = "(select i.id,i.number,i.supplier_invoice_number,i.supplier_id,i.supplier_name,"
                + "i.invoice_date,i.due_date,i.total_amount,"
                + "coalesce((select sum(l.amount) from ap_payment_line l join ap_payment_voucher v on v.id=l.voucher_id "
                + "where l.invoice_id=i.id and v.payment_date<=:date and (l.reversal_date is null or l.reversal_date>:date)),0) as paid,"
                + "coalesce((select sum(c.total_amount) from ap_credit c where c.invoice_id=i.id "
                + "and c.status='POSTED' and c.posted_date<=:date),0) as credited "
                + "from ap_invoice i where i.status='POSTED' and i.posted_date<=:date) a";
        Map<String, Object> parameters = WholesaleDao.params("date", date, "text", search.getLikeText());
        String where = " where total_amount>paid+credited and (number like :text escape '!' "
                + "or supplier_invoice_number like :text escape '!' or supplier_name like :text escape '!')";
        if (search.getSupplierId() != null) { where += " and supplier_id=:supplier"; parameters.put("supplier", search.getSupplierId()); }
        if (overdueOnly) { where += " and due_date<:date"; }
        if (search.getFrom() != null) { where += " and due_date>=:from"; parameters.put("from", search.getFrom()); }
        if (search.getTo() != null) { where += " and due_date<=:to"; parameters.put("to", search.getTo()); }
        long total = countSql(source + where, parameters);
        List<Object[]> rows = rows("select id,number,supplier_invoice_number,supplier_name,invoice_date,due_date,total_amount,paid,credited "
                + "from " + source + where + " order by due_date,id", parameters, search);
        List<APOpenItem> result = new ArrayList<APOpenItem>();
        for (Object[] row : rows) {
            result.add(new APOpenItem(id(row[0]), (String) row[1], (String) row[2], (String) row[3],
                    (Date) row[4], (Date) row[5], (BigDecimal) row[6], (BigDecimal) row[7], (BigDecimal) row[8], date));
        }
        return new Page<APOpenItem>(result, total, search.getPage(), search.getSize());
    }

    public Page<APInvoice> searchVarianceQueue(APSearch search, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        requireSearch(search);
        Map<String, Object> parameters = WholesaleDao.params("text", search.getLikeText());
        String where = " where i.status='DRAFT' and i.varianceStatus='REQUIRED' "
                + "and (i.number like :text escape '!' or i.supplierName like :text escape '!')";
        if (search.getSupplierId() != null) { where += " and i.supplier.id=:supplier"; parameters.put("supplier", search.getSupplierId()); }
        if (search.getFrom() != null) { where += " and i.invoiceDate>=:from"; parameters.put("from", search.getFrom()); }
        if (search.getTo() != null) { where += " and i.invoiceDate<=:to"; parameters.put("to", search.getTo()); }
        Page<APInvoice> result = dao.page("from APInvoice i" + where + " order by i.dueDate,i.id",
                "select count(i.id) from APInvoice i" + where, parameters, search);
        for (APInvoice invoice : result.getItems()) { new APLedger(dao).detail(invoice); }
        return result;
    }

    public APStatement getStatement(Long supplierId, Date from, Date to, int page, int size, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        Supplier supplier = dao.get(Supplier.class, supplierId);
        Date start = Checks.date(from, "開始日");
        Date end = APLedger.pastDate(to, "終了日");
        Checks.state(!start.after(end), "ap.statementRange", "開始日は終了日以前にしてください。");
        APSearch pagination = new APSearch();
        pagination.setPage(page);
        pagination.setSize(size);
        // Every document contributes its actual event date, not today's mutable balance projection.
        String events = "(select posted_date as event_date,'INVOICE' as event_type,id,number,"
                + "supplier_invoice_number as reference,total_amount as charge,cast(0 as numeric) as reduction "
                + "from ap_invoice where supplier_id=:supplier and status='POSTED' "
                + "union all select posted_date,'CREDIT',id,number,supplier_credit_number,cast(0 as numeric),total_amount "
                + "from ap_credit where supplier_id=:supplier and status='POSTED' "
                + "union all select payment_date,'PAYMENT',id,number,reference,cast(0 as numeric),amount "
                + "from ap_payment_voucher where supplier_id=:supplier "
                + "union all select cancellation_date,'PAYMENT_CANCEL',id,number,reference,amount,cast(0 as numeric) "
                + "from ap_payment_voucher where supplier_id=:supplier and status='CANCELLED') e";
        Map<String, Object> parameters = WholesaleDao.params("supplier", supplierId, "start", start);
        parameters.put("end", end);
        Object[] totals = (Object[]) sql("select coalesce(sum(case when event_date<:start then charge-reduction else 0 end),0) as opening_balance,"
                + "coalesce(sum(case when event_date>=:start then charge else 0 end),0) as period_charges,"
                + "coalesce(sum(case when event_date>=:start then reduction else 0 end),0) as period_reductions,"
                + "count(case when event_date>=:start then 1 end) as entry_count from " + events + " where event_date<=:end", parameters).uniqueResult();
        BigDecimal opening = (BigDecimal) totals[0];
        long total = ((Number) totals[3]).longValue();
        String statementRows = "select event_date,event_type,id,number,reference,charge,reduction,"
                + "sum(charge-reduction) over(order by event_date,id,event_type rows between unbounded preceding and current row) as movement "
                + "from " + events + " where event_date between :start and :end order by event_date,id,event_type";
        List<APStatementEntry> entries = new ArrayList<APStatementEntry>();
        for (Object[] row : rows(statementRows, parameters, pagination)) {
            entries.add(new APStatementEntry((Date) row[0], (String) row[1], id(row[2]), (String) row[3],
                    (String) row[4], (BigDecimal) row[5], (BigDecimal) row[6], opening.add((BigDecimal) row[7])));
        }
        return new APStatement(supplier, start, end, opening, (BigDecimal) totals[1], (BigDecimal) totals[2],
                new Page<APStatementEntry>(entries, total, page, size));
    }

    private void requireSearch(APSearch search) {
        Checks.state(search != null, "ap.search", "検索条件を指定してください。");
        Checks.state(search.getFrom() == null || search.getTo() == null || !search.getFrom().after(search.getTo()),
                "ap.searchRange", "検索開始日は終了日以前です。");
    }

    private Long id(Object value) { return ((Number) value).longValue(); }

    private SQLQuery sql(String query, Map<String, Object> parameters) {
        SQLQuery result = dao.session().createSQLQuery(query);
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) { result.setParameter(parameter.getKey(), parameter.getValue()); }
        return result;
    }

    private long countSql(String from, Map<String, Object> parameters) {
        return ((Number) sql("select count(*) from " + from, parameters).uniqueResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String query, Map<String, Object> parameters, APSearch search) {
        return (List<Object[]>) sql(query, parameters).setFirstResult(search.getOffset()).setMaxResults(search.getSize()).list();
    }
}
