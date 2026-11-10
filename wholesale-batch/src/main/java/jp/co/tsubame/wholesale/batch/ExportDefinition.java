package jp.co.tsubame.wholesale.batch;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Money;

/** Column/query metadata shared by CLI validation and the bounded export query path. */
public final class ExportDefinition {
    private static final String ORDER_STATUSES =
            "DRAFT,SUBMITTED,REJECTED,APPROVED,PART_ALLOCATED,ALLOCATED,PART_SHIPPED,SHIPPED,CANCELLED,CLOSED_PARTIAL";
    private static final Map<String, ExportDefinition> DEFINITIONS = definitions();
    private final List<String> headers;
    private final String select;
    private final String id;
    private final String date;
    private final String status;
    private final Set<String> statuses;
    private final String calculation;

    private ExportDefinition(String headers, String select, String id, String date, String status,
                             String statuses, String calculation) {
        this.headers = Collections.unmodifiableList(Arrays.asList(headers.split(",")));
        this.select = select;
        this.id = id;
        this.date = date;
        this.status = status;
        this.statuses = statuses == null ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(statuses.split(","))));
        this.calculation = calculation;
    }

    private static Map<String, ExportDefinition> definitions() {
        Map<String, ExportDefinition> result = new LinkedHashMap<String, ExportDefinition>();
        result.put("export-stock", new ExportDefinition(
                "id,warehouse_code,product_code,product_name,on_hand,reserved,blocked,available",
                "select b.id,w.code,p.code,p.name,b.onHand,b.reserved,b.blocked "
                + "from StockBalance b join b.warehouse w join b.product p",
                "b.id", null, null, null, "stock"));
        result.put("export-orders", new ExportDefinition(
                "id,number,customer_code,customer_name,warehouse_code,order_date,requested_date,status,"
                + "net_amount,tax_amount,external_reference,notes,total_amount",
                "select o.id,o.number,c.code,o.customerName,w.code,o.orderDate,o.requestedDate,"
                + "o.status,o.netAmount,o.taxAmount,o.externalReference,o.notes "
                + "from SalesOrder o join o.customer c join o.warehouse w",
                "o.id", "o.orderDate", "o.status", ORDER_STATUSES, "orders"));
        result.put("export-invoices", new ExportDefinition(
                "id,number,customer_code,customer_name,status,period_end,issued_date,due_date,net_amount,"
                + "tax_amount,total_amount,paid_amount,credited_amount,outstanding_amount",
                "select i.id,i.number,c.code,i.customerName,i.status,i.periodEnd,i.issuedDate,i.dueDate,"
                + "i.netAmount,i.taxAmount,i.totalAmount,i.paidAmount,i.creditedAmount from Invoice i join i.customer c",
                "i.id", "i.periodEnd", "i.status", "DRAFT,FINALIZED,VOID", "invoices"));
        result.put("export-receipts", new ExportDefinition(
                "id,number,request_key,receipt_date,warehouse_code,product_code,product_name,quantity,"
                + "unit_cost,reference,note,created_by,extended_cost",
                "select r.id,r.number,r.requestKey,r.receiptDate,w.code,p.code,p.name,r.quantity,"
                + "r.unitCost,r.reference,r.note,r.createdBy from StockReceipt r join r.warehouse w join r.product p",
                "r.id", "r.receiptDate", null, null, "receipts"));
        result.put("export-order-lines", new ExportDefinition(
                "id,order_id,order_number,customer_code,customer_name,warehouse_code,status,order_date,requested_date,"
                + "external_reference,delivery_address,notes,line_number,product_code,product_name,unit,pack_size,"
                + "quantity,allocated_quantity,shipped_quantity,cancelled_quantity,unit_price,tax_rate,net_amount",
                "select l.id,o.id,o.number,c.code,o.customerName,w.code,o.status,o.orderDate,o.requestedDate,"
                + "o.externalReference,o.deliveryAddress,o.notes,l.lineNumber,l.productCode,l.productName,l.unit,l.packSize,"
                + "l.quantity,l.allocatedQuantity,l.shippedQuantity,l.cancelledQuantity,l.unitPrice,l.taxRate "
                + "from SalesOrderLine l join l.order o join o.customer c join o.warehouse w",
                "l.id", "o.orderDate", "o.status", ORDER_STATUSES, "order-lines"));
        result.put("export-shipments", new ExportDefinition(
                "id,shipment_id,shipment_number,status,planned_date,shipped_date,customer_code,customer_name,"
                + "order_id,order_number,warehouse_code,line_number,product_code,product_name,unit,quantity,"
                + "returned_quantity,unit_price,tax_rate,invoice_id,invoice_number,carrier,tracking_number,note,net_amount",
                "select l.id,s.id,s.number,s.status,s.plannedDate,s.shippedDate,c.code,o.customerName,"
                + "o.id,o.number,w.code,l.lineNumber,l.productCode,l.productName,l.unit,l.quantity,"
                + "l.returnedQuantity,l.unitPrice,l.taxRate,i.id,i.number,s.carrier,s.trackingNumber,s.note "
                + "from ShipmentLine l join l.shipment s join s.order o join o.customer c join o.warehouse w "
                + "left join s.invoice i",
                "l.id", "coalesce(s.shippedDate,s.plannedDate)", "s.status", "INSTRUCTED,CONFIRMED,CANCELLED", "shipments"));
        return Collections.unmodifiableMap(result);
    }

    public static Set<String> names() { return DEFINITIONS.keySet(); }
    public static ExportDefinition get(String command) {
        ExportDefinition definition = DEFINITIONS.get(command);
        if (definition == null) { throw new IllegalArgumentException("Unknown export command"); }
        return definition;
    }
    public List<String> getHeaders() { return headers; }
    public boolean supportsDate() { return date != null; }
    public Set<String> getStatuses() { return statuses; }

    public String query(Date from, Date to, String requestedStatus, Map<String, Object> parameters) {
        Checks.state((from == null && to == null) || supportsDate(), "export.date", "This export has no date filter");
        Checks.state(from == null || to == null || !from.after(to), "export.date", "from must not be after to");
        Checks.state(requestedStatus == null || statuses.contains(requestedStatus), "export.status", "Invalid export status");
        StringBuilder hql = new StringBuilder(select).append(" where ").append(id).append(">:after");
        if (from != null) { hql.append(" and ").append(date).append(">=:from"); parameters.put("from", Dates.day(from)); }
        if (to != null) { hql.append(" and ").append(date).append("<=:to"); parameters.put("to", Dates.day(to)); }
        if (requestedStatus != null) {
            hql.append(" and ").append(status).append("=:status");
            parameters.put("status", requestedStatus);
        }
        return hql.append(" order by ").append(id).toString();
    }

    public void complete(List<Object> row) {
        if ("stock".equals(calculation)) {
            row.add(((Boolean) row.get(6)).booleanValue() ? 0
                    : ((Number) row.get(4)).intValue() - ((Number) row.get(5)).intValue());
        } else if ("orders".equals(calculation)) {
            row.add(decimal(row, 8).add(decimal(row, 9)));
        } else if ("invoices".equals(calculation)) {
            row.add(decimal(row, 10).subtract(decimal(row, 11)).subtract(decimal(row, 12)).max(BigDecimal.ZERO));
        } else if ("receipts".equals(calculation)) {
            row.add(Money.amount(decimal(row, 8), ((Number) row.get(7)).intValue()));
        } else if ("order-lines".equals(calculation)) {
            row.add(Money.amount(decimal(row, 21), ((Number) row.get(17)).intValue()));
        } else if ("shipments".equals(calculation)) {
            row.add(Money.amount(decimal(row, 17), ((Number) row.get(15)).intValue()));
        }
        for (int i = 0; i < row.size(); i++) {
            if (row.get(i) instanceof Date) { row.set(i, Dates.format((Date) row.get(i))); }
        }
    }

    private static BigDecimal decimal(List<Object> row, int index) { return (BigDecimal) row.get(index); }
}
