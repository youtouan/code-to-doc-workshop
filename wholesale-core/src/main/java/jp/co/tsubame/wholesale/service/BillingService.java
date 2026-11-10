package jp.co.tsubame.wholesale.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Fingerprints;
import jp.co.tsubame.wholesale.common.Money;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.dao.BillingAmounts;
import jp.co.tsubame.wholesale.dao.BillingLedger;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.CreditMemo;
import jp.co.tsubame.wholesale.entity.CreditMemoLine;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Invoice;
import jp.co.tsubame.wholesale.entity.InvoiceLine;
import jp.co.tsubame.wholesale.entity.SalesReturn;
import jp.co.tsubame.wholesale.entity.SalesReturnLine;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.ShipmentLine;
import org.hibernate.Hibernate;

public class BillingService extends BaseService {
    public Invoice prepare(Long customerId, Date periodEnd, Actor actor) {
        require(actor, "BILLING", "BATCH");
        Customer customer = dao.lock(Customer.class, customerId);
        Date end = Checks.date(periodEnd, "締日");
        Checks.state(!end.after(Dates.today()), "billing.future", "未来の締日は処理できません。");
        Map<String, Object> parameters = WholesaleDao.params("customer", customer, "end", end);
        List<Invoice> existing = dao.list("from Invoice i where i.customer=:customer "
                + "and i.periodEnd=:end and i.status<>'VOID'", parameters);
        if (!existing.isEmpty()) {
            return detail(existing.get(0));
        }
        validateClosingDate(customer, end);
        Date previousEnd = (Date) dao.query("select max(i.periodEnd) from Invoice i "
                + "where i.customer=:customer and i.status='FINALIZED'",
                WholesaleDao.params("customer", customer)).uniqueResult();
        Checks.state(previousEnd == null || end.after(previousEnd),
                "billing.retrograde", "確定済み請求期間以前の請求は作成できません。");
        Checks.state(dao.count("select count(i.id) from Invoice i where i.customer=:customer "
                + "and i.status='DRAFT'", WholesaleDao.params("customer", customer)) == 0,
                "billing.openDraft", "未確定の請求を確定または取消してください。");
        List<Shipment> shipments = dao.list("from Shipment s where s.order.customer=:customer "
                + "and s.status='CONFIRMED' and s.invoice is null and s.shippedDate<=:end "
                + "order by s.shippedDate,s.id", parameters);
        Checks.state(!shipments.isEmpty(), "billing.noShipments", "締日以前の未請求出荷がありません。");
        Invoice invoice = new Invoice();
        invoice.setNumber("PENDING-" + UUID.randomUUID().toString());
        invoice.setCustomer(customer);
        invoice.setCustomerName(customer.getName());
        invoice.setBillingAddress(customer.getAddress());
        invoice.setPostalCode(customer.getPostalCode());
        invoice.setTaxRounding(customer.getTaxRounding());
        invoice.setPaymentTermDays(customer.getPaymentTermDays());
        invoice.setPeriodStart(previousEnd == null
                ? Dates.addDays(Dates.closingDate(Dates.addMonths(end, -1), customer.getClosingDay()), 1)
                : Dates.addDays(previousEnd, 1));
        invoice.setPeriodEnd(end);
        invoice.setIssuedDate(Dates.today());
        invoice.setDueDate(Dates.addDays(end, customer.getPaymentTermDays()));
        invoice.setStatus("DRAFT");
        invoice.setCreatedBy(actor.getLogin());
        invoice.setCreatedAt(new Date());
        BillingAmounts amounts = new BillingAmounts(invoice.getTaxRounding());
        int lineNumber = 0;
        for (Shipment shipment : shipments) {
            Checks.state(!shipment.getLines().isEmpty(), "billing.emptyShipment", "出荷明細がありません。");
            for (ShipmentLine source : shipment.getLines()) {
                InvoiceLine line = snapshot(invoice, source, ++lineNumber);
                invoice.getLines().add(line);
                amounts.add(line.getNetAmount(), line.getTaxRate());
            }
        }
        invoice.setNetAmount(amounts.net());
        invoice.setTaxAmount(amounts.tax());
        invoice.setTotalAmount(amounts.gross());
        invoice.setClaimFingerprint(invoiceFingerprint(invoice));
        dao.save(invoice);
        invoice.setNumber(documentNumber("INV", invoice.getId()));
        for (Shipment shipment : shipments) {
            shipment.setInvoice(invoice);
        }
        dao.flush();
        List<CreditMemo> pending = dao.list("from CreditMemo c where c.customer=:customer "
                + "and c.invoice is null and c.salesReturn.shipment.invoice=:invoice order by c.id",
                WholesaleDao.params("customer", customer, "invoice", invoice));
        for (CreditMemo memo : pending) {
            memo.setInvoice(invoice);
        }
        repriceDraftCredits(invoice);
        audit(actor, "INVOICE_PREPARE", invoice, "period=" + Dates.format(end) + ", shipments=" + shipments.size());
        dao.flush();
        return detail(invoice);
    }

