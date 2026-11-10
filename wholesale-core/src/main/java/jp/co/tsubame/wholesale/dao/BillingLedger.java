package jp.co.tsubame.wholesale.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Money;
import jp.co.tsubame.wholesale.entity.CreditMemo;
import jp.co.tsubame.wholesale.entity.Invoice;

/** Balance fields are projections; allocations and credit documents are the ledger. */
public class BillingLedger {
    private final WholesaleDao dao;

    public BillingLedger(WholesaleDao dao) {
        this.dao = dao;
    }

    public BigDecimal sum(String hql, Map<String, ?> parameters) {
        BigDecimal amount = (BigDecimal) dao.query(hql, parameters).uniqueResult();
        return amount == null ? Money.ZERO : amount;
    }

    public void reconcile(Invoice invoice) {
        Map<String, Object> parameters = WholesaleDao.params("invoice", invoice);
        BigDecimal paid = sum("select sum(a.amount) from PaymentAllocation a "
                + "where a.invoice=:invoice and a.status='ACTIVE'", parameters);
        List<CreditMemo> credits = dao.list("from CreditMemo c where c.invoice=:invoice order by c.id", parameters);
        BigDecimal creditTotal = Money.ZERO;
        for (CreditMemo credit : credits) {
            creditTotal = creditTotal.add(credit.getTotalAmount());
        }
        Checks.state(paid.signum() >= 0 && paid.compareTo(invoice.getTotalAmount()) <= 0
                && creditTotal.compareTo(invoice.getTotalAmount()) <= 0,
                "receivables.corruptBalance", "入金・返品額が請求額を超えています。");
        BigDecimal remaining = invoice.getTotalAmount().subtract(paid);
        BigDecimal applied = Money.ZERO;
        for (CreditMemo credit : credits) {
            BigDecimal amount = remaining.min(credit.getTotalAmount());
            credit.setAppliedAmount(amount);
            remaining = remaining.subtract(amount);
            applied = applied.add(amount);
        }
        invoice.setPaidAmount(paid);
        invoice.setCreditedAmount(applied);
    }
}
