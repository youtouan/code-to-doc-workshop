package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Money() {
    }

    public static BigDecimal amount(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal tax(BigDecimal base, BigDecimal rate, String rounding) {
        RoundingMode mode;
        if ("DOWN".equals(rounding)) {
            mode = RoundingMode.DOWN;
        } else if ("UP".equals(rounding)) {
            mode = RoundingMode.UP;
        } else if ("HALF_UP".equals(rounding)) {
            mode = RoundingMode.HALF_UP;
        } else {
            throw new BusinessException("validation.rounding", "端数処理区分が不正です。");
        }
        return base.multiply(rate).setScale(0, mode).setScale(2);
    }

    public static BigDecimal rate(String category) {
        if ("STANDARD".equals(category)) {
            return new BigDecimal("0.1000");
        }
        if ("REDUCED".equals(category)) {
            return new BigDecimal("0.0800");
        }
        if ("EXEMPT".equals(category)) {
            return new BigDecimal("0.0000");
        }
        throw new BusinessException("validation.taxCategory", "税区分が不正です。");
    }
}
