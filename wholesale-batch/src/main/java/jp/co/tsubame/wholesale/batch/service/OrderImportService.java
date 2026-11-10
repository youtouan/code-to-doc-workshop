package jp.co.tsubame.wholesale.batch.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.batch.ImportFields;
import jp.co.tsubame.wholesale.batch.OrderGroup;
import jp.co.tsubame.wholesale.batch.RowOutcome;
import jp.co.tsubame.wholesale.batch.entity.OrderImportRecord;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.common.OrderLineInput;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.BaseService;
import jp.co.tsubame.wholesale.service.OrderService;

/** REQUIRED: claim, core DRAFT and batch SUCCESS must share executeRow's transaction. */
public class OrderImportService extends BaseService {
    private OrderService orderService;
    public void setOrderService(OrderService value) { orderService = value; }

    public RowOutcome importDraft(OrderGroup group, Actor actor) {
        require(actor, "BATCH");
        require(actor, "SALES", "MANAGER");
        Checks.state(group != null, "orderImport.group", "Grouped order input is required");
        group.validate();
        String hash = group.canonicalHash();
        OrderImportRecord previous = findClaim(group.getKey());
        if (previous != null) { return replay(previous, hash, actor); }

        List<String> header = group.getRecords().get(0);
        Customer customer = exact("Customer", Customer.class, header.get(1), "customer_code");
        Warehouse warehouse = exact("Warehouse", Warehouse.class, header.get(2), "warehouse_code");
        OrderInput input = new OrderInput();
        input.setCustomerId(customer.getId());
        input.setWarehouseId(warehouse.getId());
        input.setOrderDate(Dates.parse(header.get(3)));
        input.setRequestedDate(Dates.parse(header.get(4)));
        input.setExternalReference(header.get(5));
        input.setDeliveryAddress(header.get(6));
        input.setNotes(header.get(9));
        List<Long> productIds = new ArrayList<Long>();
        for (List<String> row : group.getRecords()) {
            Product product = exact("Product", Product.class, row.get(7), "product_code");
            OrderLineInput line = new OrderLineInput();
            line.setProductId(product.getId());
            line.setQuantity(ImportFields.integer(row.get(8), "quantity"));
            input.getLines().add(line);
            productIds.add(product.getId());
        }
        // Match Inventory/Order lock order: warehouse -> sorted products -> external key -> core customer/header.
        dao.lockReferences(Collections.singleton(warehouse.getId()), productIds);
        dao.lockKey("order-import", group.getKey());
        previous = findClaim(group.getKey());
        if (previous != null) { return replay(previous, hash, actor); }
        SalesOrder order = orderService.saveDraft(null, 0, input, actor);
        OrderImportRecord claim = new OrderImportRecord();
        claim.setExternalKey(group.getKey());
        claim.setPayloadHash(hash);
        claim.setOrder(order);
        claim.setCreatedById(actor.getUserId());
        claim.setCreatedBy(actor.getLogin());
        claim.setCreatedAt(new Date());
        dao.save(claim);
        dao.flush();
        return new RowOutcome("orderImport.created", "DRAFT created; lines=" + order.getLines().size()
                + "; total=" + order.getTotalAmount().toPlainString(), "SalesOrder", order.getId(), order.getNumber());
    }

    private OrderImportRecord findClaim(String key) {
        List<OrderImportRecord> found = dao.list("from OrderImportRecord where externalKey=:key",
                WholesaleDao.params("key", key));
        return found.isEmpty() ? null : found.get(0);
    }

    private RowOutcome replay(OrderImportRecord claim, String hash, Actor actor) {
        Checks.state(claim.getPayloadHash().equals(hash), "orderImport.keyConflict",
                "external_key already belongs to a different order payload; original order was not edited");
        SalesOrder order = orderService.getOrder(claim.getOrder().getId(), actor);
        return new RowOutcome("orderImport.replayed", "Original order returned without editing; status="
                + order.getStatus(), "SalesOrder", order.getId(), order.getNumber());
    }

    private <T> T exact(String entity, Class<T> type, String code, String field) {
        List<?> found = dao.list("from " + entity + " where code=:code", WholesaleDao.params("code", code));
        if (found.isEmpty()) { throw new BusinessException("orderImport.reference", "Unknown " + field + ": " + code); }
        return type.cast(found.get(0));
    }
}
