package jp.co.tsubame.wholesale.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.APPaymentInput;
import jp.co.tsubame.wholesale.common.APPaymentLineInput;
import jp.co.tsubame.wholesale.common.APSearch;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Fingerprints;
import jp.co.tsubame.wholesale.common.Money;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.dao.APLedger;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.APPaymentLine;
import jp.co.tsubame.wholesale.entity.APPaymentVoucher;
import jp.co.tsubame.wholesale.entity.Supplier;
import org.hibernate.Hibernate;

public class APSettlementService extends BaseService {
    public APPaymentVoucher pay(APPaymentInput input, Actor actor) {
        require(actor, "BILLING");
        Checks.state(input != null, "ap.paymentInput", "支払情報を指定してください。");
        String key = Checks.text(input.getRequestKey(), "依頼キー", 100);
        Date date = APLedger.pastDate(input.getPaymentDate(), "支払日");
        BigDecimal amount = Checks.money(input.getAmount(), "支払額", false);
        String method = Checks.code(input.getMethod(), "支払方法");
        Checks.state("BANK_TRANSFER".equals(method) || "CASH".equals(method) || "CHEQUE".equals(method),
                "ap.paymentMethod", "支払方法は振込・現金・小切手から選択してください。");
        String reference = Checks.optionalText(input.getReference(), "振込照合番号", 100);
        String notes = Checks.optionalText(input.getNotes(), "備考", 1000);
        Map<Long, BigDecimal> allocations = normalize(input.getLines(), amount);
        Supplier supplier = dao.lock(Supplier.class, input.getSupplierId());
        dao.lockKey("ap.payment", key);
        List<String> fingerprintFields = new ArrayList<String>();
        fingerprintFields.add(supplier.getId().toString());
        fingerprintFields.add(Dates.format(date));
        fingerprintFields.add(amount.toPlainString());
        fingerprintFields.add(method);
        fingerprintFields.add(reference);
        fingerprintFields.add(notes);
        for (Map.Entry<Long, BigDecimal> allocation : allocations.entrySet()) {
            fingerprintFields.add(allocation.getKey().toString());
            fingerprintFields.add(allocation.getValue().toPlainString());
        }
        String fingerprint = Fingerprints.of(fingerprintFields.toArray(new String[fingerprintFields.size()]));
        List<APPaymentVoucher> existing = dao.list("from APPaymentVoucher v where v.requestKey=:key", WholesaleDao.params("key", key));
        if (!existing.isEmpty()) {
            APPaymentVoucher voucher = existing.get(0);
            Checks.state(fingerprint.equals(voucher.getFingerprint()), "ap.paymentKeyConflict", "同じ依頼キーの支払内容が異なります。");
            return detail(voucher);
        }
        APPaymentVoucher voucher = new APPaymentVoucher();
        voucher.setNumber("NEW-" + UUID.randomUUID().toString());
        voucher.setSupplier(supplier);
        voucher.setSupplierName(supplier.getName());
        voucher.setRequestKey(key);
        voucher.setFingerprint(fingerprint);
        voucher.setPaymentDate(date);
        voucher.setAmount(amount);
        voucher.setMethod(method);
        voucher.setReference(reference);
        voucher.setNotes(notes);
        voucher.setCreatedBy(actor.getLogin());
        voucher.setCreatedAt(new Date());
        APLedger ledger = new APLedger(dao);
        for (Map.Entry<Long, BigDecimal> allocation : allocations.entrySet()) {
            Long owner = (Long) dao.query("select i.supplier.id from APInvoice i where i.id=:id",
                    WholesaleDao.params("id", allocation.getKey())).uniqueResult();
            Checks.state(supplier.getId().equals(owner), "ap.paymentSupplier", "同じ仕入先の請求だけをまとめて支払できます。");
            APInvoice invoice = dao.lock(APInvoice.class, allocation.getKey());
            Checks.state("POSTED".equals(invoice.getStatus()), "ap.paymentInvoice", "計上済み請求だけを支払できます。");
            Checks.state(!date.before(invoice.getPostedDate()), "ap.paymentDate", "請求計上日以前には支払計上できません。");
            ledger.reconcile(invoice);
            Checks.state(allocation.getValue().compareTo(invoice.getOutstandingAmount()) <= 0,
                    "ap.overpayment", "支払消込額が未払残高を超えています。");
            APPaymentLine line = new APPaymentLine();
            line.setVoucher(voucher);
            line.setInvoice(invoice);
            line.setAmount(allocation.getValue());
            voucher.getLines().add(line);
        }
        dao.save(voucher);
        voucher.setNumber(documentNumber("APV", voucher.getId()));
        dao.flush();
        for (APPaymentLine line : voucher.getLines()) { ledger.reconcile(line.getInvoice()); }
        audit(actor, "AP_PAYMENT_POST", voucher, "amount=" + amount + ", invoices=" + allocations.size());
        dao.flush();
        return detail(voucher);
    }

