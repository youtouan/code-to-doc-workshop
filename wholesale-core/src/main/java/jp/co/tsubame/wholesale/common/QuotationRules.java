package jp.co.tsubame.wholesale.common;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import jp.co.tsubame.wholesale.entity.Quotation;
import jp.co.tsubame.wholesale.entity.QuotationLine;
import jp.co.tsubame.wholesale.entity.QuotationRevision;

public final class QuotationRules {
    private QuotationRules() { }

    public static Long actorId(Actor actor) {
        Checks.state(actor != null && actor.getUserId() != null && actor.getUserId().longValue() > 0,
                "quotation.actor", "有効な利用者IDが必要です。");
        return actor.getUserId();
    }

    public static void command(QuotationCommand input) {
        Checks.state(input != null && input.getCustomerId() != null && input.getWarehouseId() != null,
                "quotation.input", "得意先と倉庫を指定してください。");
        Date date = Checks.date(input.getQuoteDate(), "見積日");
        Date until = Checks.date(input.getValidUntil(), "有効期限");
        Date requested = Checks.date(input.getRequestedDate(), "納期");
        Checks.state(!date.after(Dates.today()), "quotation.future", "未来日の見積は作成できません。");
        Checks.state(!until.before(date) && !until.after(Dates.addDays(date, 180)),
                "quotation.validity", "有効期限は見積日から180日以内です。");
        Checks.state(!requested.before(date) && !requested.after(Dates.addDays(date, 365)),
                "quotation.deliveryDate", "納期は見積日から365日以内です。");
        Checks.optionalText(input.getDeliveryAddress(), "納入先", 300);
        Checks.optionalText(input.getExternalReference(), "客先参照番号", 80);
        Checks.optionalText(input.getNotes(), "備考", 1000);
        Checks.nonempty(input.getLines(), "見積明細");
        Set<Long> products = new HashSet<Long>();
        for (QuotationLineCommand line : input.getLines()) {
            Checks.state(line != null && line.getProductId() != null && products.add(line.getProductId()),
                    "quotation.product", "明細商品を重複せず指定してください。");
            Checks.quantity(line.getQuantity(), "見積数量");
            if (line.getNegotiatedUnitPrice() != null) {
                Checks.money(line.getNegotiatedUnitPrice(), "交渉単価", true);
                Checks.text(line.getNegotiationReason(), "交渉理由", 300);
            } else {
                Checks.optionalText(line.getNegotiationReason(), "交渉理由", 300);
            }
        }
    }

    public static void active(Quotation quotation) {
        Checks.state(quotation.getCustomer().isActive() && !quotation.getCustomer().isOnHold(),
                "quotation.customer", "得意先が無効または取引保留中です。");
        Checks.state(quotation.getWarehouse().isActive(), "quotation.warehouse", "見積の倉庫が無効です。");
        for (QuotationLine line : quotation.getLines()) {
            Checks.state(line.getProduct().isActive(), "quotation.product", "見積の商品が無効です。");
            Checks.state(line.getQuantity() % line.getProduct().getPackSize() == 0,
                    "quotation.packChanged", "現在の商品入数を満たしていません。改訂して再承認してください。");
        }
    }

    public static void current(Quotation quotation) {
        Checks.state(!Dates.today().after(quotation.getValidUntil()),
                "quotation.expired", "見積の有効期限が過ぎています。改訂してください。");
    }

    public static void amounts(TaxAmounts amounts) {
        Checks.state(amounts.getNetAmount().signum() >= 0 && amounts.getTaxAmount().signum() >= 0
                && amounts.getTotalAmount().compareTo(new BigDecimal("9999999999999999.99")) <= 0,
                "quotation.amountOverflow", "税込金額が保存可能な上限を超えています。");
    }

    public static String fingerprint(QuotationRevision revision) {
        QuotationFingerprint hash = new QuotationFingerprint().add(revision.getQuotation().getCustomer().getId())
                .add(revision.getWarehouse().getId()).add(revision.getRevisionNumber())
                .add(revision.getCustomerName()).add(revision.getQuoteDate()).add(revision.getValidUntil())
                .add(revision.getRequestedDate()).add(revision.getDeliveryAddress()).add(revision.getExternalReference())
                .add(revision.getNotes()).add(revision.getTaxRounding()).add(revision.getNetAmount())
                .add(revision.getTaxAmount()).add(revision.getAuthoredById());
        for (QuotationLine line : revision.getLines()) {
            hash.add(line.getLineNumber()).add(line.getProduct().getId()).add(line.getProductCode())
                    .add(line.getProductName()).add(line.getUnit()).add(line.getPackSize()).add(line.getQuantity())
                    .add(line.getCatalogUnitPrice()).add(line.getUnitPrice()).add(line.getTaxRate())
                    .add(line.isNegotiated()).add(line.getNegotiationReason());
        }
        return hash.finish();
    }

    public static void integrity(Quotation quotation) {
        QuotationRevision revision = quotation.getCurrentRevision();
        Checks.nonempty(revision.getLines(), "見積明細");
        Checks.state(quotation.getWarehouse().getId().equals(revision.getWarehouse().getId())
                && fingerprint(revision).equals(revision.getFingerprint()),
                "quotation.snapshotChanged", "見積スナップショットが一致しません。改訂して再承認してください。");
        TaxAmounts totals = new TaxAmounts(revision.getTaxRounding());
        for (QuotationLine line : revision.getLines()) {
            Checks.quantity(line.getQuantity(), "見積数量");
            Checks.money(line.getUnitPrice(), "見積単価", true);
            Checks.state(line.getPackSize() > 0 && line.getQuantity() % line.getPackSize() == 0,
                    "quotation.pack", "保存済み見積の入数が不正です。");
            totals.add(line.getNetAmount(), line.getTaxRate());
        }
        amounts(totals);
        Checks.state(totals.getNetAmount().compareTo(revision.getNetAmount()) == 0
                && totals.getTaxAmount().compareTo(revision.getTaxAmount()) == 0,
                "quotation.snapshotChanged", "保存済み見積金額が一致しません。");
    }

    public static void approvedIntegrity(Quotation quotation) {
        integrity(quotation);
        Checks.state(quotation.getApprovedById() != null && quotation.getApprovedAt() != null
                && quotation.getSubmittedById() != null
                && !quotation.getApprovedById().equals(quotation.getCreatedById())
                && !quotation.getApprovedById().equals(quotation.getSubmittedById())
                && !quotation.getApprovedById().equals(quotation.getCurrentRevision().getAuthoredById())
                && quotation.getCurrentRevision().getFingerprint().equals(quotation.getApprovedFingerprint()),
                "quotation.approvalChanged", "現在版の独立した承認記録がありません。");
    }
}
