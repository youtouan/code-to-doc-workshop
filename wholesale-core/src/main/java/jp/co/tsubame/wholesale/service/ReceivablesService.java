package jp.co.tsubame.wholesale.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.AccountStatement;
import jp.co.tsubame.wholesale.common.AccountStatementLine;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BillingCashEntry;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Fingerprints;
import jp.co.tsubame.wholesale.common.Money;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.ReceivablesAgeing;
import jp.co.tsubame.wholesale.common.ReceivablesAgeingLine;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.dao.BillingLedger;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.CreditMemo;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Invoice;
import jp.co.tsubame.wholesale.entity.PaymentAllocation;
import jp.co.tsubame.wholesale.entity.PaymentReceipt;
import org.hibernate.Hibernate;
import org.hibernate.SQLQuery;

public class ReceivablesService extends BaseService {
    public PaymentReceipt receive(String requestKey, Long customerId, Date receivedDate, BigDecimal amount,
            String method, String reference, String notes, Actor actor) {
        require(actor, "BILLING");
        String key = Checks.text(requestKey, "依頼キー", 100);
        Date date = pastDate(receivedDate, "入金日");
        BigDecimal money = Checks.money(amount, "入金額", false);
        String paymentMethod = Checks.code(method, "入金方法");
        Checks.state("BANK_TRANSFER".equals(paymentMethod) || "CASH".equals(paymentMethod)
                || "CHEQUE".equals(paymentMethod), "receipt.method", "入金方法は振込・現金・小切手です。");
        String paymentReference = Checks.optionalText(reference, "入金照合番号", 100);
        String paymentNotes = Checks.optionalText(notes, "備考", 1000);
        Customer customer = dao.lock(Customer.class, customerId);
        // A global request key is locked after the customer; no financial row precedes the customer lock.
        dao.lockKey("billing.receipt", key);
        String fingerprint = Fingerprints.of(customerId.toString(), Dates.format(date), money.toPlainString(),
                paymentMethod, paymentReference, paymentNotes);
        List<PaymentReceipt> existing = dao.list("from PaymentReceipt p where p.requestKey=:key",
                WholesaleDao.params("key", key));
        if (!existing.isEmpty()) {
            PaymentReceipt receipt = existing.get(0);
            Checks.state(fingerprint.equals(receipt.getPayloadFingerprint()), "receipt.keyConflict",
                    "同じ依頼キーに異なる入金情報が指定されました。");
            return detail(receipt);
        }
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setNumber("PENDING-" + UUID.randomUUID().toString());
        receipt.setCustomer(customer);
        receipt.setCustomerName(customer.getName());
        receipt.setRequestKey(key);
        receipt.setPayloadFingerprint(fingerprint);
        receipt.setReceivedDate(date);
        receipt.setAmount(money);
        receipt.setMethod(paymentMethod);
        receipt.setReference(paymentReference);
        receipt.setNotes(paymentNotes);
        receipt.setStatus("POSTED");
        receipt.setCreatedBy(actor.getLogin());
        receipt.setCreatedAt(new Date());
        dao.save(receipt);
        receipt.setNumber(documentNumber("RCT", receipt.getId()));
        audit(actor, "RECEIPT_POST", receipt, "amount=" + money + ", method=" + paymentMethod);
        dao.flush();
        return detail(receipt);
    }

