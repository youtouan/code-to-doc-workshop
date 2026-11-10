package jp.co.tsubame.wholesale.dao;

import java.util.ArrayList;
import java.util.List;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.QuotationCommand;
import jp.co.tsubame.wholesale.common.QuotationLineCommand;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Quotation;
import jp.co.tsubame.wholesale.entity.QuotationRevision;
import org.hibernate.Hibernate;

public final class QuotationLocks {
    private QuotationLocks() { }

    public static Quotation lock(WholesaleDao dao, Long id) {
        return lock(dao, id, null);
    }

    public static Quotation lock(WholesaleDao dao, Long id, QuotationCommand input) {
        List<Long> warehouses = new ArrayList<Long>();
        List<Long> products = new ArrayList<Long>();
        Object[] keys = null;
        Long customerId = input == null ? null : input.getCustomerId();
        if (id != null) {
            List<Object[]> found = dao.list("select q.customer.id,q.warehouse.id,q.revisionNumber"
                    + " from Quotation q where q.id=:id", WholesaleDao.params("id", id));
            Checks.state(!found.isEmpty(), "notFound", "見積が見つかりません。");
            keys = found.get(0);
            customerId = (Long) keys[0];
            warehouses.add((Long) keys[1]);
            products.addAll(dao.<Long>list("select l.product.id from QuotationLine l"
                    + " where l.revision.quotation.id=:id and l.revision.revisionNumber=:revision",
                    WholesaleDao.params("id", id, "revision", keys[2])));
        } else {
            Checks.state(input != null, "validation.id", "見積を指定してください。");
        }
        if (input != null) {
            Checks.state(customerId.equals(input.getCustomerId()), "quotation.customerChange",
                    "見積の得意先は変更できません。別の見積を作成してください。");
            warehouses.add(input.getWarehouseId());
            for (QuotationLineCommand line : input.getLines()) { products.add(line.getProductId()); }
        }
        dao.lockReferences(warehouses, products);
        dao.lock(Customer.class, customerId);
        if (id == null) { return null; }
        Quotation quote = dao.lock(Quotation.class, id);
        Checks.state(quote.getCustomer().getId().equals(keys[0]) && quote.getWarehouse().getId().equals(keys[1])
                && quote.getRevisionNumber() == ((Number) keys[2]).intValue(),
                "concurrent.update", "見積参照先が変更されました。再表示してください。");
        return initialize(quote);
    }

    public static Quotation initialize(Quotation quote) {
        Hibernate.initialize(quote.getRevisions());
        for (QuotationRevision revision : quote.getRevisions()) { Hibernate.initialize(revision.getLines()); }
        return quote;
    }
}