    public Invoice getInvoice(Long id, Actor actor) {
        require(actor, "BILLING", "MANAGER", "BATCH");
        return detail(dao.get(Invoice.class, id));
    }

    public Page<Invoice> searchInvoices(Search search, Actor actor) {
        require(actor, "BILLING", "MANAGER", "BATCH");
        Checks.state(search != null, "billing.search", "検索条件を指定してください。");
        Map<String, Object> parameters = WholesaleDao.params("text", search.getLikeText());
        String where = " where (i.number like :text escape '!' or i.customerName like :text escape '!')";
        if (search.getCustomerId() != null) {
            where += " and i.customer.id=:customerId";
            parameters.put("customerId", search.getCustomerId());
        }
        if (search.getStatus().length() > 0) {
            Checks.state("DRAFT".equals(search.getStatus()) || "FINALIZED".equals(search.getStatus())
                    || "VOID".equals(search.getStatus()), "billing.status", "請求状態が不正です。");
            where += " and i.status=:status";
            parameters.put("status", search.getStatus());
        }
        if (search.getFrom() != null) {
            where += " and i.periodEnd>=:from";
            parameters.put("from", search.getFrom());
        }
        if (search.getTo() != null) {
            where += " and i.periodEnd<=:to";
            parameters.put("to", search.getTo());
        }
        Page<Invoice> page = dao.page("from Invoice i" + where + " order by i.periodEnd desc,i.id desc",
                "select count(i.id) from Invoice i" + where, parameters, search);
        for (Invoice invoice : page.getItems()) {
            detail(invoice);
        }
        return page;
    }

    public Invoice finalizeInvoice(Long id, int expectedVersion, Actor actor) {
        require(actor, "BILLING", "BATCH");
        Invoice invoice = lockInvoice(id);
        Checks.version(invoice.getVersion(), expectedVersion);
        Checks.state("DRAFT".equals(invoice.getStatus()), "billing.notDraft", "未確定請求だけを確定できます。");
        Checks.state(invoice.getClaimFingerprint().equals(invoiceFingerprint(invoice)),
                "billing.changedClaim", "請求明細が作成時から変更されています。再作成してください。");
        Date previousEnd = (Date) dao.query("select max(i.periodEnd) from Invoice i where "
                + "i.customer=:customer and i.status='FINALIZED'",
                WholesaleDao.params("customer", invoice.getCustomer())).uniqueResult();
        Checks.state(previousEnd == null || invoice.getPeriodStart().after(previousEnd),
                "billing.overlap", "確定済み請求期間と重複しています。");
        for (InvoiceLine line : invoice.getLines()) {
            ShipmentLine source = line.getShipmentLine();
            Checks.state(source != null && "CONFIRMED".equals(source.getShipment().getStatus())
                    && source.getShipment().getInvoice() != null
                    && id.equals(source.getShipment().getInvoice().getId())
                    && line.getQuantity() == source.getQuantity()
                    && line.getUnitPrice().compareTo(source.getUnitPrice()) == 0
                    && line.getTaxRate().compareTo(source.getTaxRate()) == 0
                    && !source.getShipment().getShippedDate().after(invoice.getPeriodEnd()),
                    "billing.changedSource", "請求対象の出荷明細が変更されています。");
        }
        invoice.setIssuedDate(Dates.today());
        repriceDraftCredits(invoice);
        List<CreditMemo> credits = invoiceCredits(invoice);
        for (CreditMemo credit : credits) {
            postCredit(credit, invoice);
        }
        invoice.setStatus("FINALIZED");
        invoice.setFinalizedBy(actor.getLogin());
        invoice.setFinalizedAt(new Date());
        audit(actor, "INVOICE_FINALIZE", invoice, invoice.getNumber() + ", amount=" + invoice.getTotalAmount());
        dao.flush();
        return detail(invoice);
    }

