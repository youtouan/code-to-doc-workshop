package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class TaxAmounts {
    private final Map<BigDecimal, BigDecimal> bases = new TreeMap<BigDecimal, BigDecimal>();
    private final String rounding;

    public TaxAmounts(String rounding) {
        this.rounding = rounding;
    }

    public void add(BigDecimal net, BigDecimal rate) {
        Checks.state(net != null && rate != null, "tax.missing", "税計算の金額・税率が不足しています。");
        BigDecimal previous = bases.get(rate);
        bases.put(rate, (previous == null ? Money.ZERO : previous).add(net));
    }

    public BigDecimal getNetAmount() {
        BigDecimal total = Money.ZERO;
        for (BigDecimal base : bases.values()) {
            total = total.add(base);
        }
        return total;
    }

    public BigDecimal getTaxAmount() {
        BigDecimal total = Money.ZERO;
        for (Map.Entry<BigDecimal, BigDecimal> bucket : bases.entrySet()) {
            total = total.add(Money.tax(bucket.getValue(), bucket.getKey(), rounding));
        }
        return total;
    }

    public BigDecimal getTotalAmount() {
        return getNetAmount().add(getTaxAmount());
    }

    public Map<BigDecimal, BigDecimal> getBases() {
        return Collections.unmodifiableMap(bases);
    }
}
