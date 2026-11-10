package jp.co.tsubame.wholesale.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.CatalogRules;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.PriceAgreement;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Warehouse;

public class CatalogService extends BaseService {
    public Customer getCustomer(Long id, Actor actor) {
        read(actor);
        return dao.get(Customer.class, id);
    }

    public Product getProduct(Long id, Actor actor) {
        read(actor);
        return dao.get(Product.class, id);
    }

    public Warehouse getWarehouse(Long id, Actor actor) {
        read(actor);
        return dao.get(Warehouse.class, id);
    }

    public Page<Customer> searchCustomers(Search search, Actor actor) {
        read(actor);
        return search("Customer", search, true);
    }

    public Page<Product> searchProducts(Search search, Actor actor) {
        read(actor);
        return search("Product", search, false);
    }

    public Page<Warehouse> searchWarehouses(Search search, Actor actor) {
        read(actor);
        return search("Warehouse", search, false);
    }

    public List<Customer> listActiveCustomers(Actor actor) {
        read(actor);
        return dao.list("from Customer c where c.active = true order by c.code");
    }

    public List<Product> listActiveProducts(Actor actor) {
        read(actor);
        return dao.list("from Product p where p.active = true order by p.code");
    }

    public List<Warehouse> listActiveWarehouses(Actor actor) {
        read(actor);
        return dao.list("from Warehouse w where w.active = true order by w.code");
    }

    public Customer saveCustomer(Customer input, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        CatalogRules.customer(input);
        Customer target = input.getId() == null ? new Customer() : dao.lock(Customer.class, input.getId());
        Checks.version(target.getVersion(), expectedVersion);
        uniqueCode("Customer", input.getCode().trim(), target.getId());
        target.setCode(input.getCode().trim());
        target.setName(input.getName().trim());
        target.setActive(input.isActive());
        target.setOnHold(input.isOnHold());
        target.setCreditLimit(Checks.money(input.getCreditLimit(), "与信限度額", true));
        target.setClosingDay(input.getClosingDay());
        target.setPaymentTermDays(input.getPaymentTermDays());
        target.setTaxRounding(input.getTaxRounding());
        target.setPostalCode(Checks.optionalText(input.getPostalCode(), "郵便番号", 12));
        target.setAddress(Checks.optionalText(input.getAddress(), "住所", 250));
        target.setTelephone(Checks.optionalText(input.getTelephone(), "電話番号", 30));
        target.setNotes(Checks.optionalText(input.getNotes(), "備考", 1000));
        dao.save(target);
        audit(actor, "CATALOG_CUSTOMER_SAVE", target, target.getCode());
        dao.flush();
        return target;
    }

    public Product saveProduct(Product input, int expectedVersion, Actor actor) {
        require(actor, "MANAGER", "BATCH");
        CatalogRules.product(input);
        Product target = input.getId() == null ? new Product() : dao.lock(Product.class, input.getId());
        Checks.version(target.getVersion(), expectedVersion);
        if (target.getId() != null && !input.isActive()) {
            assertProductCanDeactivate(target.getId());
        }
        uniqueCode("Product", input.getCode().trim(), target.getId());
        target.setCode(input.getCode().trim());
        target.setName(input.getName().trim());
        target.setUnit(input.getUnit().trim());
        target.setTaxCategory(input.getTaxCategory());
        target.setListPrice(Checks.money(input.getListPrice(), "標準売価", true));
        target.setStandardCost(Checks.money(input.getStandardCost(), "標準原価", true));
        target.setPackSize(input.getPackSize());
        target.setReorderPoint(input.getReorderPoint());
        target.setReorderQuantity(input.getReorderQuantity());
        target.setActive(input.isActive());
        target.setNotes(Checks.optionalText(input.getNotes(), "備考", 1000));
        dao.save(target);
        audit(actor, "CATALOG_PRODUCT_SAVE", target, target.getCode());
        dao.flush();
        return target;
    }

    public Warehouse saveWarehouse(Warehouse input, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        CatalogRules.warehouse(input);
        Warehouse target = input.getId() == null ? new Warehouse() : dao.lock(Warehouse.class, input.getId());
        Checks.version(target.getVersion(), expectedVersion);
        if (target.getId() != null && !input.isActive()) {
            assertWarehouseCanDeactivate(target.getId());
        }
        uniqueCode("Warehouse", input.getCode().trim(), target.getId());
        target.setCode(input.getCode().trim());
        target.setName(input.getName().trim());
        target.setAddress(Checks.optionalText(input.getAddress(), "住所", 250));
        target.setActive(input.isActive());
        dao.save(target);
        audit(actor, "CATALOG_WAREHOUSE_SAVE", target, target.getCode());
        dao.flush();
        return target;
    }