    public PaymentAllocation allocate(Long receiptId, int expectedVersion, Long invoiceId,
            BigDecimal amount, Actor actor) {
        require(actor, "BILLING");
        BigDecimal money = Checks.money(amount, "消込額", false);
        PaymentReceipt receipt = lockReceipt(receiptId);
        Checks.version(receipt.getVersion(), expectedVersion);
        Checks.state("POSTED".equals(receipt.getStatus()), "receipt.cancelled", "取消入金は消込できません。");
        Long invoiceCustomer = (Long) dao.query("select i.customer.id from Invoice i where i.id=:id",
                WholesaleDao.params("id", invoiceId)).uniqueResult();
        Checks.state(receipt.getCustomer().getId().equals(invoiceCustomer), "allocation.customer",
                "同じ得意先の請求だけを消込できます。");
        Invoice invoice = dao.lock(Invoice.class, invoiceId);
        Checks.state("FINALIZED".equals(invoice.getStatus()), "allocation.invoiceState", "確定請求だけを消込できます。");
        new BillingLedger(dao).reconcile(invoice);
        refreshReceiptBalance(receipt);
        Checks.state(money.compareTo(receipt.getUnallocatedAmount()) <= 0, "allocation.receiptAmount",
                "消込額が未消込入金額を超えています。");
        Checks.state(money.compareTo(invoice.getOutstandingAmount()) <= 0, "allocation.invoiceAmount",
                "消込額が請求残高を超えています。");
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setReceipt(receipt);
        allocation.setInvoice(invoice);
        allocation.setAmount(money);
        allocation.setAllocationDate(Dates.today());
        allocation.setStatus("ACTIVE");
        allocation.setCreatedBy(actor.getLogin());
        allocation.setCreatedAt(new Date());
        dao.save(allocation);
        receipt.getAllocations().add(allocation);
        receipt.setAllocatedAmount(receipt.getAllocatedAmount().add(money));
        invoice.setPaidAmount(invoice.getPaidAmount().add(money));
        audit(actor, "PAYMENT_ALLOCATE", allocation,
                "receipt=" + receipt.getNumber() + ", invoice=" + invoice.getNumber() + ", amount=" + money);
        dao.flush();
        return allocation;
    }

