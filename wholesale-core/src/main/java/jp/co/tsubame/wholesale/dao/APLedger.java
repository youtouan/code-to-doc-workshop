package jp.co.tsubame.wholesale.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Fingerprints;
import jp.co.tsubame.wholesale.common.Money;
import jp.co.tsubame.wholesale.common.TaxAmounts;
import jp.co.tsubame.wholesale.entity.APCredit;
import jp.co.tsubame.wholesale.entity.APCreditLine;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.APInvoiceLine;
import jp.co.tsubame.wholesale.entity.APMatch;
import jp.co.tsubame.wholesale.entity.Supplier;
import org.hibernate.Hibernate;

public class APLedger {
    private final WholesaleDao dao;

    public APLedger(WholesaleDao dao) { this.dao = dao; }

    public static String reference(String value, String field) {
        return Checks.text(Checks.text(value, field, 80).toUpperCase(Locale.ROOT), field, 80);
    }

    public static Date pastDate(Date value, String field) {
        Date date = Checks.date(value, field);
        Checks.state(!date.after(Dates.today()), "ap.futureDate", "未来の日付は指定できません。");
        return date;
    }

    public static BigDecimal rate(BigDecimal value) {
        Checks.state(value != null && value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0
                && value.stripTrailingZeros().scale() <= 4, "ap.taxRate", "税率は0から1、小数4桁までです。");
        return value.setScale(4);
    }

    public BigDecimal sum(String hql, Map<String, ?> parameters) {
        BigDecimal value = (BigDecimal) dao.query(hql, parameters).uniqueResult();
        return value == null ? Money.ZERO : value;
    }

    public APInvoice lockInvoice(Long id) {
        Long supplierId = (Long) dao.query("select i.supplier.id from APInvoice i where i.id=:id",
                WholesaleDao.params("id", id)).uniqueResult();
        dao.lock(Supplier.class, supplierId);
        return dao.lock(APInvoice.class, id);
    }

    public APCredit lockCredit(Long id) {
        Long invoiceId = (Long) dao.query("select c.invoice.id from APCredit c where c.id=:id",
                WholesaleDao.params("id", id)).uniqueResult();
        lockInvoice(invoiceId);
        return dao.lock(APCredit.class, id);
    }

    public APInvoice detail(APInvoice invoice) {
        Hibernate.initialize(invoice.getLines());
        for (APInvoiceLine line : invoice.getLines()) { Hibernate.initialize(line.getMatches()); }
        return invoice;
    }

    public APCredit detail(APCredit credit) {
        Hibernate.initialize(credit.getLines());
        return credit;
    }

    public void reconcile(APInvoice invoice) {
        Map<String, Object> parameters = WholesaleDao.params("invoice", invoice);
        BigDecimal paid = sum("select sum(l.amount) from APPaymentLine l where l.invoice=:invoice and l.status='ACTIVE'", parameters);
        BigDecimal credited = sum("select sum(c.totalAmount) from APCredit c where c.invoice=:invoice and c.status='POSTED'", parameters);
        Checks.state(paid.signum() >= 0 && credited.signum() >= 0
                && paid.add(credited).compareTo(invoice.getTotalAmount()) <= 0,
                "ap.ledgerBalance", "支払・値引額が請求額を超えています。");
        invoice.setPaidAmount(paid);
        invoice.setCreditedAmount(credited);
    }

    public static void calculate(APInvoice invoice) {
        TaxAmounts amounts = new TaxAmounts(invoice.getTaxRounding());
        BigDecimal variance = Money.ZERO;
        BigDecimal absolute = Money.ZERO;
        for (APInvoiceLine line : invoice.getLines()) {
            amounts.add(line.getNetAmount(), line.getTaxRate());
            for (APMatch match : line.getMatches()) {
                variance = variance.add(match.getVarianceAmount());
                absolute = absolute.add(match.getVarianceAmount().abs()).add(match.getPurchaseVarianceAmount().abs());
            }
        }
        invoice.setNetAmount(Checks.money(amounts.getNetAmount(), "請求税抜額", true));
        invoice.setTaxAmount(amounts.getTaxAmount());
        invoice.setTotalAmount(Checks.money(amounts.getTotalAmount(), "請求総額", true));
        invoice.setVarianceAmount(variance);
        invoice.setAbsoluteVarianceAmount(absolute);
    }

