package jp.co.tsubame.wholesale.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.StockControlRules;
import jp.co.tsubame.wholesale.common.StockTransferCommand;
import jp.co.tsubame.wholesale.common.StockTransferReceiptCommand;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.entity.StockTransfer;
import jp.co.tsubame.wholesale.entity.StockTransferLine;
import jp.co.tsubame.wholesale.entity.StockTransferReceipt;
import jp.co.tsubame.wholesale.entity.StockTransferReceiptLine;
import jp.co.tsubame.wholesale.entity.Warehouse;

public class StockTransferService extends StockControlSupport {
    public StockTransfer get(Actor actor, Long id) {
        reader(actor);
        return dao.get(StockTransfer.class, id);
    }

    public Page<StockTransfer> search(Actor actor, Search search) {
        reader(actor);
        return documents("StockTransfer", "createdAt", search, true);
    }

    public Page<StockTransferReceipt> listReceipts(Actor actor, Long transferId, Search search) {
        reader(actor);
        Checks.state(search != null, "validation.search", "検索条件が必要です。");
        dao.get(StockTransfer.class, transferId);
        String filter = " from StockTransferReceipt r where r.transfer.id=:transfer";
        return dao.page("select r" + filter + " order by r.id desc", "select count(r.id)" + filter,
                WholesaleDao.params("transfer", transferId), search);
    }

