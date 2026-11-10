package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.entity.SupplierProduct;

public final class PurchasingRules {
    private PurchasingRules() { }

    public static void supplier(Supplier input) {
        Checks.state(input != null, "validation.supplier", "仕入先を指定してください。");
        Checks.code(input.getCode(), "仕入先コード");
        Checks.text(input.getName(), "仕入先名", 120);
        CatalogRules.closingDay(input.getClosingDay());
        CatalogRules.paymentTerms(input.getPaymentTermDays());
        CatalogRules.rounding(input.getTaxRounding());
        leadTime(input.getDefaultLeadTimeDays());
        Checks.money(input.getMinimumOrderAmount(), "最低発注金額", true);
        CatalogRules.contact(input.getPostalCode(), input.getAddress(), input.getTelephone(), input.getNotes());
        Checks.optionalText(input.getOrderingInstructions(), "発注条件", 1000);
    }

    public static void supplierProduct(SupplierProduct input) {
        Checks.state(input != null && input.getSupplier() != null && input.getProduct() != null,
                "validation.supplierProduct", "仕入先と商品を指定してください。");
        CatalogRules.interval(input.getValidFrom(), input.getValidTo());
        Checks.quantity(input.getMinimumQuantity(), "最低発注数");
        Checks.quantity(input.getOrderPackSize(), "仕入入数");
        Checks.state(input.getMinimumQuantity() % input.getOrderPackSize() == 0,
                "purchasing.minimumPack", "最低発注数は仕入入数の倍数にしてください。");
        leadTime(input.getLeadTimeDays());
        Checks.money(input.getUnitCost(), "仕入単価", true);
        Checks.optionalText(input.getSupplierProductCode(), "仕入先商品コード", 60);
        Checks.optionalText(input.getNotes(), "備考", 1000);
    }

    public static void leadTime(int days) {
        Checks.state(days >= 0 && days <= 365, "validation.leadTime", "調達日数は0から365日です。");
    }

    public static int roundToPack(long required, int minimum, int pack) {
        Checks.quantity(pack, "仕入入数");
        Checks.quantity(minimum, "最低発注数");
        Checks.state(required >= 0 && required <= 1000000L,
                "purchasing.reorderLimit", "発注提案数が1,000,000を超えています。分割して発注してください。");
        long raw = Math.max(required, (long) minimum);
        long rounded = ((raw + pack - 1L) / pack) * pack;
        Checks.state(rounded <= 1000000L, "purchasing.reorderLimit", "入数丸め後の数量が上限を超えています。");
        return (int) rounded;
    }

    public static BigDecimal lineAmount(BigDecimal unitCost, int quantity) {
        BigDecimal amount = Checks.money(unitCost, "仕入単価", true)
                .multiply(BigDecimal.valueOf(Checks.quantity(quantity, "発注数")));
        return Checks.money(amount, "明細金額", true);
    }

    public static List<PurchasingReceiptLineCommand> receiptLines(PurchasingReceiptCommand command) {
        Checks.state(command != null, "validation.receipt", "入荷情報を指定してください。");
        Checks.text(command.getRequestKey(), "要求キー", 100);
        Checks.state(command.getOrderId() != null, "validation.order", "発注を指定してください。");
        Checks.date(command.getReceiptDate(), "入荷日");
        Checks.optionalText(command.getSupplierDeliveryNumber(), "仕入先納品書番号", 60);
        Checks.optionalText(command.getNotes(), "備考", 1000);
        Checks.nonempty(command.getLines(), "入荷明細");
        List<PurchasingReceiptLineCommand> result = new ArrayList<PurchasingReceiptLineCommand>();
        Set<Long> ids = new HashSet<Long>();
        for (PurchasingReceiptLineCommand line : command.getLines()) {
            Checks.state(line != null && line.getOrderLineId() != null,
                    "validation.receiptLine", "発注明細を指定してください。");
            Checks.state(ids.add(line.getOrderLineId()), "purchasing.duplicateReceiptLine", "発注明細が重複しています。");
            CatalogRules.nonnegativeQuantity(line.getAcceptedQuantity(), "良品数");
            CatalogRules.nonnegativeQuantity(line.getRejectedQuantity(), "不良品数");
            Checks.quantity(line.getAcceptedQuantity() + line.getRejectedQuantity(), "入荷数");
            if (line.getRejectedQuantity() > 0) {
                Checks.text(line.getRejectionReason(), "不良理由", 250);
            } else {
                Checks.optionalText(line.getRejectionReason(), "不良理由", 250);
            }
            Checks.optionalText(line.getNotes(), "明細備考", 250);
            result.add(line);
        }
        Collections.sort(result, new Comparator<PurchasingReceiptLineCommand>() {
            public int compare(PurchasingReceiptLineCommand a, PurchasingReceiptLineCommand b) {
                return a.getOrderLineId().compareTo(b.getOrderLineId());
            }
        });
        return result;
    }

    public static String receiptHash(PurchasingReceiptCommand command) {
        List<PurchasingReceiptLineCommand> lines = receiptLines(command);
        StringBuilder canonical = new StringBuilder();
        field(canonical, command.getOrderId().toString());
        field(canonical, Dates.format(Dates.day(command.getReceiptDate())));
        field(canonical, Checks.optionalText(command.getSupplierDeliveryNumber(), "仕入先納品書番号", 60));
        field(canonical, Checks.optionalText(command.getNotes(), "備考", 1000));
        for (PurchasingReceiptLineCommand line : lines) {
            field(canonical, line.getOrderLineId().toString());
            field(canonical, Integer.toString(line.getAcceptedQuantity()));
            field(canonical, Integer.toString(line.getRejectedQuantity()));
            field(canonical, Checks.optionalText(line.getRejectionReason(), "不良理由", 250));
            field(canonical, Checks.optionalText(line.getNotes(), "明細備考", 250));
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    canonical.toString().getBytes(Charset.forName("UTF-8")));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : bytes) {
                int unsigned = value & 255;
                if (unsigned < 16) { hex.append('0'); }
                hex.append(Integer.toHexString(unsigned));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", ex);
        }
    }

    private static void field(StringBuilder builder, String text) {
        builder.append(text.length()).append(':').append(text).append(';');
    }
}
