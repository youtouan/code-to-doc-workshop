package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import jp.co.tsubame.wholesale.dao.BillingAmounts;
import jp.co.tsubame.wholesale.entity.OrderAmendment;
import jp.co.tsubame.wholesale.entity.OrderAmendmentLine;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;

public final class OrderAmendmentRules {
    private OrderAmendmentRules() { }

    public static void eligible(SalesOrder order) {
        Checks.state(OrderStates.isApproved(order.getStatus()), "amendment.orderState",
                "承認済み・未完了の受注のみ変更できます。");
        Checks.state(order.getCustomer().isActive() && !order.getCustomer().isOnHold(),
                "order.customer", "得意先が無効または取引保留中です。");
        Checks.state(order.getWarehouse().isActive(), "order.warehouse", "倉庫が無効です。");
        for (SalesOrderLine line : order.getLines()) {
            Checks.state(line.getProduct().isActive(), "order.product", "受注商品が無効です。");
        }
    }

    public static Map<Long, Integer> targets(OrderAmendment amendment) {
        Map<Long, Integer> result = new HashMap<Long, Integer>();
        for (OrderAmendmentLine line : amendment.getLines()) {
            Checks.state(result.put(line.getOrderLine().getId(), Integer.valueOf(line.getTargetQuantity())) == null,
                    "amendment.duplicateLine", "変更明細が重複しています。");
        }
        return result;
    }

    public static void validateTargets(SalesOrder order, Map<Long, Integer> targets) {
        Map<Long, Integer> remaining = new HashMap<Long, Integer>(targets);
        for (SalesOrderLine line : order.getLines()) {
            Integer target = remaining.remove(line.getId());
            if (target == null) { continue; }
            Checks.quantity(target.intValue(), "変更後数量");
            Checks.state(target.intValue() >= line.getShippedQuantity() + line.getCancelledQuantity(),
                    "amendment.fulfilledQuantity", "出荷済み・取消済み数量より少なく変更できません。");
            Checks.state(target.intValue() % line.getPackSize() == 0,
                    "amendment.pack", "変更後数量は受注時入数の倍数にしてください。");
        }
        Checks.state(remaining.isEmpty(), "amendment.foreignLine", "別の受注明細は変更できません。");
    }

    public static TaxAmounts totals(SalesOrder order, Map<Long, Integer> targets) {
        TaxAmounts result = new TaxAmounts(order.getTaxRounding());
        for (SalesOrderLine line : order.getLines()) {
            Integer target = targets.get(line.getId());
            result.add(Money.amount(line.getUnitPrice(), target == null ? line.getQuantity() : target.intValue()),
                    line.getTaxRate());
        }
        QuotationRules.amounts(result);
        return result;
    }

    public static BigDecimal remainingExposure(SalesOrder order, Map<Long, Integer> targets) {
        BillingAmounts result = new BillingAmounts(order.getTaxRounding());
        for (SalesOrderLine line : order.getLines()) {
            Integer target = targets.get(line.getId());
            int quantity = (target == null ? line.getQuantity() : target.intValue())
                    - line.getShippedQuantity() - line.getCancelledQuantity();
            Checks.state(quantity >= 0, "amendment.fulfilledQuantity", "受注残数が負数になります。");
            result.add(Money.amount(line.getUnitPrice(), quantity), line.getTaxRate());
        }
        return result.gross();
    }

    public static String fingerprint(SalesOrder order) {
        QuotationFingerprint hash = new QuotationFingerprint().add(order.getId()).add(order.getVersion())
                .add(order.getCustomer().getId()).add(order.getWarehouse().getId()).add(order.getStatus())
                .add(order.getOrderDate()).add(order.getRequestedDate()).add(order.getTaxRounding())
                .add(order.getNetAmount()).add(order.getTaxAmount()).add(order.getDeliveryAddress())
                .add(order.getExternalReference()).add(order.getNotes());
        ArrayList<SalesOrderLine> lines = new ArrayList<SalesOrderLine>(order.getLines());
        Collections.sort(lines, new Comparator<SalesOrderLine>() {
            public int compare(SalesOrderLine a, SalesOrderLine b) { return a.getId().compareTo(b.getId()); }
        });
        for (SalesOrderLine line : lines) {
            hash.add(line.getId()).add(line.getVersion()).add(line.getProduct().getId()).add(line.getLineNumber())
                    .add(line.getQuantity()).add(line.getAllocatedQuantity()).add(line.getShippedQuantity())
                    .add(line.getCancelledQuantity()).add(line.getUnitPrice()).add(line.getTaxRate())
                    .add(line.getPackSize()).add(line.getProductCode()).add(line.getProductName())
                    .add(line.getUnit()).add(line.getPriceReason());
        }
        return hash.finish();
    }

    public static void unchanged(SalesOrder order, OrderAmendment amendment) {
        Checks.state(order.getVersion() == amendment.getBaseOrderVersion()
                && fingerprint(order).equals(amendment.getSourceFingerprint()),
                "amendment.stale", "申請後に受注・引当・出荷が更新されました。取消して再申請してください。");
    }
}
