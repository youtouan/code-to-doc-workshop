package jp.co.tsubame.wholesale.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Fingerprints;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.dao.InventoryLedger;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Reservation;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.entity.StockMovement;
import jp.co.tsubame.wholesale.entity.StockReceipt;
import jp.co.tsubame.wholesale.entity.Warehouse;

public class InventoryService extends BaseService {
    private InventoryLedger ledger;

    public void setLedger(InventoryLedger ledger) {
        this.ledger = ledger;
    }

    public StockReceipt receive(String requestKey, Long warehouseId, Long productId, int quantity,
                                BigDecimal unitCost, Date receiptDate, String reference, String note, Actor actor) {
        require(actor, "WAREHOUSE", "BATCH", "MANAGER");
        String key = Checks.text(requestKey, "受付キー", 120);
        Checks.quantity(quantity, "入庫数量");
        BigDecimal cost = Checks.money(unitCost, "仕入単価", true);
        Date date = Checks.date(receiptDate, "入庫日");
        Checks.state(!date.after(Dates.today()), "receipt.future", "未来日の入庫は登録できません。");
        String ref = Checks.text(reference, "参照番号", 80);
        String remark = Checks.optionalText(note, "備考", 500);
        String fingerprint = Fingerprints.of(String.valueOf(warehouseId), String.valueOf(productId),
                String.valueOf(quantity), cost.toPlainString(), Dates.format(date), ref, remark);
        dao.lockReferences(Collections.singleton(warehouseId), Collections.singleton(productId));
        dao.lockKey("receipt", key);
        List<StockReceipt> existing = dao.list("from StockReceipt where requestKey=:key",
                WholesaleDao.params("key", key));
        if (!existing.isEmpty()) {
            Checks.state(existing.get(0).getFingerprint().equals(fingerprint), "idempotency.conflict",
                    "同じ受付キーで異なる内容が登録されています。");
            return existing.get(0);
        }
        Warehouse warehouse = dao.get(Warehouse.class, warehouseId);
        Product product = dao.get(Product.class, productId);
        Checks.state(warehouse.isActive() && product.isActive(), "receipt.inactive", "停止中の商品・倉庫には入庫できません。");
        StockReceipt receipt = new StockReceipt();
        receipt.setNumber("NEW-" + UUID.randomUUID().toString());
        receipt.setRequestKey(key);
        receipt.setFingerprint(fingerprint);
        receipt.setWarehouse(warehouse);
        receipt.setProduct(product);
        receipt.setQuantity(quantity);
        receipt.setUnitCost(cost);
        receipt.setReceiptDate(date);
        receipt.setReference(ref);
        receipt.setNote(remark);
        receipt.setCreatedBy(actor.getLogin());
        receipt.setCreatedAt(new Date());
        dao.save(receipt);
        receipt.setNumber(documentNumber("RC", receipt.getId()));
        StockBalance balance = ledger.lock(warehouseId, productId);
        ledger.post(balance, quantity, 0, "RECEIPT", "StockReceipt", receipt.getId(),
                receipt.getNumber(), ref, actor);
        audit(actor, "STOCK_RECEIVE", receipt, ref + " quantity=" + quantity);
        dao.flush();
        return receipt;
    }

    public int reserve(SalesOrderLine line, int requested, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BATCH");
        Checks.quantity(requested, "引当数量");
        Checks.state(requested <= line.getShortageQuantity(), "allocation.excess", "未引当数量を超えています。");
        StockBalance balance = ledger.lock(line.getOrder().getWarehouse().getId(), line.getProduct().getId());
        if (balance.isBlocked()) {
            return 0;
        }
        int quantity = Math.min(balance.getAvailable(), requested);
        quantity -= quantity % line.getPackSize();
        if (quantity == 0) {
            return 0;
        }
        Reservation reservation = reservation(line, balance);
        reservation.setQuantity(reservation.getQuantity() + quantity);
        reservation.setUpdatedAt(new Date());
        line.setAllocatedQuantity(line.getAllocatedQuantity() + quantity);
        ledger.post(balance, 0, quantity, "RESERVE", "SalesOrder", line.getOrder().getId(),
                line.getOrder().getNumber(), "line=" + line.getLineNumber(), actor);
        return quantity;
    }

    public void release(SalesOrderLine line, int quantity, String reason, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BATCH");
        Checks.quantity(quantity, "引当解除数量");
        StockBalance balance = ledger.lock(line.getOrder().getWarehouse().getId(), line.getProduct().getId());
        Reservation reservation = reservation(line, balance);
        Checks.state(quantity <= reservation.getQuantity() && quantity <= line.getAllocatedQuantity(),
                "allocation.release", "解除対象の引当数量が不足しています。");
        reservation.setQuantity(reservation.getQuantity() - quantity);
        reservation.setUpdatedAt(new Date());
        line.setAllocatedQuantity(line.getAllocatedQuantity() - quantity);
        ledger.post(balance, 0, -quantity, "RELEASE", "SalesOrder", line.getOrder().getId(),
                line.getOrder().getNumber(), reason, actor);
    }