    public PaymentAllocation reverseAllocation(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "BILLING");
        String reversalReason = Checks.text(reason, "消込取消理由", 500);
        Long customerId = (Long) dao.query("select a.receipt.customer.id from PaymentAllocation a where a.id=:id",
                WholesaleDao.params("id", id)).uniqueResult();
        dao.lock(Customer.class, customerId);
        PaymentAllocation allocation = dao.lock(PaymentAllocation.class, id);
        Checks.version(allocation.getVersion(), expectedVersion);
        Checks.state("ACTIVE".equals(allocation.getStatus()), "allocation.reversed", "消込は既に取消済みです。");
        PaymentReceipt receipt = dao.lock(PaymentReceipt.class, allocation.getReceipt().getId());
        Invoice invoice = dao.lock(Invoice.class, allocation.getInvoice().getId());
        Checks.state("POSTED".equals(receipt.getStatus()) && "FINALIZED".equals(invoice.getStatus()),
                "allocation.state", "消込の入金・請求状態が不正です。");
        allocation.setStatus("REVERSED");
        allocation.setReversedBy(actor.getLogin());
        allocation.setReversedAt(new Date());
        allocation.setReversalDate(Dates.today());
        allocation.setReversalReason(reversalReason);
        dao.flush();
        refreshReceiptBalance(receipt);
        new BillingLedger(dao).reconcile(invoice);
        audit(actor, "PAYMENT_REVERSE", allocation, reversalReason);
        dao.flush();
        return allocation;
    }

    public void cancelReceipt(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "BILLING");
        String cancellationReason = Checks.text(reason, "入金取消理由", 500);
        PaymentReceipt receipt = lockReceipt(id);
        Checks.version(receipt.getVersion(), expectedVersion);
        Checks.state("POSTED".equals(receipt.getStatus()), "receipt.cancelled", "入金は既に取消済みです。");
        refreshReceiptBalance(receipt);
        Checks.state(receipt.getAllocatedAmount().signum() == 0, "receipt.allocated",
                "消込済み入金は取消できません。先に全消込を取消してください。");
        receipt.setStatus("CANCELLED");
        receipt.setCancelledBy(actor.getLogin());
        receipt.setCancelledAt(new Date());
        receipt.setCancellationDate(Dates.today());
        receipt.setCancellationReason(cancellationReason);
        audit(actor, "RECEIPT_CANCEL", receipt, cancellationReason);
        dao.flush();
    }

    public PaymentReceipt getReceipt(Long id, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        return detail(dao.get(PaymentReceipt.class, id));
    }

    public Page<PaymentReceipt> searchReceipts(Search search, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        Checks.state(search != null, "receipt.search", "検索条件を指定してください。");
        String where = " where (p.number like :text escape '!' or p.customerName like :text escape '!' "
                + "or p.reference like :text escape '!')";
        Map<String, Object> parameters = WholesaleDao.params("text", search.getLikeText());
        if (search.getCustomerId() != null) {
            where += " and p.customer.id=:customerId";
            parameters.put("customerId", search.getCustomerId());
        }
        if (search.getStatus().length() != 0) {
            Checks.state("POSTED".equals(search.getStatus()) || "CANCELLED".equals(search.getStatus()),
                    "receipt.status", "入金状態が不正です。");
            where += " and p.status=:status";
            parameters.put("status", search.getStatus());
        }
        if (search.getFrom() != null) {
            where += " and p.receivedDate>=:from";
            parameters.put("from", search.getFrom());
        }
        if (search.getTo() != null) {
            where += " and p.receivedDate<=:to";
            parameters.put("to", search.getTo());
        }
        Page<PaymentReceipt> result = dao.page("from PaymentReceipt p" + where + " order by p.receivedDate desc,p.id desc",
                "select count(p.id) from PaymentReceipt p" + where, parameters, search);
        for (PaymentReceipt receipt : result.getItems()) {
            detail(receipt);
        }
        return result;
    }

    public Page<PaymentReceipt> searchCashLedger(Search search, Actor actor) {
        return searchReceipts(search, actor);
    }

    @SuppressWarnings("unchecked")
    public Page<BillingCashEntry> searchCashMovements(Search search, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        Checks.state(search != null, "cash.search", "検索条件を指定してください。");
        String movements = "(select id,number,customer_id,customer_name,received_date as event_date,"
                + "'RECEIPT' as event_type,method,reference,amount as inflow,cast(0 as numeric) as outflow,"
                + "created_by as event_actor,notes as reason from billing_payment_receipt "
                + "union all select id,number,customer_id,customer_name,cancellation_date as event_date,"
                + "'RECEIPT_CANCEL' as event_type,method,reference,cast(0 as numeric) as inflow,amount as outflow,"
                + "cancelled_by as event_actor,cancellation_reason as reason from billing_payment_receipt "
                + "where status='CANCELLED') cash";
        String where = " where (number like :text escape '!' or customer_name like :text escape '!' "
                + "or reference like :text escape '!')";
        Map<String, Object> parameters = WholesaleDao.params("text", search.getLikeText());
        if (search.getCustomerId() != null) {
            where += " and customer_id=:customerId";
            parameters.put("customerId", search.getCustomerId());
        }
        if (search.getFrom() != null) {
            where += " and event_date>=:from";
            parameters.put("from", search.getFrom());
        }
        if (search.getTo() != null) {
            where += " and event_date<=:to";
            parameters.put("to", search.getTo());
        }
        if (search.getStatus().length() > 0) {
            Checks.state("RECEIPT".equals(search.getStatus()) || "RECEIPT_CANCEL".equals(search.getStatus()),
                    "cash.type", "現金移動区分が不正です。");
            where += " and event_type=:type";
            parameters.put("type", search.getStatus());
        }
        SQLQuery count = dao.session().createSQLQuery("select count(*) from " + movements + where);
        SQLQuery select = dao.session().createSQLQuery("select id,number,customer_id,customer_name,event_date,"
                + "event_type,method,reference,inflow,outflow,event_actor,reason from "
                + movements + where + " order by event_date desc,id desc,event_type");
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            count.setParameter(parameter.getKey(), parameter.getValue());
            select.setParameter(parameter.getKey(), parameter.getValue());
        }
        long total = ((Number) count.uniqueResult()).longValue();
        select.setFirstResult(search.getOffset()).setMaxResults(search.getSize());
        List<Object[]> rows = (List<Object[]>) select.list();
        List<BillingCashEntry> entries = new ArrayList<BillingCashEntry>();
        for (Object[] row : rows) {
            entries.add(new BillingCashEntry(((Number) row[0]).longValue(), (String) row[1],
                    ((Number) row[2]).longValue(), (String) row[3], (Date) row[4],
                    (String) row[5], (String) row[6], (String) row[7], (BigDecimal) row[8],
                    (BigDecimal) row[9], (String) row[10], (String) row[11]));
        }
        return new Page<BillingCashEntry>(entries, total, search.getPage(), search.getSize());
    }

    public List<Invoice> listOpenInvoices(Long customerId, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        Customer customer = dao.get(Customer.class, customerId);
        List<Invoice> invoices = dao.list("from Invoice i where i.customer=:customer and i.status='FINALIZED' "
                + "and i.totalAmount>i.paidAmount+i.creditedAmount order by i.dueDate,i.id",
                WholesaleDao.params("customer", customer));
        for (Invoice invoice : invoices) {
            Hibernate.initialize(invoice.getLines());
        }
        return invoices;
    }

    public AccountStatement getStatement(Long customerId, Date from, Date to, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        Customer customer = dao.get(Customer.class, customerId);
        Date start = Checks.date(from, "開始日");
        Date end = pastDate(to, "終了日");
        Checks.state(!start.after(end), "statement.range", "開始日は終了日以前にしてください。");
        BigDecimal opening = accountBalance(customer, Dates.addDays(start, -1));
        AccountStatement result = new AccountStatement(customer, start, end, opening);
        List<AccountStatementLine> entries = new ArrayList<AccountStatementLine>();
        Map<String, Object> parameters = WholesaleDao.params("customer", customer, "from", start);
        parameters.put("to", end);
        List<Invoice> invoices = dao.list("from Invoice i where i.customer=:customer and i.status='FINALIZED' "
                + "and i.issuedDate between :from and :to", parameters);
        for (Invoice invoice : invoices) {
            entries.add(new AccountStatementLine(invoice.getIssuedDate(), "INVOICE", invoice.getId(),
                    invoice.getNumber(), invoice.getCustomerName(), invoice.getTotalAmount(), Money.ZERO));
        }
        List<CreditMemo> credits = dao.list("from CreditMemo c where c.customer=:customer and c.status='APPLIED' "
                + "and c.postedDate between :from and :to", parameters);
        for (CreditMemo credit : credits) {
            entries.add(new AccountStatementLine(credit.getPostedDate(), "CREDIT", credit.getId(),
                    credit.getNumber(), credit.getSalesReturn().getNumber(), Money.ZERO, credit.getTotalAmount()));
        }
        List<PaymentReceipt> receipts = dao.list("from PaymentReceipt p where p.customer=:customer "
                + "and p.receivedDate between :from and :to", parameters);
        for (PaymentReceipt receipt : receipts) {
            entries.add(new AccountStatementLine(receipt.getReceivedDate(), "RECEIPT", receipt.getId(),
                    receipt.getNumber(), receipt.getReference(), Money.ZERO, receipt.getAmount()));
        }
        List<PaymentReceipt> cancelled = dao.list("from PaymentReceipt p where p.customer=:customer "
                + "and p.cancellationDate between :from and :to", parameters);
        for (PaymentReceipt receipt : cancelled) {
            entries.add(new AccountStatementLine(receipt.getCancellationDate(), "RECEIPT_CANCEL", receipt.getId(),
                    receipt.getNumber(), receipt.getCancellationReason(), receipt.getAmount(), Money.ZERO));
        }
        Collections.sort(entries, new Comparator<AccountStatementLine>() {
            public int compare(AccountStatementLine left, AccountStatementLine right) {
                int date = left.getDate().compareTo(right.getDate());
                if (date != 0) {
                    return date;
                }
                int id = left.getDocumentId().compareTo(right.getDocumentId());
                return id == 0 ? left.getType().compareTo(right.getType()) : id;
            }
        });
        for (AccountStatementLine entry : entries) {
            result.add(entry);
        }
        return result;
    }

    public ReceivablesAgeing getAgeing(Long customerId, Date asOf, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        Customer customer = dao.get(Customer.class, customerId);
        Date date = pastDate(asOf, "基準日");
        ReceivablesAgeing result = new ReceivablesAgeing(customer, date);
        Map<String, Object> parameters = WholesaleDao.params("customer", customer, "date", date);
        List<Invoice> invoices = dao.list("from Invoice i where i.customer=:customer and i.status='FINALIZED' "
                + "and i.issuedDate<=:date order by i.dueDate,i.id", parameters);
        BigDecimal appliedReceipts = Money.ZERO;
        BigDecimal appliedCredits = Money.ZERO;
        for (Invoice invoice : invoices) {
            Map<String, Object> invoiceParameters = WholesaleDao.params("invoice", invoice, "date", date);
            BigDecimal paid = allocationTotalAsOf(invoiceParameters);
            BigDecimal credits = new BillingLedger(dao).sum("select sum(c.totalAmount) from CreditMemo c "
                    + "where c.invoice=:invoice and c.status='APPLIED' and c.postedDate<=:date", invoiceParameters);
            BigDecimal credited = credits.min(invoice.getTotalAmount().subtract(paid));
            BigDecimal outstanding = invoice.getTotalAmount().subtract(paid).subtract(credited);
            Checks.state(outstanding.signum() >= 0 && credited.signum() >= 0,
                    "ageing.balance", "過去時点の消込残高が不整合です。");
            appliedReceipts = appliedReceipts.add(paid);
            appliedCredits = appliedCredits.add(credited);
            if (outstanding.signum() > 0) {
                result.add(new ReceivablesAgeingLine(invoice, date, outstanding));
            }
        }
        BillingLedger ledger = new BillingLedger(dao);
        BigDecimal receipts = ledger.sum("select sum(p.amount) from PaymentReceipt p where p.customer=:customer "
                + "and p.receivedDate<=:date and (p.cancellationDate is null or p.cancellationDate>:date)", parameters);
        BigDecimal credits = ledger.sum("select sum(c.totalAmount) from CreditMemo c where c.customer=:customer "
                + "and c.status='APPLIED' and c.postedDate<=:date", parameters);
        result.setUnallocatedReceipts(receipts.subtract(appliedReceipts));
        result.setUnappliedCredits(credits.subtract(appliedCredits));
        return result;
    }

    private BigDecimal allocationTotalAsOf(Map<String, Object> parameters) {
        return new BillingLedger(dao).sum("select sum(a.amount) from PaymentAllocation a where a.invoice=:invoice "
                + "and a.allocationDate<=:date and (a.reversalDate is null or a.reversalDate>:date)", parameters);
    }

    private BigDecimal accountBalance(Customer customer, Date asOf) {
        BillingLedger ledger = new BillingLedger(dao);
        Map<String, Object> parameters = WholesaleDao.params("customer", customer, "date", asOf);
        BigDecimal invoiced = ledger.sum("select sum(i.totalAmount) from Invoice i where i.customer=:customer "
                + "and i.status='FINALIZED' and i.issuedDate<=:date", parameters);
        BigDecimal credited = ledger.sum("select sum(c.totalAmount) from CreditMemo c where c.customer=:customer "
                + "and c.status='APPLIED' and c.postedDate<=:date", parameters);
        BigDecimal received = ledger.sum("select sum(p.amount) from PaymentReceipt p where p.customer=:customer "
                + "and p.receivedDate<=:date and (p.cancellationDate is null or p.cancellationDate>:date)", parameters);
        return invoiced.subtract(credited).subtract(received);
    }

    private Date pastDate(Date value, String field) {
        Date date = Checks.date(value, field);
        Checks.state(!date.after(Dates.today()), "receivables.future", "未来の日付は指定できません。");
        return date;
    }

    private PaymentReceipt lockReceipt(Long id) {
        Long customerId = (Long) dao.query("select p.customer.id from PaymentReceipt p where p.id=:id",
                WholesaleDao.params("id", id)).uniqueResult();
        dao.lock(Customer.class, customerId);
        return dao.lock(PaymentReceipt.class, id);
    }

    private void refreshReceiptBalance(PaymentReceipt receipt) {
        BigDecimal allocated = new BillingLedger(dao).sum("select sum(a.amount) from PaymentAllocation a "
                + "where a.receipt=:receipt and a.status='ACTIVE'", WholesaleDao.params("receipt", receipt));
        Checks.state(allocated.signum() >= 0 && allocated.compareTo(receipt.getAmount()) <= 0,
                "receipt.balance", "入金の消込残高が不整合です。");
        receipt.setAllocatedAmount(allocated);
    }

    private PaymentReceipt detail(PaymentReceipt receipt) {
        Hibernate.initialize(receipt.getAllocations());
        return receipt;
    }
}
