package jp.co.tsubame.wholesale.common;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class StockControlRules {
    private StockControlRules() { }

    public static Map<Long, Integer> quantities(List<StockControlLineCommand> lines) {
        Checks.nonempty(lines, "移動明細");
        Map<Long, Integer> result = new TreeMap<Long, Integer>();
        for (StockControlLineCommand line : lines) {
            Checks.state(line != null && line.getProductId() != null && line.getProductId() > 0,
                    "stockControl.product", "商品を指定してください。");
            Checks.quantity(line.getQuantity(), "移動数量");
            Checks.state(!result.containsKey(line.getProductId()), "stockControl.duplicateProduct",
                    "同じ商品を複数行に指定できません。");
            result.put(line.getProductId(), line.getQuantity());
        }
        return result;
    }

    public static int adjustment(int value) {
        Checks.state(value != 0 && value >= -1000000 && value <= 1000000,
                "stockControl.adjustmentQuantity", "調整数量は0以外の±1,000,000以内です。");
        return value;
    }

    public static int counted(Integer value) {
        Checks.state(value != null && value >= 0 && value <= 2000000000,
                "stockControl.countQuantity", "実棚数量は0から2,000,000,000です。未入力と0は異なります。");
        return value;
    }

    public static void independent(Actor actor, Long proposerId, String proposerLogin) {
        Checks.state(!actor.getUserId().equals(proposerId) && !actor.getLogin().equals(proposerLogin),
                "approval.self", "申請者と承認者は別の担当者でなければなりません。");
    }

    public static String fingerprint(Long transferId, String kind, String note, Map<Long, Integer> lines) {
        StringBuilder detail = new StringBuilder();
        for (Map.Entry<Long, Integer> entry : new TreeMap<Long, Integer>(lines).entrySet()) {
            detail.append(entry.getKey()).append(':').append(entry.getValue()).append(';');
        }
        return Fingerprints.of(String.valueOf(transferId), kind, note, detail.toString());
    }
}
