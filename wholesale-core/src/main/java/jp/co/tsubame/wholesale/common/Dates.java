package jp.co.tsubame.wholesale.common;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class Dates {
    private static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Tokyo");

    private Dates() {
    }

    public static Date today() {
        return day(new Date());
    }

    public static Date day(Date date) {
        if (date == null) {
            return null;
        }
        Calendar calendar = calendar(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return new java.sql.Date(calendar.getTimeInMillis());
    }

    public static Date parse(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new BusinessException("validation.date", "日付はyyyy-MM-dd形式で入力してください。");
        }
        SimpleDateFormat format = formatter();
        format.setLenient(false);
        try {
            return day(format.parse(value));
        } catch (ParseException ex) {
            throw new BusinessException("validation.date", "存在しない日付です。");
        }
    }

    public static String format(Date date) {
        return date == null ? "" : formatter().format(date);
    }

    public static Date addDays(Date date, int days) {
        Calendar calendar = calendar(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return day(calendar.getTime());
    }

    public static Date addMonths(Date date, int months) {
        Calendar calendar = calendar(date);
        calendar.add(Calendar.MONTH, months);
        return day(calendar.getTime());
    }

    public static Date monthEnd(Date date) {
        Calendar calendar = calendar(date);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        return day(calendar.getTime());
    }

    public static Date closingDate(Date inMonth, int closingDay) {
        Checks.state(closingDay == 10 || closingDay == 20 || closingDay == 31,
                "validation.closingDay", "締日は10日・20日・末日から選択してください。");
        Calendar calendar = calendar(inMonth);
        calendar.set(Calendar.DAY_OF_MONTH, Math.min(closingDay, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
        return day(calendar.getTime());
    }

    public static boolean sameDay(Date first, Date second) {
        return first != null && second != null && day(first).equals(day(second));
    }

    public static Calendar calendar(Date date) {
        Calendar calendar = Calendar.getInstance(ZONE, Locale.JAPAN);
        calendar.setTime(date);
        return calendar;
    }

    private static SimpleDateFormat formatter() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setTimeZone(ZONE);
        return format;
    }
}