    public StockTransfer saveDraft(Actor actor, StockTransferCommand command) {
        require(actor, "WAREHOUSE", "MANAGER");
        Checks.state(command != null, "validation.command", "倉庫間移動を指定してください。");
        Map<Long, Integer> quantities = StockControlRules.quantities(command.getLines());
        lockReferences(command.getId(), Arrays.asList(command.getSourceWarehouseId(),
                command.getDestinationWarehouseId()), quantities.keySet());
        StockTransfer transfer;
        boolean fresh = command.getId() == null;
        if (fresh) {
            Checks.version(0, command.getExpectedVersion());
            transfer = new StockTransfer();
            transfer.setNumber(initialNumber());
            transfer.setCreatedById(actor.getUserId());
            transfer.setCreatedBy(actor.getLogin());
            transfer.setCreatedAt(new Date());
        } else {
            transfer = dao.lock(StockTransfer.class, command.getId());
            Checks.version(transfer.getVersion(), command.getExpectedVersion());
            state(transfer.getStatus(), "DRAFT");
            transfer.getLines().clear();
            dao.flush();
        }
        Warehouse source = activeWarehouse(command.getSourceWarehouseId());
        Warehouse destination = activeWarehouse(command.getDestinationWarehouseId());
        Checks.state(!source.getId().equals(destination.getId()), "stockControl.sameWarehouse",
                "移動元と移動先は異なる倉庫を指定してください。");
        transfer.setSourceWarehouse(source);
        transfer.setDestinationWarehouse(destination);
        transfer.setNote(Checks.optionalText(command.getNote(), "備考", 500));
        transfer.setUpdatedAt(nextUpdate(transfer.getUpdatedAt()));
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            StockTransferLine line = new StockTransferLine();
            line.setTransfer(transfer);
            line.setProduct(activeProduct(entry.getKey()));
            line.setQuantity(entry.getValue());
            transfer.getLines().add(line);
        }
        dao.save(transfer);
        if (fresh) { transfer.setNumber(documentNumber("TR", transfer.getId())); }
        audit(actor, fresh ? "TRANSFER_CREATE" : "TRANSFER_EDIT", transfer, transfer.getNote());
        dao.flush();
        return transfer;
    }

    public StockTransfer submit(Actor actor, Long id, int expectedVersion) {
        require(actor, "WAREHOUSE", "MANAGER");
        StockTransfer transfer = locked(id, expectedVersion);
        state(transfer.getStatus(), "DRAFT");
        validateReferences(transfer);
        Checks.nonempty(transfer.getLines(), "移動明細");
        transfer.setStatus("SUBMITTED");
        return finish(actor, "TRANSFER_SUBMIT", transfer, transfer.getNote());
    }

    public StockTransfer approve(Actor actor, Long id, int expectedVersion) {
        require(actor, "MANAGER");
        StockTransfer transfer = locked(id, expectedVersion);
        state(transfer.getStatus(), "SUBMITTED");
        StockControlRules.independent(actor, transfer.getCreatedById(), transfer.getCreatedBy());
        validateReferences(transfer);
        transfer.setStatus("APPROVED");
        transfer.setApprovedBy(actor.getLogin());
        transfer.setApprovedAt(new Date());
        return finish(actor, "TRANSFER_APPROVE", transfer, transfer.getNote());
    }

    public StockTransfer dispatch(Actor actor, Long id, int expectedVersion) {
        require(actor, "WAREHOUSE", "BATCH");
        StockTransfer transfer = locked(id, expectedVersion);
        state(transfer.getStatus(), "APPROVED");
        validateReferences(transfer);
        // Only source balances are touched here; all multi-row locks use ascending product IDs.
        List<StockTransferLine> lines = sortedLines(transfer);
        Map<Long, StockBalance> balances = new TreeMap<Long, StockBalance>();
        for (StockTransferLine line : lines) {
            StockBalance balance = ledger.lock(transfer.getSourceWarehouse().getId(), line.getProduct().getId());
            Checks.state(!balance.isBlocked(), "inventory.blocked", "棚卸中の在庫は移動できません。");
            Checks.state(balance.getAvailable() >= line.getQuantity(), "inventory.insufficient",
                    "移動元の利用可能在庫が不足しています。");
            balances.put(line.getProduct().getId(), balance);
        }
        for (StockTransferLine line : lines) {
            Product product = activeProduct(line.getProduct().getId());
            line.setUnitCost(Checks.money(product.getStandardCost(), "移動時標準原価", true));
            ledger.post(balances.get(product.getId()), -line.getQuantity(), 0, "TRANSFER_OUT", "TRANSFER",
                    transfer.getId(), transfer.getNumber(), transfer.getNote(), actor);
            line.setDispatchedQuantity(line.getQuantity());
        }
        transfer.setStatus("IN_TRANSIT");
        transfer.setDispatchedBy(actor.getLogin());
        transfer.setDispatchedAt(new Date());
        return finish(actor, "TRANSFER_DISPATCH", transfer, transfer.getNote());
    }

    public StockTransfer cancel(Actor actor, Long id, int expectedVersion, String reason) {
        require(actor, "WAREHOUSE", "MANAGER");
        StockTransfer transfer = locked(id, expectedVersion);
        Checks.state("DRAFT".equals(transfer.getStatus()) || "SUBMITTED".equals(transfer.getStatus())
                || "APPROVED".equals(transfer.getStatus()), "stockControl.state", "出庫後の移動は取消できません。");
        transfer.setCancellationReason(Checks.text(reason, "取消理由", 500));
        transfer.setStatus("CANCELLED");
        return finish(actor, "TRANSFER_CANCEL", transfer, transfer.getCancellationReason());
    }

    public StockTransfer receive(Actor actor, Long id, int expectedVersion, StockTransferReceiptCommand command) {
        require(actor, "WAREHOUSE", "BATCH");
        return acknowledge(actor, id, expectedVersion, command, false);
    }

    public StockTransfer reconcileLoss(Actor actor, Long id, int expectedVersion,
                                       StockTransferReceiptCommand command) {
        require(actor, "MANAGER");
        return acknowledge(actor, id, expectedVersion, command, true);
    }

    private StockTransfer acknowledge(Actor actor, Long id, int expectedVersion,
                                      StockTransferReceiptCommand command, boolean loss) {
        Checks.state(command != null, "validation.command", "受領内容を指定してください。");
        String requestKey = Checks.text(command.getRequestKey(), "処理キー", 120);
        String note = loss ? Checks.text(command.getNote(), "損失理由", 500)
                : Checks.optionalText(command.getNote(), "受領備考", 500);
        Map<Long, Integer> quantities = StockControlRules.quantities(command.getLines());
        String kind = loss ? "LOSS" : "RECEIPT";
        String fingerprint = StockControlRules.fingerprint(id, kind, note, quantities);
        lockReferences(id, Collections.<Long>emptyList(), Collections.<Long>emptyList());
        StockTransfer transfer = dao.lock(StockTransfer.class, id);
        dao.lockKey("stock-transfer-acknowledgement", requestKey);
        List<StockTransferReceipt> prior = dao.list("from StockTransferReceipt r where r.requestKey=:key",
                WholesaleDao.params("key", requestKey));
        if (!prior.isEmpty()) {
            Checks.state(prior.get(0).getFingerprint().equals(fingerprint), "idempotency.conflict",
                    "同じ処理キーに異なる受領・損失内容が指定されています。");
            return transfer;
        }
        Checks.version(transfer.getVersion(), expectedVersion);
        Checks.state("IN_TRANSIT".equals(transfer.getStatus()) || "PART_RECEIVED".equals(transfer.getStatus()),
                "stockControl.state", "輸送中の移動のみ受領・損失処理できます。");
        validateReferences(transfer);
        Map<Long, StockTransferLine> lines = new TreeMap<Long, StockTransferLine>();
        for (StockTransferLine line : transfer.getLines()) { lines.put(line.getProduct().getId(), line); }
        Map<Long, StockBalance> balances = new TreeMap<Long, StockBalance>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            StockTransferLine line = lines.get(entry.getKey());
            Checks.state(line != null, "stockControl.foreignLine", "この移動に含まれない商品です。");
            Checks.state(entry.getValue() <= line.getInTransitQuantity(), "stockControl.overReceipt",
                    "受領・損失数量が未受領数量を超えています。");
            if (!loss) {
                StockBalance balance = ledger.lock(transfer.getDestinationWarehouse().getId(), entry.getKey());
                Checks.state(!balance.isBlocked(), "inventory.blocked", "移動先が棚卸中です。");
                Checks.state((long) balance.getOnHand() + entry.getValue() <= 2000000000L,
                        "inventory.insufficient", "移動先の在庫上限を超えています。");
                balances.put(entry.getKey(), balance);
            }
        }
        StockTransferReceipt receipt = new StockTransferReceipt();
        receipt.setTransfer(transfer);
        receipt.setNumber((loss ? "TRL-" : "TRR-") + UUID.randomUUID().toString());
        receipt.setKind(kind);
        receipt.setRequestKey(requestKey);
        receipt.setFingerprint(fingerprint);
        receipt.setNote(note);
        receipt.setCreatedBy(actor.getLogin());
        receipt.setCreatedAt(new Date());
        dao.save(receipt);
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            StockTransferLine line = lines.get(entry.getKey());
            int quantity = entry.getValue();
            StockTransferReceiptLine received = new StockTransferReceiptLine();
            received.setReceipt(receipt);
            received.setTransferLine(line);
            received.setQuantity(quantity);
            received.setUnitCost(line.getUnitCost());
            receipt.getLines().add(received);
            dao.save(received);
            if (loss) {
                // Dispatch already removed physical stock. Loss closes transit, not a second stock issue.
                line.setLostQuantity(line.getLostQuantity() + quantity);
            } else {
                ledger.post(balances.get(entry.getKey()), quantity, 0, "TRANSFER_IN", "TRANSFER_RECEIPT",
                        receipt.getId(), receipt.getNumber(), note, actor);
                line.setReceivedQuantity(line.getReceivedQuantity() + quantity);
            }
        }
        long lost = 0;
        long received = 0;
        for (StockTransferLine line : transfer.getLines()) {
            lost += line.getLostQuantity();
            received += line.getReceivedQuantity();
        }
        if (transfer.getInTransitQuantity() == 0L) {
            transfer.setStatus(lost == 0L ? "COMPLETED" : "RECONCILED");
            transfer.setCompletedAt(new Date());
        } else {
            transfer.setStatus(received == 0L ? "IN_TRANSIT" : "PART_RECEIVED");
        }
        audit(actor, loss ? "TRANSFER_LOSS_EVENT" : "TRANSFER_RECEIPT_EVENT", receipt, note);
        return finish(actor, loss ? "TRANSFER_RECONCILE_LOSS" : "TRANSFER_RECEIVE", transfer, note);
    }

    private void validateReferences(StockTransfer transfer) {
        activeWarehouse(transfer.getSourceWarehouse().getId());
        activeWarehouse(transfer.getDestinationWarehouse().getId());
        Checks.state(!transfer.getSourceWarehouse().getId().equals(transfer.getDestinationWarehouse().getId()),
                "stockControl.sameWarehouse", "移動元と移動先は同じ倉庫にできません。");
        for (StockTransferLine line : transfer.getLines()) {
            activeProduct(line.getProduct().getId());
            Checks.quantity(line.getQuantity(), "移動数量");
        }
    }

    private StockTransfer locked(Long id, int expectedVersion) {
        Checks.state(id != null, "validation.id", "移動伝票を指定してください。");
        lockReferences(id, Collections.<Long>emptyList(), Collections.<Long>emptyList());
        StockTransfer transfer = dao.lock(StockTransfer.class, id);
        Checks.version(transfer.getVersion(), expectedVersion);
        return transfer;
    }

    private void lockReferences(Long id, Collection<Long> additionalWarehouses, Collection<Long> additionalProducts) {
        List<Long> warehouses = new ArrayList<Long>(additionalWarehouses);
        List<Long> products = new ArrayList<Long>(additionalProducts);
        if (id != null) {
            // Scalar discovery avoids caching a stale header while an idempotent receipt waits on references.
            Object[] snapshot = (Object[]) dao.query(
                    "select t.sourceWarehouse.id,t.destinationWarehouse.id from StockTransfer t where t.id=:id",
                    WholesaleDao.params("id", id)).uniqueResult();
            Checks.state(snapshot != null, "notFound", "移動伝票が見つかりません。");
            warehouses.add((Long) snapshot[0]);
            warehouses.add((Long) snapshot[1]);
            products.addAll(dao.<Long>list("select l.product.id from StockTransferLine l where l.transfer.id=:id",
                    WholesaleDao.params("id", id)));
        }
        // Draft replacement protects both old and new references in one warehouse-first ordering.
        dao.lockReferences(warehouses, products);
    }

    private StockTransfer finish(Actor actor, String operation, StockTransfer transfer, String detail) {
        transfer.setUpdatedAt(nextUpdate(transfer.getUpdatedAt()));
        audit(actor, operation, transfer, detail);
        dao.flush();
        return transfer;
    }

    private List<StockTransferLine> sortedLines(StockTransfer transfer) {
        List<StockTransferLine> result = new ArrayList<StockTransferLine>(transfer.getLines());
        Collections.sort(result, new Comparator<StockTransferLine>() {
            @Override
            public int compare(StockTransferLine first, StockTransferLine second) {
                return first.getProduct().getId().compareTo(second.getProduct().getId());
            }
        });
        return result;
    }
}
