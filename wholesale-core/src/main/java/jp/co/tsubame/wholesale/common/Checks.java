package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;

public final class Checks {
    private Checks() {
    }

    public static void state(boolean condition, String code, String message) {
        if (!condition) {
            throw new BusinessException(code, message);
        }
    }

    public static String text(String value, String field, int maxLength) {
        if (value == null || value.trim().length() == 0) {
            throw new BusinessException("validation.required", field, field + "を入力してください。");
        }
        String result = value.trim();
        if (result.length() > maxLength || containsControl(result)) {
            throw new BusinessException("validation.text", field, field + "の長さまたは文字が不正です。");
        }
        return result;
    }

    public static String optionalText(String value, String field, int maxLength) {
        return value == null || value.trim().length() == 0 ? "" : text(value, field, maxLength);
    }

    public static String code(String value, String field) {
        String result = text(value, field, 30);
        state(result.matches("[A-Z0-9][A-Z0-9_-]{0,29}"), "validation.code",
                field + "は半角英大文字・数字・ハイフン・下線で入力してください。");
        return result;
    }

    public static int quantity(int value, String field) {
        state(value > 0 && value <= 1000000, "validation.quantity",
                field + "は1から1,000,000までの整数で入力してください。");
        return value;
    }

    public static BigDecimal money(BigDecimal value, String field, boolean allowZero) {
        state(value != null && value.signum() >= (allowZero ? 0 : 1)
                && value.compareTo(new BigDecimal("999999999999.99")) <= 0,
                "validation.money", field + "の金額が範囲外です。");
        state(value.stripTrailingZeros().scale() <= 2, "validation.money.scale",
                field + "は小数点以下2桁までです。");
        return value.setScale(2);
    }

    public static Date date(Date value, String field) {
        state(value != null, "validation.date", field + "を指定してください。");
        return Dates.day(value);
    }

    public static <T> Collection<T> nonempty(Collection<T> values, String field) {
        state(values != null && !values.isEmpty(), "validation.lines", field + "を1件以上入力してください。");
        state(values.size() <= 200, "validation.lines.limit", field + "は200件までです。");
        return values;
    }

    public static void version(int actual, int expected) {
        state(actual == expected, "concurrent.update", "他の処理により更新されました。再表示してから操作してください。");
    }

    private static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                return true;
            }
        }
        return false;
    }
}
