package jp.co.tsubame.wholesale.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.StockControlRules;
import jp.co.tsubame.wholesale.common.StockCountCommand;
import jp.co.tsubame.wholesale.common.StockCountEntry;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.entity.StockCount;
import jp.co.tsubame.wholesale.entity.StockCountLine;
import org.hibernate.LockMode;

public class StockCountService extends StockControlSupport {
    public StockCount get(Actor actor, Long id) {
        reader(actor);
        return dao.get(StockCount.class, id);
    }

    public Page<StockCount> search(Actor actor, Search search) {
        reader(actor);
        return documents("StockCount", "createdAt", search, false);
    }

    @SuppressWarnings("unchecked")
    public StockCount begin(Actor actor, StockCountCommand command) {
        require(actor, "WAREHOUSE", "MANAGER");
        Checks.state(command != null, "validation.command", "棚卸範囲を指定してください。");
        // A warehouse lock stabilizes the actual balance set before its product keys are discovered.
        dao.lockReferences(Collections.singleton(command.getWarehouseId()), Collections.<Long>emptyList());
        Map<String, Object> parameters = WholesaleDao.params("warehouse", command.getWarehouseId());
        String filter = " from StockBalance b where b.warehouse.id=:warehouse";
        List<Long> productIds = command.getProductIds();
        boolean filtered = productIds != null && !productIds.isEmpty();
        if (filtered) {
            Checks.nonempty(productIds, "棚卸対象商品");
            Set<Long> unique = new HashSet<Long>();
            for (Long productId : productIds) {
                Checks.state(productId != null && productId > 0 && unique.add(productId),
                        "stockControl.countSelection", "棚卸商品は有効なIDを重複せず指定してください。");
            }
            filter += " and b.product.id in (:products)";
            parameters.put("products", productIds);
        }
        // Select real balance keys, not synthetic zero balances or only rows with positive stock.
        List<Object[]> balances = (List<Object[]>) dao.query("select b.id,b.product.id" + filter + " order by b.product.id",
                parameters).setMaxResults(201).list();
        Checks.nonempty(balances, "棚卸対象在庫");
        Checks.state(!filtered || balances.size() == productIds.size(), "stockControl.countMissingBalance",
                "指定した全商品の在庫残高がこの倉庫に存在する必要があります。");
        List<Long> selectedProducts = new ArrayList<Long>();
        for (Object[] balance : balances) { selectedProducts.add((Long) balance[1]); }
        dao.lockReferences(Collections.<Long>emptyList(), selectedProducts);
        StockCount count = new StockCount();
        count.setNumber(initialNumber());
        count.setWarehouse(activeWarehouse(command.getWarehouseId()));
        count.setNote(Checks.optionalText(command.getNote(), "棚卸備考", 500));
        count.setCreatedById(actor.getUserId());
        count.setCreatedBy(actor.getLogin());
        count.setCreatedAt(new Date());
        count.setUpdatedAt(new Date());
        dao.save(count);
        count.setNumber(documentNumber("CNT", count.getId()));
        for (Object[] selected : balances) {
            StockBalance balance = lockedBalance((Long) selected[0]);
            activeProduct(balance.getProduct().getId());
            Checks.state(!balance.isBlocked(), "inventory.blocked", "別の棚卸または利用停止により在庫が凍結されています。");
            StockCountLine line = new StockCountLine();
            line.setStockCount(count);
            line.setBalance(balance);
            line.setSnapshotOnHand(balance.getOnHand());
            line.setSnapshotReserved(balance.getReserved());
            line.setSnapshotMovementAt(balance.getLastMovementAt());
            line.setUnitCost(Checks.money(balance.getProduct().getStandardCost(), "棚卸開始時標準原価", true));
            balance.setBlocked(true);
            count.getLines().add(line);
            dao.save(line);
        }
        dao.flush();
        // The frozen version includes this transaction's blocked=true update.
        for (StockCountLine line : count.getLines()) { line.setSnapshotVersion(line.getBalance().getVersion()); }
        audit(actor, "COUNT_BEGIN", count, "balances=" + count.getLines().size() + "; " + count.getNote());
        dao.flush();
        return count;
    }

