package jp.co.tsubame.wholesale.dao;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Money;

/** A rate bucket reverses the difference between the original and remaining tax. */
public class BillingAmounts {
    private final Map<BigDecimal, BigDecimal> bases = new TreeMap<BigDecimal, BigDecimal>();
    private final String rounding;

    public BillingAmounts(String rounding) {
        Money.tax(Money.ZERO, BigDecimal.ZERO, rounding);
        this.rounding = rounding;
    }

    public void add(BigDecimal net, BigDecimal rate) {
        Checks.state(net != null && rate != null && net.signum() >= 0 && rate.signum() >= 0
                && rate.compareTo(BigDecimal.ONE) <= 0, "billing.amount", "請求金額または税率が不正です。");
        BigDecimal existing = bases.get(rate);
        bases.put(rate, (existing == null ? Money.ZERO : existing).add(net));
    }

    public BigDecimal net() {
        BigDecimal result = Money.ZERO;
        for (BigDecimal amount : bases.values()) {
            result = result.add(amount);
        }
        return result;
    }

    public BigDecimal tax() {
        BigDecimal result = Money.ZERO;
        for (Map.Entry<BigDecimal, BigDecimal> bucket : bases.entrySet()) {
            result = result.add(Money.tax(bucket.getValue(), bucket.getKey(), rounding));
        }
        return result;
    }

    public BigDecimal gross() {
        return net().add(tax());
    }

    public BigDecimal cumulativeCreditTax(BillingAmounts returned) {
        BigDecimal result = Money.ZERO;
        for (Map.Entry<BigDecimal, BigDecimal> bucket : returned.bases.entrySet()) {
            BigDecimal original = bases.get(bucket.getKey());
            Checks.state(original != null && bucket.getValue().compareTo(original) <= 0,
                    "credit.overReturn", "返品額が元の請求額を超えています。");
            result = result.add(Money.tax(original, bucket.getKey(), rounding)
                    .subtract(Money.tax(original.subtract(bucket.getValue()), bucket.getKey(), rounding)));
        }
        return result;
    }
}
