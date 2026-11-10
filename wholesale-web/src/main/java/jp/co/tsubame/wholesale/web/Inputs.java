package jp.co.tsubame.wholesale.web;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.web.form.BaseForm;

public final class Inputs {
    public static final int MAX_LINES = 100;
    public static final int MAX_PASSWORD_LENGTH = 128;
    private Inputs() { }

    public static String text(String value, String field, int maximum, boolean required) {
        String result = value == null ? "" : value.trim();
        if ((required && result.length() == 0) || result.length() > maximum || result.indexOf('\0') >= 0) {
            throw invalid(field, field + "を" + (required ? "1～" : "0～") + maximum + "文字で入力してください。");
        }
        return result;
    }

    public static Long id(String value, String field) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw invalid(field, field + "の指定が不正です。");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw invalid(field, field + "の指定が不正です。");
        }
    }

    public static Long optionalId(String value, String field) {
        return value == null || value.length() == 0 ? null : id(value, field);
    }

    public static int integer(String value, String field, int minimum, int maximum) {
        if (value == null || !value.matches("-?(0|[1-9][0-9]{0,9})")) {
            throw invalid(field, field + "は整数で入力してください。");
        }
        try {
            int result = Integer.parseInt(value);
            if (result < minimum || result > maximum) {
                throw invalid(field, field + "は" + minimum + "～" + maximum + "で入力してください。");
            }
            return result;
        } catch (NumberFormatException ex) {
            throw invalid(field, field + "の桁数が大きすぎます。");
        }
    }

    public static int quantity(String value, String field, boolean allowZero) {
        return integer(value, field, allowZero ? 0 : 1, 100000000);
    }

    public static BigDecimal money(String value, String field, boolean optional) {
        if (optional && (value == null || value.length() == 0)) {
            return null;
        }
        if (value == null || !value.matches("(0|[1-9][0-9]{0,11})(\\.[0-9]{1,2})?")) {
            throw invalid(field, field + "は0以上、小数2桁以内の金額で入力してください（桁区切り不可）。");
        }
        return new BigDecimal(value);
    }

    public static Date date(String value, String field, boolean optional) {
        if (optional && (value == null || value.length() == 0)) {
            return null;
        }
        try {
            return Dates.parse(value);
        } catch (BusinessException ex) {
            throw invalid(field, field + "は実在する日付をyyyy-MM-dd形式で入力してください。");
        }
    }

    public static String choice(String value, String field, String... choices) {
        for (String choice : choices) {
            if (choice.equals(value)) {
                return value;
            }
        }
        throw invalid(field, field + "の選択が不正です。");
    }

    public static boolean bool(String value, String field) {
        if ("true".equals(value)) { return true; }
        if (value == null || "".equals(value) || "false".equals(value)) { return false; }
        throw invalid(field, field + "の選択が不正です。");
    }

    public static void arrays(String[] keys, String[]... columns) {
        arrays(MAX_LINES, keys, columns);
    }
    public static void arrays(int maximum, String[] keys, String[]... columns) {
        if (keys == null || keys.length == 0 || keys.length > maximum) {
            throw invalid("明細", "明細は1～" + maximum + "行で指定してください。");
        }
        for (String[] column : columns) {
            if (column == null || column.length != keys.length) {
                throw invalid("明細", "明細の列数が一致しません。画面を再表示してください。");
            }
        }
    }

    public static Set<Long> uniqueIds(String[] values, String field) {
        Set<Long> result = new HashSet<Long>();
        for (String value : values) {
            if (!result.add(id(value, field))) {
                throw invalid(field, "同じ明細が重複しています。");
            }
        }
        return result;
    }

    public static Search search(BaseForm form) {
        Search search = new Search();
        search.setText(text(form.getText(), "検索語", 100, false));
        search.setStatus(text(form.getStatus(), "状態", 40, false));
        search.setCustomerId(optionalId(form.getCustomerId(), "得意先"));
        search.setWarehouseId(optionalId(form.getWarehouseId(), "倉庫"));
        search.setFrom(date(form.getFrom(), "開始日", true));
        search.setTo(date(form.getTo(), "終了日", true));
        if (search.getFrom() != null && search.getTo() != null && search.getFrom().after(search.getTo())) {
            throw invalid("終了日", "終了日は開始日以降で指定してください。");
        }
        search.setPage(integer(form.getPageNumber(), "ページ", 1, 100000));
        search.setSize(integer(form.getSize(), "表示件数", 1, 100));
        return search;
    }

    public static BusinessException invalid(String field, String message) {
        return new BusinessException("validation.web", field, message);
    }
}