    public StockCount record(Actor actor, Long id, int expectedVersion, List<StockCountEntry> entries) {
        require(actor, "WAREHOUSE", "BATCH");
        Checks.nonempty(entries, "実棚入力");
        StockCount count = locked(id, expectedVersion);
        state(count.getStatus(), "COUNTING");
        Map<Long, StockCountLine> lines = new LinkedHashMap<Long, StockCountLine>();
        for (StockCountLine line : count.getLines()) { lines.put(line.getId(), line); }
        Set<Long> entered = new HashSet<Long>();
        for (StockCountEntry entry : entries) {
            Checks.state(entry != null && entry.getLineId() != null && entered.add(entry.getLineId()),
                    "stockControl.duplicateCountLine", "実棚明細は重複せず指定してください。");
            StockCountLine line = lines.get(entry.getLineId());
            Checks.state(line != null, "stockControl.foreignLine", "別の棚卸の明細は入力できません。");
            int quantity = StockControlRules.counted(entry.getCountedQuantity());
            String note = Checks.optionalText(entry.getNote(), "差異理由", 500);
            if (quantity != line.getSnapshotOnHand()) { note = Checks.text(note, "差異理由", 500); }
            line.setCountedQuantity(quantity);
            line.setNote(note);
            line.setCountedBy(actor.getLogin());
            line.setCountedAt(new Date());
        }
        return finish(actor, "COUNT_RECORD", count, "lines=" + entries.size());
    }

    public StockCount review(Actor actor, Long id, int expectedVersion) {
        require(actor, "WAREHOUSE", "MANAGER");
        StockCount count = locked(id, expectedVersion);
        state(count.getStatus(), "COUNTING");
        validateComplete(count);
        count.setStatus("REVIEWED");
        count.setReviewedBy(actor.getLogin());
        count.setReviewedAt(new Date());
        return finish(actor, "COUNT_REVIEW", count, "discrepancies=" + count.getDiscrepancyLines()
                + "; value=" + count.getCountedDifferenceValue());
    }

    public StockCount reopen(Actor actor, Long id, int expectedVersion, String reason) {
        require(actor, "WAREHOUSE", "MANAGER");
        StockCount count = locked(id, expectedVersion);
        state(count.getStatus(), "REVIEWED");
        String detail = Checks.text(reason, "再入力理由", 500);
        count.setStatus("COUNTING");
        count.setReviewedBy(null);
        count.setReviewedAt(null);
        return finish(actor, "COUNT_REOPEN", count, detail);
    }

    public StockCount approve(Actor actor, Long id, int expectedVersion) {
        require(actor, "MANAGER");
        StockCount count = locked(id, expectedVersion);
        state(count.getStatus(), "REVIEWED");
        StockControlRules.independent(actor, count.getCreatedById(), count.getCreatedBy());
        activeWarehouse(count.getWarehouse().getId());
        validateComplete(count);
        List<StockCountLine> lines = sortedLines(count);
        for (StockCountLine line : lines) {
            activeProduct(line.getProduct().getId());
            StockBalance balance = lockedBalance(line.getBalance().getId());
            validateSnapshot(line, balance);
            Checks.state(line.getCountedQuantity() >= balance.getReserved(), "inventory.insufficient",
                    "実棚数量が引当数量を下回っています。棚卸を取消して引当を見直してから再実施してください。");
        }
        for (StockCountLine line : lines) {
            StockBalance balance = line.getBalance();
            int difference = line.getDifference();
            if (difference != 0) {
                ledger.post(balance, difference, 0, "COUNT", "STOCK_COUNT", count.getId(),
                        count.getNumber(), line.getNote(), actor);
            }
            balance.setBlocked(false);
            line.setHolding(false);
        }
        count.setStatus("APPROVED");
        count.setApprovedBy(actor.getLogin());
        count.setApprovedAt(new Date());
        return finish(actor, "COUNT_APPROVE", count, "discrepancies=" + count.getDiscrepancyLines()
                + "; value=" + count.getCountedDifferenceValue());
    }

