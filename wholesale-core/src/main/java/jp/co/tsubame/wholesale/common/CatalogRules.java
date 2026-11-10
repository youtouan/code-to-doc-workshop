package jp.co.tsubame.wholesale.common;

import java.util.Date;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Warehouse;

/** Shared deterministic reference validation; all persistence stays in services. */
public final class CatalogRules {
    private CatalogRules() { }

    public static void customer(Customer input) {
        Checks.state(input != null, "validation.customer", "得意先を指定してください。");
        Checks.code(input.getCode(), "得意先コード");
        Checks.text(input.getName(), "得意先名", 120);
        Checks.money(input.getCreditLimit(), "与信限度額", true);
        closingDay(input.getClosingDay());
        paymentTerms(input.getPaymentTermDays());
        rounding(input.getTaxRounding());
        contact(input.getPostalCode(), input.getAddress(), input.getTelephone(), input.getNotes());
    }

    public static void product(Product input) {
        Checks.state(input != null, "validation.product", "商品を指定してください。");
        Checks.code(input.getCode(), "商品コード");
        Checks.text(input.getName(), "商品名", 120);
        Checks.text(input.getUnit(), "単位", 20);
        Checks.state("STANDARD".equals(input.getTaxCategory())
                || "REDUCED".equals(input.getTaxCategory()) || "EXEMPT".equals(input.getTaxCategory()),
                "validation.taxCategory", "税区分が不正です。");
        Checks.money(input.getListPrice(), "標準売価", true);
        Checks.money(input.getStandardCost(), "標準原価", true);
        Checks.quantity(input.getPackSize(), "入数");
        nonnegativeQuantity(input.getReorderPoint(), "発注点");
        nonnegativeQuantity(input.getReorderQuantity(), "標準発注数");
        Checks.state(input.getReorderQuantity() == 0 || input.getReorderQuantity() % input.getPackSize() == 0,
                "validation.reorderPack", "標準発注数は入数の倍数にしてください。");
        Checks.optionalText(input.getNotes(), "備考", 1000);
    }

    public static void warehouse(Warehouse input) {
        Checks.state(input != null, "validation.warehouse", "倉庫を指定してください。");
        Checks.code(input.getCode(), "倉庫コード");
        Checks.text(input.getName(), "倉庫名", 120);
        Checks.optionalText(input.getAddress(), "住所", 250);
    }

    public static void closingDay(int day) {
        Checks.state(day == 10 || day == 20 || day == 31,
                "validation.closingDay", "締日は10日・20日・月末から選択してください。");
    }

    public static void paymentTerms(int days) {
        Checks.state(days >= 0 && days <= 120, "validation.paymentTerms", "支払期日は0から120日です。");
    }

    public static void rounding(String value) {
        Checks.state("DOWN".equals(value) || "UP".equals(value) || "HALF_UP".equals(value),
                "validation.taxRounding", "税端数処理が不正です。");
    }

    public static void contact(String postalCode, String address, String telephone, String notes) {
        String postal = Checks.optionalText(postalCode, "郵便番号", 12);
        Checks.state(postal.length() == 0 || postal.matches("[0-9]{3}-?[0-9]{4}"),
                "validation.postalCode", "郵便番号は7桁で入力してください。");
        Checks.optionalText(address, "住所", 250);
        String phone = Checks.optionalText(telephone, "電話番号", 30);
        Checks.state(phone.length() == 0 || phone.matches("[+0-9 ()-]{3,30}"),
                "validation.telephone", "電話番号が不正です。");
        Checks.optionalText(notes, "備考", 1000);
    }

    public static int nonnegativeQuantity(int value, String field) {
        Checks.state(value >= 0 && value <= 1000000,
                "validation.quantity", field + "は0から1,000,000までです。");
        return value;
    }

    public static void interval(Date from, Date to) {
        Checks.date(from, "適用開始日");
        Checks.state(to == null || !Dates.day(to).before(Dates.day(from)),
                "validation.interval", "適用終了日は適用開始日以降にしてください。");
    }

    public static boolean overlaps(Date from, Date to, Date otherFrom, Date otherTo) {
        interval(from, to);
        interval(otherFrom, otherTo);
        return (otherTo == null || !Dates.day(from).after(Dates.day(otherTo)))
                && (to == null || !Dates.day(otherFrom).after(Dates.day(to)));
    }
}
