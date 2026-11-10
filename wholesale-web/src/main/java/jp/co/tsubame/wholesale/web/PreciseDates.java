package jp.co.tsubame.wholesale.web;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class PreciseDates {
    private PreciseDates() { }
    public static Date parse(String value, String field, boolean optional) {
        if (optional && (value == null || value.length() == 0)) { return null; }
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2}(\\.[0-9]{1,3})?)?")) {
            throw Inputs.invalid(field, field + "は日本時間の日時で入力してください。");
        }
        String normalized = value;
        if (normalized.length() == 16) { normalized += ":00"; }
        if (normalized.length() == 19) { normalized += ".000"; }
        while (normalized.length() < 23) { normalized += "0"; }
        try {
            return formatter().parse(normalized);
        } catch (ParseException ex) {
            throw Inputs.invalid(field, field + "に実在する日時を入力してください。");
        }
    }
    public static String format(Date value) { return value == null ? "" : formatter().format(value); }
    private static SimpleDateFormat formatter() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT);
        format.setLenient(false); format.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo")); return format;
    }
}