    public StockCount cancel(Actor actor, Long id, int expectedVersion, String reason) {
        require(actor, "WAREHOUSE", "MANAGER");
        StockCount count = locked(id, expectedVersion);
        Checks.state("COUNTING".equals(count.getStatus()) || "REVIEWED".equals(count.getStatus()),
                "stockControl.state", "未承認の棚卸のみ取消できます。");
        String detail = Checks.text(reason, "取消理由", 500);
        int changedSnapshots = 0;
        for (StockCountLine line : sortedLines(count)) {
            StockBalance balance = lockedBalance(line.getBalance().getId());
            Checks.state(line.isHolding(), "stockControl.countOwnership", "棚卸の凍結所有状態が不正です。");
            if (!matchesSnapshot(line, balance)) { changedSnapshots++; }
            balance.setBlocked(false);
            line.setHolding(false);
        }
        count.setStatus("CANCELLED");
        count.setCancellationReason(detail);
        return finish(actor, "COUNT_CANCEL_UNBLOCK", count,
                "unblocked=" + count.getLines().size() + "; changedSnapshots=" + changedSnapshots + "; " + detail);
    }

    private void validateComplete(StockCount count) {
        Checks.nonempty(count.getLines(), "棚卸明細");
        for (StockCountLine line : count.getLines()) {
            int quantity = StockControlRules.counted(line.getCountedQuantity());
            if (quantity != line.getSnapshotOnHand()) { Checks.text(line.getNote(), "差異理由", 500); }
        }
    }

    private void validateSnapshot(StockCountLine line, StockBalance balance) {
        Checks.state(line.isHolding() && matchesSnapshot(line, balance), "stockControl.countSnapshot",
                "棚卸開始後に在庫残高が変わりました。取消して再度棚卸してください。");
    }

    private boolean matchesSnapshot(StockCountLine line, StockBalance balance) {
        Date snapshot = line.getSnapshotMovementAt();
        Date current = balance.getLastMovementAt();
        return balance.isBlocked() && balance.getVersion() == line.getSnapshotVersion()
                && balance.getOnHand() == line.getSnapshotOnHand()
                && balance.getReserved() == line.getSnapshotReserved()
                && (snapshot == null ? current == null : current != null && snapshot.getTime() == current.getTime());
    }

    private StockBalance lockedBalance(Long id) {
        StockBalance balance = dao.lock(StockBalance.class, id);
        // Eager count lines may already have loaded an older balance before the lock wait.
        dao.session().refresh(balance, LockMode.UPGRADE);
        return balance;
    }

    private StockCount locked(Long id, int expectedVersion) {
        Checks.state(id != null, "validation.id", "棚卸を指定してください。");
        Long warehouseId = (Long) dao.query("select c.warehouse.id from StockCount c where c.id=:id",
                WholesaleDao.params("id", id)).uniqueResult();
        Checks.state(warehouseId != null, "notFound", "棚卸が見つかりません。");
        List<Long> products = dao.list("select l.balance.product.id from StockCountLine l where l.stockCount.id=:id",
                WholesaleDao.params("id", id));
        dao.lockReferences(Collections.singleton(warehouseId), products);
        StockCount count = dao.lock(StockCount.class, id);
        Checks.version(count.getVersion(), expectedVersion);
        return count;
    }

    private StockCount finish(Actor actor, String operation, StockCount count, String detail) {
        count.setUpdatedAt(nextUpdate(count.getUpdatedAt()));
        audit(actor, operation, count, detail);
        dao.flush();
        return count;
    }

    private List<StockCountLine> sortedLines(StockCount count) {
        List<StockCountLine> lines = new ArrayList<StockCountLine>(count.getLines());
        Collections.sort(lines, new Comparator<StockCountLine>() {
            @Override
            public int compare(StockCountLine first, StockCountLine second) {
                return first.getProduct().getId().compareTo(second.getProduct().getId());
            }
        });
        return lines;
    }
}
