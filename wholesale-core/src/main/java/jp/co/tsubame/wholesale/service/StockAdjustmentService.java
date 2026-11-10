package jp.co.tsubame.wholesale.service;

import java.util.Collections;
import java.util.Date;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.StockAdjustmentCommand;
import jp.co.tsubame.wholesale.common.StockControlRules;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.StockAdjustment;
import jp.co.tsubame.wholesale.entity.StockBalance;

public class StockAdjustmentService extends StockControlSupport {
    public StockAdjustment get(Actor actor, Long id) {
        reader(actor);
        return dao.get(StockAdjustment.class, id);
    }

    public Page<StockAdjustment> search(Actor actor, Search search) {
        reader(actor);
        return documents("StockAdjustment", "proposedAt", search, false);
    }

    public StockAdjustment propose(Actor actor, StockAdjustmentCommand command) {
        require(actor, "WAREHOUSE", "MANAGER");
        Checks.state(command != null, "validation.command", "在庫調整内容を指定してください。");
        dao.lockReferences(Collections.singleton(command.getWarehouseId()), Collections.singleton(command.getProductId()));
        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setNumber(initialNumber());
        adjustment.setWarehouse(activeWarehouse(command.getWarehouseId()));
        adjustment.setProduct(activeProduct(command.getProductId()));
        adjustment.setQuantityChange(StockControlRules.adjustment(command.getQuantityChange()));
        adjustment.setReason(Checks.text(command.getReason(), "調整理由", 500));
        adjustment.setUnitCost(Checks.money(adjustment.getProduct().getStandardCost(), "申請時標準原価", true));
        adjustment.setProposedById(actor.getUserId());
        adjustment.setProposedBy(actor.getLogin());
        adjustment.setProposedAt(new Date());
        dao.save(adjustment);
        adjustment.setNumber(documentNumber("ADJ", adjustment.getId()));
        audit(actor, "ADJUSTMENT_PROPOSE", adjustment, adjustment.getReason());
        dao.flush();
        return adjustment;
    }

    public StockAdjustment approve(Actor actor, Long id, int expectedVersion, String reason) {
        require(actor, "MANAGER");
        StockAdjustment adjustment = locked(id, expectedVersion);
        StockControlRules.independent(actor, adjustment.getProposedById(), adjustment.getProposedBy());
        String decision = Checks.text(reason, "承認理由", 500);
        activeWarehouse(adjustment.getWarehouse().getId());
        activeProduct(adjustment.getProduct().getId());
        StockBalance balance = ledger.lock(adjustment.getWarehouse().getId(), adjustment.getProduct().getId());
        ledger.post(balance, adjustment.getQuantityChange(), 0, "ADJUSTMENT", "STOCK_ADJUSTMENT",
                adjustment.getId(), adjustment.getNumber(), adjustment.getReason(), actor);
        return decide(actor, adjustment, "APPROVED", decision);
    }

    public StockAdjustment reject(Actor actor, Long id, int expectedVersion, String reason) {
        require(actor, "MANAGER");
        StockAdjustment adjustment = locked(id, expectedVersion);
        StockControlRules.independent(actor, adjustment.getProposedById(), adjustment.getProposedBy());
        return decide(actor, adjustment, "REJECTED", Checks.text(reason, "却下理由", 500));
    }

    public StockAdjustment cancel(Actor actor, Long id, int expectedVersion, String reason) {
        require(actor, "WAREHOUSE", "MANAGER");
        StockAdjustment adjustment = locked(id, expectedVersion);
        Checks.state(actor.hasRole("ADMIN") || actor.hasRole("MANAGER")
                || actor.getUserId().equals(adjustment.getProposedById()), "permission.denied",
                "申請者または管理者のみ取消できます。");
        return decide(actor, adjustment, "CANCELLED", Checks.text(reason, "取消理由", 500));
    }

    private StockAdjustment locked(Long id, int expectedVersion) {
        Checks.state(id != null, "validation.id", "在庫調整を指定してください。");
        Object[] snapshot = (Object[]) dao.query(
                "select a.warehouse.id,a.product.id from StockAdjustment a where a.id=:id",
                WholesaleDao.params("id", id)).uniqueResult();
        Checks.state(snapshot != null, "notFound", "在庫調整が見つかりません。");
        dao.lockReferences(Collections.singleton((Long) snapshot[0]), Collections.singleton((Long) snapshot[1]));
        StockAdjustment adjustment = dao.lock(StockAdjustment.class, id);
        Checks.version(adjustment.getVersion(), expectedVersion);
        state(adjustment.getStatus(), "PROPOSED");
        return adjustment;
    }

    private StockAdjustment decide(Actor actor, StockAdjustment adjustment, String status, String reason) {
        adjustment.setStatus(status);
        adjustment.setDecisionReason(reason);
        adjustment.setDecidedBy(actor.getLogin());
        adjustment.setDecidedAt(new Date());
        audit(actor, "ADJUSTMENT_" + status, adjustment, reason);
        dao.flush();
        return adjustment;
    }
}