    public void consume(SalesOrderLine line, int quantity, Long shipmentId, String shipmentNumber, Actor actor) {
        require(actor, "WAREHOUSE", "BATCH");
        Checks.quantity(quantity, "出荷数量");
        StockBalance balance = ledger.lock(line.getOrder().getWarehouse().getId(), line.getProduct().getId());
        Reservation reservation = reservation(line, balance);
        Checks.state(quantity <= reservation.getQuantity() && quantity <= line.getAllocatedQuantity(),
                "shipping.reservation", "出荷に必要な引当が不足しています。");
        reservation.setQuantity(reservation.getQuantity() - quantity);
        reservation.setUpdatedAt(new Date());
        line.setAllocatedQuantity(line.getAllocatedQuantity() - quantity);
        line.setShippedQuantity(line.getShippedQuantity() + quantity);
        ledger.post(balance, -quantity, -quantity, "SHIP", "Shipment", shipmentId,
                shipmentNumber, line.getProductCode(), actor);
    }

    public Page<StockBalance> searchStock(Search search, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        Map<String, Object> params = WholesaleDao.params("text", search.getLikeText());
        String where = " from StockBalance b where (b.product.code like :text escape '!' "
                + "or b.product.name like :text escape '!')";
        if (search.getWarehouseId() != null) {
            where += " and b.warehouse.id=:warehouse";
            params.put("warehouse", search.getWarehouseId());
        }
        if ("SHORT".equals(search.getStatus())) {
            where += " and b.onHand-b.reserved < b.product.reorderPoint";
        } else if ("BLOCKED".equals(search.getStatus())) {
            where += " and b.blocked=true";
        } else if ("AVAILABLE".equals(search.getStatus())) {
            where += " and b.blocked=false and b.onHand>b.reserved";
        }
        return dao.page("select b" + where + " order by b.warehouse.code,b.product.code",
                "select count(b.id)" + where, params, search);
    }

    public StockBalance getStock(Long id, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        return dao.get(StockBalance.class, id);
    }

    public Page<StockMovement> searchMovements(Long balanceId, Search search, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        Map<String, Object> params = WholesaleDao.params("balance", balanceId);
        String where = " from StockMovement m where m.balance.id=:balance";
        if (search.getFrom() != null) {
            where += " and m.occurredAt>=:from";
            params.put("from", search.getFrom());
        }
        if (search.getTo() != null) {
            where += " and m.occurredAt<:to";
            params.put("to", Dates.addDays(search.getTo(), 1));
        }
        if (search.getStatus().length() != 0) {
            where += " and m.movementType=:type";
            params.put("type", search.getStatus());
        }
        return dao.page("select m" + where + " order by m.id desc",
                "select count(m.id)" + where, params, search);
    }

    public Page<StockReceipt> searchReceipts(Search search, Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        Map<String, Object> params = WholesaleDao.params("text", search.getLikeText());
        String where = " from StockReceipt r where (r.number like :text escape '!' "
                + "or r.reference like :text escape '!' or r.product.name like :text escape '!')";
        if (search.getWarehouseId() != null) {
            where += " and r.warehouse.id=:warehouse";
            params.put("warehouse", search.getWarehouseId());
        }
        if (search.getFrom() != null) {
            where += " and r.receiptDate>=:from";
            params.put("from", search.getFrom());
        }
        if (search.getTo() != null) {
            where += " and r.receiptDate<=:to";
            params.put("to", search.getTo());
        }
        return dao.page("select r" + where + " order by r.id desc",
                "select count(r.id)" + where, params, search);
    }

    public StockReceipt getReceipt(Long id, Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        return dao.get(StockReceipt.class, id);
    }

    public List<Reservation> listReservations(Long orderId, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BATCH");
        return dao.list("from Reservation r where r.orderLine.order.id=:order and r.quantity>0 "
                + "order by r.orderLine.lineNumber", WholesaleDao.params("order", orderId));
    }

    private Reservation reservation(SalesOrderLine line, StockBalance balance) {
        List<Reservation> found = dao.list("from Reservation r where r.orderLine.id=:line",
                WholesaleDao.params("line", line.getId()));
        if (!found.isEmpty()) {
            return found.get(0);
        }
        Reservation created = new Reservation();
        created.setOrderLine(line);
        created.setBalance(balance);
        created.setQuantity(0);
        created.setUpdatedAt(new Date());
        dao.save(created);
        return created;
    }
}