    public void cancelPayment(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "BILLING");
        Long supplierId = (Long) dao.query("select v.supplier.id from APPaymentVoucher v where v.id=:id",
                WholesaleDao.params("id", id)).uniqueResult();
        dao.lock(Supplier.class, supplierId);
        APPaymentVoucher voucher = dao.lock(APPaymentVoucher.class, id);
        Checks.version(voucher.getVersion(), expectedVersion);
        Checks.state("POSTED".equals(voucher.getStatus()), "ap.paymentCancelled", "支払は既に取消済みです。");
        voucher.setCancellationReason(Checks.text(reason, "支払取消理由", 500));
        voucher.setCancelledBy(actor.getLogin());
        voucher.setCancelledAt(new Date());
        voucher.setCancellationDate(Dates.today());
        voucher.setStatus("CANCELLED");
        Map<Long, APInvoice> invoices = new TreeMap<Long, APInvoice>();
        for (APPaymentLine line : voucher.getLines()) {
            Checks.state("ACTIVE".equals(line.getStatus()), "ap.paymentLineState", "支払明細状態が不整合です。");
            invoices.put(line.getInvoice().getId(), line.getInvoice());
            line.setStatus("REVERSED");
            line.setReversalDate(Dates.today());
        }
        for (Long invoiceId : invoices.keySet()) { dao.lock(APInvoice.class, invoiceId); }
        dao.flush();
        for (APInvoice invoice : invoices.values()) { new APLedger(dao).reconcile(invoice); }
        audit(actor, "AP_PAYMENT_CANCEL", voucher, voucher.getCancellationReason() + "; all settlements reversed");
        dao.flush();
    }

    public APPaymentVoucher getPayment(Long id, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        return detail(dao.get(APPaymentVoucher.class, id));
    }

    public Page<APPaymentVoucher> searchPayments(APSearch search, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        Checks.state(search != null, "ap.search", "検索条件を指定してください。");
        String where = " where (v.number like :text escape '!' or v.supplierName like :text escape '!' "
                + "or v.reference like :text escape '!')";
        Map<String, Object> parameters = WholesaleDao.params("text", search.getLikeText());
        if (search.getSupplierId() != null) { where += " and v.supplier.id=:supplier"; parameters.put("supplier", search.getSupplierId()); }
        if (search.getStatus().length() > 0) {
            Checks.state("POSTED".equals(search.getStatus()) || "CANCELLED".equals(search.getStatus()), "ap.status", "支払状態が不正です。");
            where += " and v.status=:status"; parameters.put("status", search.getStatus());
        }
        if (search.getFrom() != null) { where += " and v.paymentDate>=:from"; parameters.put("from", search.getFrom()); }
        if (search.getTo() != null) { where += " and v.paymentDate<=:to"; parameters.put("to", search.getTo()); }
        Page<APPaymentVoucher> result = dao.page("from APPaymentVoucher v" + where + " order by v.paymentDate desc,v.id desc",
                "select count(v.id) from APPaymentVoucher v" + where, parameters, search);
        for (APPaymentVoucher voucher : result.getItems()) { detail(voucher); }
        return result;
    }

    public static Map<Long, BigDecimal> normalize(List<APPaymentLineInput> lines, BigDecimal amount) {
        Checks.nonempty(lines, "支払消込明細");
        Map<Long, BigDecimal> result = new TreeMap<Long, BigDecimal>();
        BigDecimal total = Money.ZERO;
        for (APPaymentLineInput line : lines) {
            Checks.state(line != null && line.getInvoiceId() != null && !result.containsKey(line.getInvoiceId()),
                    "ap.paymentLine", "支払先請求が不正または重複しています。");
            BigDecimal value = Checks.money(line.getAmount(), "消込額", false);
            result.put(line.getInvoiceId(), value);
            total = total.add(value);
        }
        Checks.state(total.compareTo(amount) == 0, "ap.paymentTotal", "支払額は各請求の消込額合計と一致させてください。");
        return result;
    }

    private APPaymentVoucher detail(APPaymentVoucher voucher) {
        Hibernate.initialize(voucher.getLines());
        return voucher;
    }
}