    public PriceAgreement savePriceAgreement(PriceAgreement input, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        Checks.state(input != null && input.getCustomer() != null && input.getProduct() != null,
                "validation.agreement", "得意先と商品を指定してください。");
        CatalogRules.interval(input.getValidFrom(), input.getValidTo());
        Checks.quantity(input.getMinimumQuantity(), "最低数量");
        BigDecimal amount = Checks.money(input.getUnitPrice(), "契約単価", true);
        dao.lockReferences(Collections.<Long>emptyList(), Collections.singleton(input.getProduct().getId()));
        Customer customer = dao.lock(Customer.class, input.getCustomer().getId());
        Product product = dao.get(Product.class, input.getProduct().getId());
        PriceAgreement target = input.getId() == null ? new PriceAgreement() : dao.get(PriceAgreement.class, input.getId());
        Checks.version(target.getVersion(), expectedVersion);
        Checks.state(target.getId() == null || target.getCustomer().getId().equals(customer.getId()),
                "agreement.customerImmutable", "契約の得意先は変更できません。");
        Map<String, Object> parameters = WholesaleDao.params("customer", customer, "product", product);
        parameters.put("tier", Integer.valueOf(input.getMinimumQuantity()));
        List<PriceAgreement> peers = dao.list("from PriceAgreement p where p.customer = :customer"
                + " and p.product = :product and p.minimumQuantity = :tier", parameters);
        for (PriceAgreement peer : peers) {
            Checks.state(peer.getId().equals(target.getId())
                    || !CatalogRules.overlaps(input.getValidFrom(), input.getValidTo(),
                            peer.getValidFrom(), peer.getValidTo()),
                    "agreement.overlap", "同一得意先・商品・最低数量の契約期間が重複しています。");
        }
        target.setCustomer(customer);
        target.setProduct(product);
        target.setValidFrom(Dates.day(input.getValidFrom()));
        target.setValidTo(Dates.day(input.getValidTo()));
        target.setMinimumQuantity(input.getMinimumQuantity());
        target.setUnitPrice(amount);
        target.setNotes(Checks.optionalText(input.getNotes(), "備考", 1000));
        dao.save(target);
        audit(actor, "CATALOG_PRICE_SAVE", target, customer.getCode() + "/" + product.getCode());
        dao.flush();
        return target;
    }

    public List<PriceAgreement> listPriceAgreements(Long customerId, Actor actor) {
        read(actor);
        Customer customer = dao.get(Customer.class, customerId);
        return dao.list("from PriceAgreement p where p.customer = :customer"
                + " order by p.product.code, p.minimumQuantity, p.validFrom desc",
                WholesaleDao.params("customer", customer));
    }

    public void deletePriceAgreement(Long id, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        PriceAgreement reference = dao.get(PriceAgreement.class, id);
        dao.lock(Customer.class, reference.getCustomer().getId());
        // A fresh row lock observes concurrent contract edits made before the customer lock was acquired.
        PriceAgreement target = dao.lock(PriceAgreement.class, id);
        dao.session().refresh(target);
        Checks.version(target.getVersion(), expectedVersion);
        audit(actor, "CATALOG_PRICE_DELETE", target,
                target.getCustomer().getCode() + "/" + target.getProduct().getCode());
        dao.delete(target);
        dao.flush();
    }

    public BigDecimal price(Long customerId, Long productId, int quantity, Date onDate, Actor actor) {
        read(actor);
        Checks.quantity(quantity, "数量");
        Date day = Checks.date(onDate, "価格基準日");
        Customer customer = dao.get(Customer.class, customerId);
        Product product = dao.get(Product.class, productId);
        Map<String, Object> parameters = WholesaleDao.params("customer", customer, "product", product);
        parameters.put("quantity", Integer.valueOf(quantity));
        parameters.put("day", day);
        List<PriceAgreement> contracts = dao.list("from PriceAgreement p where p.customer = :customer"
                + " and p.product = :product and p.minimumQuantity <= :quantity and p.validFrom <= :day"
                + " and (p.validTo is null or p.validTo >= :day)"
                + " order by p.minimumQuantity desc, p.validFrom desc, p.id desc", parameters);
        return contracts.isEmpty() ? product.getListPrice() : contracts.get(0).getUnitPrice();
    }

