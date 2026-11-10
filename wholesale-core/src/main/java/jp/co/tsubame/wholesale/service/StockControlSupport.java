package jp.co.tsubame.wholesale.service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.dao.InventoryLedger;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Warehouse;

public abstract class StockControlSupport extends BaseService {
    protected InventoryLedger ledger;

    public void setLedger(InventoryLedger ledger) { this.ledger = ledger; }

    protected void reader(Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER", "SALES", "BILLING", "BATCH");
    }

    protected Warehouse activeWarehouse(Long id) {
        Warehouse warehouse = dao.get(Warehouse.class, id);
        Checks.state(warehouse.isActive(), "stockControl.warehouseInactive", "利用停止中の倉庫です。");
        return warehouse;
    }

    protected Product activeProduct(Long id) {
        Product product = dao.get(Product.class, id);
        Checks.state(product.isActive(), "stockControl.productInactive", "利用停止中の商品です。");
        return product;
    }

    protected void state(String actual, String expected) {
        Checks.state(expected.equals(actual), "stockControl.state", "現在の伝票状態では実行できません。");
    }

    protected Date nextUpdate(Date previous) {
        return new Date(Math.max(System.currentTimeMillis(), previous == null ? 0L : previous.getTime() + 1L));
    }

    protected String initialNumber() { return "NEW-" + UUID.randomUUID().toString(); }

    protected <T> Page<T> documents(String entity, String dateProperty, Search search, boolean transfer) {
        Checks.state(search != null, "validation.search", "検索条件が必要です。");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        StringBuilder filter = new StringBuilder(" from ").append(entity).append(" d where 1=1");
        if (search.getWarehouseId() != null) {
            filter.append(transfer
                    ? " and (d.sourceWarehouse.id=:warehouse or d.destinationWarehouse.id=:warehouse)"
                    : " and d.warehouse.id=:warehouse");
            parameters.put("warehouse", search.getWarehouseId());
        }
        if (search.getStatus().length() > 0) {
            filter.append(" and d.status=:status");
            parameters.put("status", Checks.text(search.getStatus(), "状態", 30));
        }
        if (search.getText().length() > 0) {
            filter.append(" and d.number like :text escape '!'");
            parameters.put("text", search.getLikeText());
        }
        if (search.getFrom() != null) {
            filter.append(" and d.").append(dateProperty).append(">=:from");
            parameters.put("from", search.getFrom());
        }
        if (search.getTo() != null) {
            filter.append(" and d.").append(dateProperty).append("<:until");
            parameters.put("until", Dates.addDays(search.getTo(), 1));
        }
        return dao.page("select d" + filter + " order by d.id desc",
                "select count(d.id)" + filter, parameters, search);
    }
}
