package jp.co.tsubame.wholesale.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.DispatchManifest;
import jp.co.tsubame.wholesale.entity.DispatchStop;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.Shipment;
import org.hibernate.Hibernate;

public class DispatchLocks {
    private WholesaleDao dao;
    public void setDao(WholesaleDao dao) { this.dao = dao; }

    public Map<Long, Shipment> sources(Collection<Long> shipmentIds, Collection<Long> additionalWarehouses) {
        Checks.state(shipmentIds != null && additionalWarehouses != null, "validation.id", "出荷と倉庫を指定してください。");
        for (Long id : shipmentIds) { Checks.state(id != null && id > 0, "validation.id", "出荷IDが不正です。"); }
        for (Long id : additionalWarehouses) { Checks.state(id != null && id > 0, "validation.id", "倉庫IDが不正です。"); }
        Set<Long> ids = new TreeSet<Long>(shipmentIds);
        Checks.state(ids.size() <= 200, "dispatch.stopLimit", "編集前後の対象出荷が多すぎます。");
        Set<Long> warehouses = new TreeSet<Long>(additionalWarehouses);
        Set<Long> customers = new TreeSet<Long>();
        Set<Long> orders = new TreeSet<Long>();
        List<Object[]> snapshots = ids.isEmpty() ? Collections.<Object[]>emptyList()
                : dao.<Object[]>list("select s.id,s.order.id,s.order.customer.id,s.order.warehouse.id,s.version,s.order.version "
                + "from Shipment s where s.id in (:ids)", WholesaleDao.params("ids", ids));
        Checks.state(snapshots.size() == ids.size(), "notFound", "対象出荷が見つかりません。");
        for (Object[] snapshot : snapshots) {
            orders.add((Long) snapshot[1]);
            customers.add((Long) snapshot[2]);
            warehouses.add((Long) snapshot[3]);
        }
        List<Long> products = orders.isEmpty() ? Collections.<Long>emptyList()
                : dao.<Long>list("select distinct l.product.id from SalesOrderLine l where l.order.id in (:orders)",
                WholesaleDao.params("orders", orders));
        dao.lockReferences(warehouses, products);
        for (Long id : customers) { dao.lock(Customer.class, id); }
        for (Long id : orders) { Hibernate.initialize(dao.lock(SalesOrder.class, id).getLines()); }
        Map<Long, Shipment> result = new LinkedHashMap<Long, Shipment>();
        for (Long id : ids) { result.put(id, initialize(dao.lock(Shipment.class, id))); }
        for (Object[] snapshot : snapshots) {
            Shipment shipment = result.get((Long) snapshot[0]);
            Checks.version(shipment.getVersion(), ((Number) snapshot[4]).intValue());
            Checks.version(shipment.getOrder().getVersion(), ((Number) snapshot[5]).intValue());
            Checks.state(shipment.getOrder().getId().equals(snapshot[1])
                    && shipment.getOrder().getCustomer().getId().equals(snapshot[2])
                    && shipment.getWarehouse().getId().equals(snapshot[3]),
                    "concurrent.update", "出荷の参照先が変更されました。再表示してください。");
        }
        return result;
    }

    public DispatchManifest manifest(Long id, int expectedVersion, Collection<Long> addedShipments, Long newWarehouseId) {
        Checks.state(id != null, "validation.id", "配送表を指定してください。");
        Object[] snapshot = (Object[]) dao.query("select m.version,m.warehouse.id from DispatchManifest m where m.id=:id",
                WholesaleDao.params("id", id)).uniqueResult();
        Checks.state(snapshot != null, "notFound", "配送表が見つかりません。");
        List<Long> shipments = new ArrayList<Long>(addedShipments);
        shipments.addAll(dao.<Long>list("select s.shipment.id from DispatchStop s where s.manifest.id=:id",
                WholesaleDao.params("id", id)));
        List<Long> warehouses = new ArrayList<Long>();
        warehouses.add((Long) snapshot[1]);
        if (newWarehouseId != null) { warehouses.add(newWarehouseId); }
        sources(shipments, warehouses);
        DispatchManifest manifest = dao.lock(DispatchManifest.class, id);
        Checks.version(manifest.getVersion(), ((Number) snapshot[0]).intValue());
        Checks.version(manifest.getVersion(), expectedVersion);
        return initialize(manifest);
    }

    public static Shipment initialize(Shipment shipment) {
        Hibernate.initialize(shipment.getLines());
        Hibernate.initialize(shipment.getOrder().getLines());
        return shipment;
    }

    public static DispatchManifest initialize(DispatchManifest manifest) {
        Hibernate.initialize(manifest.getStops());
        for (DispatchStop stop : manifest.getStops()) { initialize(stop.getShipment()); }
        return manifest;
    }
}
