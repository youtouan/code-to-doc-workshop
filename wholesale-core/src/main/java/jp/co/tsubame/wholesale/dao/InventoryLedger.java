package jp.co.tsubame.wholesale.dao;

import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.entity.StockMovement;
import org.hibernate.LockMode;

public class InventoryLedger {
    private WholesaleDao dao;

    public void setDao(WholesaleDao dao) {
        this.dao = dao;
    }

    public StockBalance lock(Long warehouseId, Long productId) {
        Checks.state(warehouseId != null && productId != null, "inventory.key", "倉庫と商品を指定してください。");
        // PostgreSQL arbitrates creation of the first balance before the row lock is taken.
        dao.session().createSQLQuery("insert into stock_balance "
                + "(id,version,warehouse_id,product_id,on_hand,reserved,blocked) "
                + "values(nextval('wholesale_seq'),0,:warehouse,:product,0,0,false) "
                + "on conflict (warehouse_id,product_id) do nothing")
                .setLong("warehouse", warehouseId).setLong("product", productId).executeUpdate();
        Object result = dao.query("from StockBalance b where b.warehouse.id=:warehouse and b.product.id=:product",
                WholesaleDao.params("warehouse", warehouseId, "product", productId))
                .setLockMode("b", LockMode.UPGRADE).uniqueResult();
        Checks.state(result != null, "inventory.balance", "在庫残高の作成に失敗しました。");
        return StockBalance.class.cast(result);
    }

    public void post(StockBalance balance, int stockDelta, int reservationDelta, String type,
                     String documentType, Long documentId, String number, String note, Actor actor) {
        Checks.state(!balance.isBlocked() || "COUNT".equals(type),
                "inventory.blocked", "棚卸中または利用停止中の在庫は変更できません。");
        long stock = (long) balance.getOnHand() + stockDelta;
        long reserved = (long) balance.getReserved() + reservationDelta;
        Checks.state(stock >= 0 && reserved >= 0 && stock >= reserved && stock <= 2000000000L,
                "inventory.insufficient", "利用可能在庫または引当数量が不足しています。");
        Checks.state(stockDelta != 0 || reservationDelta != 0, "inventory.noChange", "在庫移動数量が0です。");
        Checks.state(documentId != null && actor != null, "inventory.source", "在庫移動の発生元がありません。");
        balance.setOnHand((int) stock);
        balance.setReserved((int) reserved);
        balance.setLastMovementAt(new Date());
        StockMovement movement = new StockMovement();
        movement.setBalance(balance);
        movement.setMovementType(Checks.text(type, "移動区分", 30));
        movement.setQuantityChange(stockDelta);
        movement.setReservedChange(reservationDelta);
        movement.setOnHandAfter((int) stock);
        movement.setReservedAfter((int) reserved);
        movement.setDocumentType(Checks.text(documentType, "伝票種別", 40));
        movement.setDocumentId(documentId);
        movement.setDocumentNumber(Checks.text(number, "伝票番号", 50));
        movement.setNote(Checks.optionalText(note, "備考", 500));
        movement.setActor(actor.getLogin());
        movement.setOccurredAt(new Date());
        dao.save(movement);
    }

    public List<StockMovement> movements(Long balanceId) {
        return dao.list("from StockMovement m where m.balance.id=:balance order by m.id desc",
                WholesaleDao.params("balance", balanceId));
    }
}
