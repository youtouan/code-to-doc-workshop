package jp.co.tsubame.wholesale.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import org.hibernate.Hibernate;

public final class OrderLocks {
    private OrderLocks() {
    }

    public static SalesOrder lock(WholesaleDao dao, Long id) {
        SalesOrder snapshot = dao.get(SalesOrder.class, id);
        Hibernate.initialize(snapshot.getLines());
        List<Long> products = new ArrayList<Long>();
        for (SalesOrderLine line : snapshot.getLines()) {
            products.add(line.getProduct().getId());
        }
        dao.lockReferences(Collections.singleton(snapshot.getWarehouse().getId()), products);
        dao.lock(Customer.class, snapshot.getCustomer().getId());
        SalesOrder order = dao.lock(SalesOrder.class, id);
        Hibernate.initialize(order.getLines());
        return order;
    }
}
