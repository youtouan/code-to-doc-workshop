package jp.co.tsubame.wholesale.dao;

import java.util.List;
import java.util.Collections;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.OrderAmendment;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import org.hibernate.Hibernate;

public final class OrderAmendmentLocks {
    private OrderAmendmentLocks() { }
    public static OrderAmendment lock(WholesaleDao dao, Long id) {
        Checks.state(id != null, "validation.id", "変更申請を指定してください。");
        List<Long> orders = dao.list("select a.order.id from OrderAmendment a where a.id=:id",
                WholesaleDao.params("id", id));
        Checks.state(!orders.isEmpty(), "notFound", "受注変更申請が見つかりません。");
        lockSource(dao, orders.get(0));
        OrderAmendment amendment = dao.lock(OrderAmendment.class, id);
        Checks.state(amendment.getOrder().getId().equals(orders.get(0)),
                "concurrent.update", "変更対象受注が一致しません。");
        return initialize(amendment);
    }

    public static SalesOrder lockSource(WholesaleDao dao, Long id) {
        Checks.state(id != null, "validation.id", "受注を指定してください。");
        List<Object[]> headers = dao.list("select o.customer.id,o.warehouse.id from SalesOrder o where o.id=:id",
                WholesaleDao.params("id", id));
        Checks.state(!headers.isEmpty(), "notFound", "受注が見つかりません。");
        Object[] keys = headers.get(0);
        List<Long> products = dao.list("select l.product.id from SalesOrderLine l where l.order.id=:id",
                WholesaleDao.params("id", id));
        dao.lockReferences(Collections.singleton((Long) keys[1]), products);
        dao.lock(Customer.class, (Long) keys[0]);
        SalesOrder order = dao.lock(SalesOrder.class, id);
        Checks.state(order.getCustomer().getId().equals(keys[0]) && order.getWarehouse().getId().equals(keys[1]),
                "concurrent.update", "受注参照先が更新されました。再表示してください。");
        Hibernate.initialize(order.getLines());
        return order;
    }
    public static OrderAmendment initialize(OrderAmendment amendment) {
        Hibernate.initialize(amendment.getLines());
        Hibernate.initialize(amendment.getOrder().getLines());
        return amendment;
    }
}