    private void read(Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "BILLING", "MANAGER", "BATCH");
    }

    private void assertProductCanDeactivate(Long id) {
        unused("select count(l.id) from StockTransferLine l where l.product.id = :id"
                + " and l.transfer.status in ('DRAFT','SUBMITTED','APPROVED','IN_TRANSIT','PART_RECEIVED')",
                id, "transfer", "未完了の倉庫移動に含まれる商品は停止できません。移動を完了または取消してください。");
        unused("select count(a.id) from StockAdjustment a where a.product.id = :id and a.status = 'PROPOSED'",
                id, "adjustment", "申請中の在庫調整に含まれる商品は停止できません。");
        unused("select count(l.id) from StockCountLine l where l.balance.product.id = :id"
                + " and (l.holding = true or l.stockCount.status in ('COUNTING','REVIEWED'))",
                id, "count", "棚卸中の商品は停止できません。棚卸を完了または取消してください。");
        unused("select count(b.id) from StockBalance b where b.product.id = :id"
                + " and (b.onHand <> 0 or b.reserved <> 0 or b.blocked = true)",
                id, "stock", "在庫・引当・凍結が残る商品は停止できません。");
        unused("select count(l.id) from PurchaseOrderLine l where l.product.id = :id"
                + " and l.order.status in ('DRAFT','SUBMITTED','APPROVED','REJECTED','PART_RECEIVED')",
                id, "purchasing", "未完了の発注に含まれる商品は停止できません。");
        unused("select count(l.id) from SalesOrderLine l where l.product.id = :id"
                + " and l.order.status in ('DRAFT','SUBMITTED','APPROVED','PART_ALLOCATED','ALLOCATED','PART_SHIPPED')",
                id, "sales", "未完了の受注に含まれる商品は停止できません。");
        unused("select count(l.id) from SalesReturnLine l where l.shipmentLine.orderLine.product.id = :id"
                + " and l.restock = true and l.salesReturn.status in ('REQUESTED','APPROVED')",
                id, "return", "在庫へ戻す返品が未入庫のため商品を停止できません。");
    }

    private void assertWarehouseCanDeactivate(Long id) {
        // Transit must be checked independently: a destination may have no balance row yet.
        unused("select count(t.id) from StockTransfer t where"
                + " (t.sourceWarehouse.id = :id or t.destinationWarehouse.id = :id)"
                + " and t.status in ('DRAFT','SUBMITTED','APPROVED','IN_TRANSIT','PART_RECEIVED')",
                id, "transfer", "未完了の倉庫移動の出庫元・入庫先倉庫は停止できません。");
        unused("select count(a.id) from StockAdjustment a where a.warehouse.id = :id and a.status = 'PROPOSED'",
                id, "adjustment", "申請中の在庫調整がある倉庫は停止できません。");
        unused("select count(c.id) from StockCount c where c.warehouse.id = :id"
                + " and c.status in ('COUNTING','REVIEWED')",
                id, "count", "棚卸中の倉庫は停止できません。");
        unused("select count(l.id) from StockCountLine l where l.stockCount.warehouse.id = :id and l.holding = true",
                id, "count", "棚卸による凍結が残る倉庫は停止できません。");
        unused("select count(b.id) from StockBalance b where b.warehouse.id = :id"
                + " and (b.onHand <> 0 or b.reserved <> 0 or b.blocked = true)",
                id, "stock", "在庫・引当・凍結が残る倉庫は停止できません。");
        unused("select count(o.id) from PurchaseOrder o where o.warehouse.id = :id"
                + " and o.status in ('DRAFT','SUBMITTED','APPROVED','REJECTED','PART_RECEIVED')",
                id, "purchasing", "未完了の発注の入荷倉庫は停止できません。");
        unused("select count(o.id) from SalesOrder o where o.warehouse.id = :id"
                + " and o.status in ('DRAFT','SUBMITTED','APPROVED','PART_ALLOCATED','ALLOCATED','PART_SHIPPED')",
                id, "sales", "未完了の受注の出荷倉庫は停止できません。");
        unused("select count(l.id) from SalesReturnLine l where l.salesReturn.shipment.order.warehouse.id = :id"
                + " and l.restock = true and l.salesReturn.status in ('REQUESTED','APPROVED')",
                id, "return", "在庫へ戻す返品が未入庫のため倉庫を停止できません。");
    }

    private void unused(String hql, Long id, String reason, String message) {
        Checks.state(dao.count(hql, WholesaleDao.params("id", id)) == 0, "catalog.inUse." + reason, message);
    }

    private void uniqueCode(String entity, String code, Long id) {
        dao.lockKey("catalog." + entity, code);
        Map<String, Object> parameters = WholesaleDao.params("code", code);
        String hql = "select count(e.id) from " + entity + " e where e.code = :code";
        if (id != null) {
            hql += " and e.id <> :id";
            parameters.put("id", id);
        }
        Checks.state(dao.count(hql, parameters) == 0, "catalog.duplicateCode", "コードが重複しています。");
    }

    private <T> Page<T> search(String entity, Search search, boolean holdSupported) {
        Checks.state(search != null, "validation.search", "検索条件を指定してください。");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        String where = " from " + entity + " e where 1 = 1";
        if (search.getText().length() > 0) {
            where += " and (lower(e.code) like :text escape '!' or lower(e.name) like :text escape '!')";
            parameters.put("text", search.getLikeText().toLowerCase(java.util.Locale.ROOT));
        }
        String status = search.getStatus();
        Checks.state(status.length() == 0 || "ACTIVE".equals(status) || "INACTIVE".equals(status)
                || (holdSupported && "HOLD".equals(status)),
                "validation.status", "マスタ状態が不正です。");
        if ("ACTIVE".equals(status)) { where += " and e.active = true"; }
        if ("INACTIVE".equals(status)) { where += " and e.active = false"; }
        if ("HOLD".equals(status)) { where += " and e.onHold = true"; }
        return dao.page("select e" + where + " order by e.code, e.id",
                "select count(e.id)" + where, parameters, search);
    }
}