    public void cancelDraft(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "BILLING");
        String cancellationReason = Checks.text(reason, "取消理由", 500);
        Invoice invoice = lockInvoice(id);
        Checks.version(invoice.getVersion(), expectedVersion);
        Checks.state("DRAFT".equals(invoice.getStatus()), "billing.notDraft", "確定請求は取消できません。");
        Checks.state(invoice.getPaidAmount().signum() == 0, "billing.hasPayments", "入金済み請求は取消できません。");
        for (CreditMemo memo : invoiceCredits(invoice)) {
            Checks.state("PENDING".equals(memo.getStatus()), "billing.postedCredit", "計上済み返品が存在します。");
            memo.setInvoice(null);
            memo.setAppliedAmount(Money.ZERO);
        }
        List<Shipment> shipments = dao.list("from Shipment s where s.invoice=:invoice",
                WholesaleDao.params("invoice", invoice));
        for (Shipment shipment : shipments) {
            shipment.setInvoice(null);
        }
        invoice.setCreditedAmount(Money.ZERO);
        invoice.setStatus("VOID");
        invoice.setCancelledBy(actor.getLogin());
        invoice.setCancelledAt(new Date());
        invoice.setCancellationReason(cancellationReason);
        for (Shipment shipment : shipments) {
            repricePendingShipmentCredits(shipment);
        }
        audit(actor, "INVOICE_VOID", invoice, cancellationReason);
        dao.flush();
    }

    public CreditMemo issueReturnCredit(SalesReturn salesReturn, Actor actor) {
        require(actor, "WAREHOUSE", "BILLING");
        Checks.state(salesReturn != null && salesReturn.getId() != null,
                "credit.return", "保存済み返品を指定してください。");
        Long customerId = (Long) dao.query("select r.shipment.order.customer.id from SalesReturn r where r.id=:id",
                WholesaleDao.params("id", salesReturn.getId())).uniqueResult();
        Customer customer = dao.lock(Customer.class, customerId);
        SalesReturn source = dao.get(SalesReturn.class, salesReturn.getId());
        Checks.state("RECEIVED".equals(source.getStatus()), "credit.notReceived", "返品受入済み伝票だけを処理できます。");
        Date issued = Checks.date(source.getReceivedDate(), "返品受入日");
        Checks.state(!issued.after(Dates.today()), "credit.future", "未来の返品は計上できません。");
        String fingerprint = returnFingerprint(source);
        List<CreditMemo> duplicates = dao.list("from CreditMemo c where c.salesReturn=:source",
                WholesaleDao.params("source", source));
        if (!duplicates.isEmpty()) {
            CreditMemo duplicate = duplicates.get(0);
            Checks.state(fingerprint.equals(duplicate.getSourceFingerprint()), "credit.sourceChanged",
                    "返品伝票が計上時から変更されています。");
            return creditDetail(duplicate);
        }
        Shipment shipment = source.getShipment();
        Checks.state("CONFIRMED".equals(shipment.getStatus()), "credit.shipment", "確定出荷の返品だけを計上できます。");
        Invoice invoice = shipment.getInvoice();
        if (invoice != null) {
            invoice = dao.lock(Invoice.class, invoice.getId());
            Checks.state(!"VOID".equals(invoice.getStatus()), "credit.voidInvoice", "取消請求への返品は計上できません。");
        }
        CreditMemo memo = new CreditMemo();
        memo.setNumber("PENDING-" + UUID.randomUUID().toString());
        memo.setCustomer(customer);
        memo.setCustomerName(customer.getName());
        memo.setSalesReturn(source);
        memo.setInvoice(invoice);
        memo.setStatus("PENDING");
        memo.setIssuedDate(issued);
        memo.setTaxRounding(invoice == null ? shipment.getOrder().getTaxRounding() : invoice.getTaxRounding());
        memo.setCreatedBy(actor.getLogin());
        memo.setCreatedAt(new Date());
        memo.setSourceFingerprint(fingerprint);
        Set<Long> seen = new HashSet<Long>();
        Checks.state(!source.getLines().isEmpty(), "credit.lines", "返品明細がありません。");
        for (SalesReturnLine returnLine : source.getLines()) {
            ShipmentLine original = returnLine.getShipmentLine();
            Checks.state(original != null && original.getShipment().getId().equals(shipment.getId())
                    && seen.add(original.getId()), "credit.lineSource", "返品元出荷明細が不正または重複しています。");
            int quantity = Checks.quantity(returnLine.getQuantity(), "返品数量");
            long already = dao.count("select sum(l.quantity) from CreditMemoLine l where l.shipmentLine=:line",
                    WholesaleDao.params("line", original));
            Checks.state(already + quantity <= original.getQuantity()
                    && already + quantity <= original.getReturnedQuantity(),
                    "credit.overReturn", "返品計上数量が出荷・受入数量を超えています。");
            Checks.state(returnLine.getUnitPrice().compareTo(original.getUnitPrice()) == 0
                    && returnLine.getTaxRate().compareTo(original.getTaxRate()) == 0,
                    "credit.price", "返品単価・税率は出荷時の条件と一致する必要があります。");
            CreditMemoLine line = new CreditMemoLine();
            line.setCreditMemo(memo);
            line.setSalesReturnLine(returnLine);
            line.setShipmentLine(original);
            line.setLineNumber(memo.getLines().size() + 1);
            line.setProductCode(original.getProductCode());
            line.setDescription(original.getProductName());
            line.setQuantity(quantity);
            line.setUnitPrice(original.getUnitPrice());
            line.setTaxRate(original.getTaxRate());
            line.setNetAmount(Money.amount(original.getUnitPrice(), quantity));
            memo.getLines().add(line);
        }
        if (invoice == null) {
            pricePendingShipmentCredit(memo, shipment);
        } else if ("FINALIZED".equals(invoice.getStatus())) {
            priceNewInvoiceCredit(memo, invoice);
        }
        dao.save(memo);
        memo.setNumber(documentNumber("CRM", memo.getId()));
        dao.flush();
        if (invoice != null) {
            if ("DRAFT".equals(invoice.getStatus())) {
                repriceDraftCredits(invoice);
            } else {
                postCredit(memo, invoice);
                new BillingLedger(dao).reconcile(invoice);
            }
        }
        audit(actor, "RETURN_CREDIT", memo, "return=" + source.getNumber() + ", amount=" + memo.getTotalAmount());
        dao.flush();
        return creditDetail(memo);
    }

    public List<Customer> listClosingCustomers(Date date, Actor actor) {
        return listClosingCustomers(date, null, 200, actor);
    }

    @SuppressWarnings("unchecked")
    public List<Customer> listClosingCustomers(Date date, Long afterId, int limit, Actor actor) {
        require(actor, "BILLING", "BATCH");
        Date day = Checks.date(date, "締日");
        Checks.state(!day.after(Dates.today()), "billing.future", "未来の締日は処理できません。");
        Checks.state(limit >= 1 && limit <= 200, "billing.pageSize", "対象顧客数は1から200です。");
        int dayNumber = Dates.calendar(day).get(java.util.Calendar.DAY_OF_MONTH);
        int closingDay = Dates.sameDay(day, Dates.monthEnd(day)) ? 31 : dayNumber;
        Checks.state(closingDay == 10 || closingDay == 20 || closingDay == 31,
                "billing.closingDate", "締日は10日・20日・末日です。");
        Map<String, Object> parameters = WholesaleDao.params("end", day, "closing", closingDay);
        parameters.put("afterId", afterId == null ? 0L : afterId);
        return (List<Customer>) dao.query("from Customer c where c.id>:afterId and c.closingDay=:closing "
                + "and (exists (select s.id from Shipment s where s.order.customer=c and s.status='CONFIRMED' "
                + "and s.invoice is null and s.shippedDate<=:end) or exists (select i.id from Invoice i "
                + "where i.customer=c and i.periodEnd=:end and i.status='DRAFT')) order by c.id",
                parameters).setMaxResults(limit).list();
    }

    public List<CreditMemo> listCredits(Long customerId, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        Customer customer = dao.get(Customer.class, customerId);
        List<CreditMemo> result = dao.list("from CreditMemo c where c.customer=:customer order by c.issuedDate desc,c.id desc",
                WholesaleDao.params("customer", customer));
        for (CreditMemo memo : result) {
            creditDetail(memo);
        }
        return result;
    }

    private Date validateClosingDate(Customer customer, Date periodEnd) {
        Date end = Checks.date(periodEnd, "締日");
        Checks.state(!end.after(Dates.today()), "billing.future", "未来の締日は処理できません。");
        Checks.state(Dates.sameDay(end, Dates.closingDate(end, customer.getClosingDay())),
                "billing.closingDate", "顧客の締日と一致していません。");
        return end;
    }

    private Invoice lockInvoice(Long id) {
        Long customerId = (Long) dao.query("select i.customer.id from Invoice i where i.id=:id",
                WholesaleDao.params("id", id)).uniqueResult();
        dao.lock(Customer.class, customerId);
        return dao.lock(Invoice.class, id);
    }

    private Invoice detail(Invoice invoice) {
        Hibernate.initialize(invoice.getLines());
        return invoice;
    }

    private CreditMemo creditDetail(CreditMemo memo) {
        Hibernate.initialize(memo.getLines());
        return memo;
    }

    private InvoiceLine snapshot(Invoice invoice, ShipmentLine source, int number) {
        InvoiceLine line = new InvoiceLine();
        line.setInvoice(invoice);
        line.setLineNumber(number);
        line.setShipmentLine(source);
        line.setShipmentNumber(source.getShipment().getNumber());
        line.setOrderNumber(source.getShipment().getOrder().getNumber());
        line.setShippedDate(source.getShipment().getShippedDate());
        line.setProductCode(source.getProductCode());
        line.setDescription(source.getProductName());
        line.setUnit(source.getUnit());
        line.setQuantity(Checks.quantity(source.getQuantity(), "出荷数量"));
        line.setUnitPrice(Checks.money(source.getUnitPrice(), "出荷単価", true));
        line.setTaxRate(source.getTaxRate());
        line.setNetAmount(source.getNetAmount());
        return line;
    }

    private String invoiceFingerprint(Invoice invoice) {
        List<String> values = new ArrayList<String>();
        values.add(invoice.getCustomer().getId().toString());
        values.add(invoice.getCustomerName());
        values.add(invoice.getBillingAddress());
        values.add(invoice.getPostalCode());
        values.add(invoice.getTaxRounding());
        values.add(Integer.toString(invoice.getPaymentTermDays()));
        values.add(Dates.format(invoice.getPeriodStart()));
        values.add(Dates.format(invoice.getPeriodEnd()));
        values.add(Dates.format(invoice.getDueDate()));
        values.add(invoice.getNetAmount().toPlainString());
        values.add(invoice.getTaxAmount().toPlainString());
        values.add(invoice.getTotalAmount().toPlainString());
        for (InvoiceLine line : invoice.getLines()) {
            values.add(line.getShipmentLine().getId().toString());
            values.add(Integer.toString(line.getLineNumber()));
            values.add(Integer.toString(line.getQuantity()));
            values.add(line.getUnitPrice().toPlainString());
            values.add(line.getTaxRate().toPlainString());
            values.add(line.getNetAmount().toPlainString());
            values.add(line.getDescription());
            values.add(line.getProductCode());
            values.add(line.getUnit());
            values.add(line.getOrderNumber());
            values.add(line.getShipmentNumber());
            values.add(Dates.format(line.getShippedDate()));
        }
        return Fingerprints.of(values.toArray(new String[values.size()]));
    }

    private String returnFingerprint(SalesReturn source) {
        List<String> values = new ArrayList<String>();
        values.add(source.getId().toString());
        values.add(source.getShipment().getId().toString());
        values.add(Dates.format(source.getReceivedDate()));
        for (SalesReturnLine line : source.getLines()) {
            values.add(line.getId().toString());
            values.add(line.getShipmentLine().getId().toString());
            values.add(Integer.toString(line.getQuantity()));
            values.add(line.getUnitPrice().toPlainString());
            values.add(line.getTaxRate().toPlainString());
        }
        return Fingerprints.of(values.toArray(new String[values.size()]));
    }

    private List<CreditMemo> invoiceCredits(Invoice invoice) {
        return dao.list("from CreditMemo c where c.invoice=:invoice order by c.id", WholesaleDao.params("invoice", invoice));
    }

    private BillingAmounts originalInvoice(Invoice invoice) {
        BillingAmounts result = new BillingAmounts(invoice.getTaxRounding());
        for (InvoiceLine line : invoice.getLines()) {
            result.add(line.getNetAmount(), line.getTaxRate());
        }
        return result;
    }

    private void addCredit(BillingAmounts amounts, CreditMemo memo) {
        for (CreditMemoLine line : memo.getLines()) {
            amounts.add(line.getNetAmount(), line.getTaxRate());
        }
    }

    private void price(CreditMemo memo, BillingAmounts original, BillingAmounts cumulative, BigDecimal priorTax) {
        BillingAmounts current = new BillingAmounts(memo.getTaxRounding());
        addCredit(current, memo);
        addCredit(cumulative, memo);
        BigDecimal tax = original.cumulativeCreditTax(cumulative).subtract(priorTax);
        Checks.state(tax.signum() >= 0, "credit.tax", "累計返品税額が不整合です。");
        memo.setNetAmount(current.net());
        memo.setTaxAmount(tax);
        memo.setTotalAmount(current.net().add(tax));
    }

    private void pricePendingShipmentCredit(CreditMemo memo, Shipment shipment) {
        BillingAmounts original = new BillingAmounts(memo.getTaxRounding());
        for (ShipmentLine line : shipment.getLines()) {
            original.add(line.getNetAmount(), line.getTaxRate());
        }
        BillingAmounts cumulative = new BillingAmounts(memo.getTaxRounding());
        List<CreditMemo> previous = dao.list("from CreditMemo c where c.salesReturn.shipment=:shipment order by c.id",
                WholesaleDao.params("shipment", shipment));
        for (CreditMemo credit : previous) {
            addCredit(cumulative, credit);
        }
        price(memo, original, cumulative, original.cumulativeCreditTax(cumulative));
    }

    private void repricePendingShipmentCredits(Shipment shipment) {
        String rounding = shipment.getOrder().getTaxRounding();
        BillingAmounts original = new BillingAmounts(rounding);
        for (ShipmentLine line : shipment.getLines()) {
            original.add(line.getNetAmount(), line.getTaxRate());
        }
        BillingAmounts cumulative = new BillingAmounts(rounding);
        BigDecimal previousTax = Money.ZERO;
        List<CreditMemo> credits = dao.list("from CreditMemo c where c.salesReturn.shipment=:shipment order by c.id",
                WholesaleDao.params("shipment", shipment));
        for (CreditMemo memo : credits) {
            Checks.state("PENDING".equals(memo.getStatus()) && memo.getInvoice() == null,
                    "credit.posted", "出荷の返品が既に請求計上されています。");
            memo.setTaxRounding(rounding);
            price(memo, original, cumulative, previousTax);
            previousTax = previousTax.add(memo.getTaxAmount());
        }
    }

    private void priceNewInvoiceCredit(CreditMemo memo, Invoice invoice) {
        BillingAmounts original = originalInvoice(invoice);
        BillingAmounts cumulative = new BillingAmounts(invoice.getTaxRounding());
        BigDecimal previousTax = Money.ZERO;
        for (CreditMemo credit : invoiceCredits(invoice)) {
            addCredit(cumulative, credit);
            previousTax = previousTax.add(credit.getTaxAmount());
        }
        price(memo, original, cumulative, previousTax);
    }

    private void repriceDraftCredits(Invoice invoice) {
        BillingAmounts original = originalInvoice(invoice);
        BillingAmounts cumulative = new BillingAmounts(invoice.getTaxRounding());
        BigDecimal previousTax = Money.ZERO;
        for (CreditMemo credit : invoiceCredits(invoice)) {
            Checks.state("PENDING".equals(credit.getStatus()), "credit.frozen", "計上済み返品額は変更できません。");
            credit.setTaxRounding(invoice.getTaxRounding());
            price(credit, original, cumulative, previousTax);
            previousTax = previousTax.add(credit.getTaxAmount());
        }
        new BillingLedger(dao).reconcile(invoice);
    }

    private void postCredit(CreditMemo memo, Invoice invoice) {
        memo.setStatus("APPLIED");
        memo.setPostedDate(memo.getIssuedDate().after(invoice.getIssuedDate())
                ? memo.getIssuedDate() : invoice.getIssuedDate());
    }
}
