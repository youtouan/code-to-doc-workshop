package jp.co.tsubame.wholesale.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.DashboardSnapshot;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.WorkItem;
import jp.co.tsubame.wholesale.dao.WholesaleDao;

public class DashboardService extends BaseService {
    public DashboardSnapshot getDashboard(Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        DashboardSnapshot result = new DashboardSnapshot();
        Map<String, Object> today = WholesaleDao.params("today", Dates.today());
        Map<String, Object> none = Collections.emptyMap();
        result.addCount("myDrafts", dao.count("select count(o.id) from SalesOrder o "
                + "where o.status='DRAFT' and o.createdBy=:actor", WholesaleDao.params("actor", actor.getLogin())));
        result.addCount("awaitingApproval", dao.count("select count(o.id) from SalesOrder o where o.status='SUBMITTED'", none));
        result.addCount("lateOrders", dao.count("select count(o.id) from SalesOrder o "
                + "where o.status in ('APPROVED','PART_ALLOCATED','ALLOCATED','PART_SHIPPED') "
                + "and o.requestedDate<:today", today));
        result.addCount("shortOrders", dao.count("select count(distinct o.id) from SalesOrder o join o.lines l "
                + "where o.status in ('APPROVED','PART_ALLOCATED','PART_SHIPPED') "
                + "and l.quantity>l.shippedQuantity+l.cancelledQuantity+l.allocatedQuantity", none));
        result.addCount("shipmentsDue", dao.count("select count(s.id) from Shipment s "
                + "where s.status='INSTRUCTED' and s.plannedDate<=:today", today));
        result.addCount("unbilledShipments", dao.count("select count(s.id) from Shipment s "
                + "where s.status='CONFIRMED' and s.invoice is null", none));
        result.addCount("stockBelowReorder", dao.count("select count(b.id) from StockBalance b "
                + "where b.product.active=true and b.warehouse.active=true "
                + "and b.onHand-b.reserved<b.product.reorderPoint", none));
        result.addCount("blockedStock", dao.count("select count(b.id) from StockBalance b where b.blocked=true", none));
        result.addCount("receiptsToday", dao.count("select count(r.id) from StockReceipt r where r.receiptDate=:today", today));
        result.addCount("returnsPending", dao.count("select count(r.id) from SalesReturn r "
                + "where r.status in ('REQUESTED','APPROVED')", none));
        List<?> orders = dao.query("select new jp.co.tsubame.wholesale.common.WorkItem("
                + "'ORDER',o.id,o.number,o.customerName,o.requestedDate,o.status) from SalesOrder o "
                + "where o.status in ('SUBMITTED','APPROVED','PART_ALLOCATED','ALLOCATED','PART_SHIPPED') "
                + "order by o.requestedDate,o.id", none).setMaxResults(12).list();
        for (Object item : orders) {
            result.addOrder(WorkItem.class.cast(item));
        }
        List<?> shipments = dao.query("select new jp.co.tsubame.wholesale.common.WorkItem("
                + "'SHIPMENT',s.id,s.number,s.order.customerName,s.plannedDate,s.status) from Shipment s "
                + "where s.status='INSTRUCTED' order by s.plannedDate,s.id", none).setMaxResults(12).list();
        for (Object item : shipments) {
            result.addShipment(WorkItem.class.cast(item));
        }
        return result;
    }
}