    public static void invalidateReview(APInvoice invoice) {
        calculate(invoice);
        invoice.setVarianceStatus(invoice.getAbsoluteVarianceAmount().signum() == 0 ? "NONE" : "REQUIRED");
        invoice.setVarianceReason("");
        invoice.setVarianceApprovedBy(null);
        invoice.setVarianceApprovedById(null);
        invoice.setVarianceApprovedAt(null);
        invoice.setReviewFingerprint(null);
    }

    public static String fingerprint(APInvoice invoice) {
        List<String> values = new ArrayList<String>();
        values.add(invoice.getSupplier().getId().toString());
        values.add(invoice.getSupplierInvoiceNumber());
        values.add(Dates.format(invoice.getInvoiceDate()));
        values.add(Dates.format(invoice.getDueDate()));
        values.add(invoice.getTaxRounding());
        values.add(Integer.toString(invoice.getChangeNumber()));
        for (APInvoiceLine line : invoice.getLines()) {
            values.add(line.getId().toString());
            values.add(line.getProduct().getId().toString());
            values.add(Integer.toString(line.getQuantity()));
            values.add(line.getUnitPrice().toPlainString());
            values.add(line.getTaxRate().toPlainString());
            for (APMatch match : line.getMatches()) {
                values.add(match.getReceiptLine().getId().toString());
                values.add(Integer.toString(match.getQuantity()));
                values.add(match.getReceiptUnitCost().toPlainString());
                values.add(match.getOrderedUnitCost().toPlainString());
            }
        }
        return Fingerprints.of(values.toArray(new String[values.size()]));
    }

    public void priceCredit(APCredit credit) {
        APInvoice invoice = credit.getInvoice();
        TaxAmounts original = new TaxAmounts(invoice.getTaxRounding());
        TaxAmounts returned = new TaxAmounts(invoice.getTaxRounding());
        TaxAmounts current = new TaxAmounts(invoice.getTaxRounding());
        for (APInvoiceLine line : invoice.getLines()) { original.add(line.getNetAmount(), line.getTaxRate()); }
        BigDecimal previousTax = Money.ZERO;
        List<APCredit> previous = dao.list("from APCredit c where c.invoice=:invoice and c.status='POSTED'",
                WholesaleDao.params("invoice", invoice));
        for (APCredit prior : previous) {
            previousTax = previousTax.add(prior.getTaxAmount());
            for (APCreditLine line : prior.getLines()) { returned.add(line.getNetAmount(), line.getTaxRate()); }
        }
        for (APCreditLine line : credit.getLines()) {
            returned.add(line.getNetAmount(), line.getTaxRate());
            current.add(line.getNetAmount(), line.getTaxRate());
        }
        BigDecimal cumulativeTax = creditTax(original, returned, invoice.getTaxRounding());
        BigDecimal tax = cumulativeTax.subtract(previousTax);
        Checks.state(tax.signum() >= 0, "ap.creditTax", "累計値引税額が不整合です。");
        credit.setNetAmount(current.getNetAmount());
        credit.setTaxAmount(tax);
        credit.setTotalAmount(current.getNetAmount().add(tax));
    }

    public static BigDecimal creditTax(TaxAmounts original, TaxAmounts returned, String rounding) {
        BigDecimal result = Money.ZERO;
        for (Map.Entry<BigDecimal, BigDecimal> bucket : returned.getBases().entrySet()) {
            BigDecimal base = original.getBases().get(bucket.getKey());
            Checks.state(base != null && bucket.getValue().compareTo(base) <= 0,
                    "ap.overCredit", "値引額が元の税率別請求額を超えています。");
            result = result.add(Money.tax(base, bucket.getKey(), rounding)
                    .subtract(Money.tax(base.subtract(bucket.getValue()), bucket.getKey(), rounding)));
        }
        return result;
    }
}
