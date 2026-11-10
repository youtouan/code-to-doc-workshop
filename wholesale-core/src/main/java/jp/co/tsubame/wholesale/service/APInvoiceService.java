package jp.co.tsubame.wholesale.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.APCreditInput;
import jp.co.tsubame.wholesale.common.APCreditLineInput;
import jp.co.tsubame.wholesale.common.APInvoiceInput;
import jp.co.tsubame.wholesale.common.APInvoiceLineInput;
import jp.co.tsubame.wholesale.common.APMatchInput;
import jp.co.tsubame.wholesale.common.APSearch;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Money;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.dao.APLedger;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.APCredit;
import jp.co.tsubame.wholesale.entity.APCreditLine;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.APInvoiceLine;
import jp.co.tsubame.wholesale.entity.APMatch;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.PurchaseReceiptLine;
import jp.co.tsubame.wholesale.entity.Supplier;

public class APInvoiceService extends BaseService {
    public APInvoice saveDraft(Long id, int expectedVersion, APInvoiceInput input, Actor actor) {
        require(actor, "BILLING");
        Checks.state(input != null, "ap.input", "仕入先請求の入力がありません。");
        Supplier supplier = dao.lock(Supplier.class, input.getSupplierId());
        APInvoice invoice;
        if (id == null) {
            invoice = new APInvoice();
            invoice.setNumber("NEW-" + UUID.randomUUID().toString());
            invoice.setSupplier(supplier);
            invoice.setSupplierName(supplier.getName());
            invoice.setSupplierAddress(supplier.getAddress());
            invoice.setTaxRounding(supplier.getTaxRounding());
            invoice.setCreatedBy(actor.getLogin());
            invoice.setCreatedById(actor.getUserId());
            invoice.setCreatedAt(new Date());
        } else {
            // Reject a different supplier before acquiring that supplier's invoice row.
            Long owner = (Long) dao.query("select i.supplier.id from APInvoice i where i.id=:id",
                    WholesaleDao.params("id", id)).uniqueResult();
            Checks.state(supplier.getId().equals(owner), "ap.supplierChange", "保存後の仕入先変更はできません。");
            invoice = dao.lock(APInvoice.class, id);
            Checks.version(invoice.getVersion(), expectedVersion);
            draft(invoice);
        }
        String reference = APLedger.reference(input.getSupplierInvoiceNumber(), "仕入先請求番号");
        Map<String, Object> parameters = WholesaleDao.params("supplier", supplier, "reference", reference);
        parameters.put("id", id == null ? -1L : id);
        Checks.state(dao.count("select count(i.id) from APInvoice i where i.supplier=:supplier "
                + "and i.supplierInvoiceNumber=:reference and i.id<>:id", parameters) == 0,
                "ap.duplicateInvoice", "同じ仕入先請求番号が既に登録されています。");
        Date invoiceDate = APLedger.pastDate(input.getInvoiceDate(), "請求日");
        Date dueDate = input.getDueDate() == null ? Dates.addDays(invoiceDate, supplier.getPaymentTermDays())
                : Checks.date(input.getDueDate(), "支払期日");
        Checks.state(!dueDate.before(invoiceDate) && !dueDate.after(Dates.addDays(invoiceDate, 365)),
                "ap.dueDate", "支払期日は請求日から365日以内です。");
        Checks.nonempty(input.getLines(), "請求明細");
        if (id != null) {
            invoice.getLines().clear();
            dao.flush();
        }
        invoice.setSupplierInvoiceNumber(reference);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setDueDate(dueDate);
        invoice.setNotes(Checks.optionalText(input.getNotes(), "備考", 1000));
        for (APInvoiceLineInput source : input.getLines()) {
            Checks.state(source != null, "ap.line", "請求明細が不正です。");
            Product product = dao.get(Product.class, source.getProductId());
            APInvoiceLine line = new APInvoiceLine();
            line.setInvoice(invoice);
            line.setLineNumber(invoice.getLines().size() + 1);
            line.setProduct(product);
            line.setProductCode(product.getCode());
            line.setDescription(source.getDescription() == null || source.getDescription().trim().length() == 0
                    ? product.getName() : Checks.text(source.getDescription(), "品名", 160));
            line.setUnit(product.getUnit());
            line.setQuantity(Checks.quantity(source.getQuantity(), "請求数量"));
            line.setUnitPrice(Checks.money(source.getUnitPrice(), "請求単価", true));
            line.setTaxRate(APLedger.rate(source.getTaxRate()));
            line.setNetAmount(Checks.money(Money.amount(line.getUnitPrice(), line.getQuantity()), "明細金額", true));
            invoice.getLines().add(line);
        }
        changed(invoice, actor);
        dao.save(invoice);
        if (id == null) { invoice.setNumber(documentNumber("API", invoice.getId())); }
        audit(actor, id == null ? "AP_INVOICE_CREATE" : "AP_INVOICE_REVISE", invoice,
                "supplierInvoice=" + reference + ", lines=" + invoice.getLines().size() + "; matching reset");
        dao.flush();
        return new APLedger(dao).detail(invoice);
    }

    public APInvoice replaceMatches(Long invoiceId, int expectedVersion, List<APMatchInput> inputs, Actor actor) {
        require(actor, "BILLING");
        Checks.state(inputs != null && inputs.size() <= 500, "ap.matches", "照合指定は0から500件です。");
        APInvoice invoice = new APLedger(dao).lockInvoice(invoiceId);
        Checks.version(invoice.getVersion(), expectedVersion);
        draft(invoice);
        Map<Long, APInvoiceLine> lines = new HashMap<Long, APInvoiceLine>();
        for (APInvoiceLine line : invoice.getLines()) {
            lines.put(line.getId(), line);
            line.getMatches().clear();
        }
        dao.flush();
        Set<String> pairs = new HashSet<String>();
        Map<Long, Long> receiptQuantities = new HashMap<Long, Long>();
        for (APMatchInput input : inputs) {
            Checks.state(input != null && lines.containsKey(input.getInvoiceLineId()),
                    "ap.matchLine", "この請求の明細を選択してください。");
            APInvoiceLine line = lines.get(input.getInvoiceLineId());
            PurchaseReceiptLine receipt = dao.get(PurchaseReceiptLine.class, input.getReceiptLineId());
            validateReceipt(invoice, line, receipt);
            Checks.state(pairs.add(line.getId() + ":" + receipt.getId()), "ap.duplicateMatch", "同じ請求・入荷明細の照合が重複しています。");
            int quantity = Checks.quantity(input.getQuantity(), "照合数量");
            Checks.state((long) line.getMatchedQuantity() + quantity <= line.getQuantity(),
                    "ap.invoiceOvermatch", "照合数量が請求明細数量を超えています。");
            Long previous = receiptQuantities.get(receipt.getId());
            long thisInvoice = (previous == null ? 0 : previous.longValue()) + quantity;
            Map<String, Object> parameters = WholesaleDao.params("receipt", receipt, "invoice", invoice);
            long otherClaims = dao.count("select sum(m.quantity) from APMatch m where m.receiptLine=:receipt "
                    + "and m.invoiceLine.invoice<>:invoice and m.invoiceLine.invoice.status<>'CANCELLED'", parameters);
            Checks.state(otherClaims + thisInvoice <= receipt.getAcceptedQuantity(),
                    "ap.receiptOvermatch", "他の請求を含めると検収済み入荷数量を超えています。");
            receiptQuantities.put(receipt.getId(), thisInvoice);
            APMatch match = new APMatch();
            match.setInvoiceLine(line);
            match.setReceiptLine(receipt);
            match.setQuantity(quantity);
            match.setReceiptNumber(receipt.getReceipt().getNumber());
            match.setPurchaseOrderNumber(receipt.getOrderLine().getOrder().getNumber());
            match.setReceiptDate(receipt.getReceipt().getReceiptDate());
            match.setReceiptUnitCost(receipt.getUnitCost());
            match.setOrderedUnitCost(receipt.getOrderLine().getUnitCost());
            line.getMatches().add(match);
        }
        changed(invoice, actor);
        dao.save(invoice);
        audit(actor, "AP_MATCH_REPLACE", invoice, "matches=" + inputs.size() + ", variance=" + invoice.getAbsoluteVarianceAmount());
        dao.flush();
        return new APLedger(dao).detail(invoice);
    }

    public APInvoice approveVariance(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "MANAGER");
        APInvoice invoice = new APLedger(dao).lockInvoice(id);
        Checks.version(invoice.getVersion(), expectedVersion);
        draft(invoice);
        validateMatching(invoice);
        APLedger.calculate(invoice);
        Checks.state(invoice.getAbsoluteVarianceAmount().signum() > 0, "ap.noVariance", "承認対象の差異がありません。");
        Checks.state(!actor.getUserId().equals(invoice.getCreatedById()) && !actor.getUserId().equals(invoice.getLastChangedById()),
                "ap.selfApproval", "起票者・最終変更者は差異を承認できません。");
        invoice.setVarianceReason(Checks.text(reason, "差異承認理由", 500));
        invoice.setVarianceStatus("APPROVED");
        invoice.setVarianceApprovedBy(actor.getLogin());
        invoice.setVarianceApprovedById(actor.getUserId());
        invoice.setVarianceApprovedAt(new Date());
        invoice.setReviewFingerprint(APLedger.fingerprint(invoice));
        audit(actor, "AP_VARIANCE_APPROVE", invoice, invoice.getVarianceReason());
        dao.flush();
        return new APLedger(dao).detail(invoice);
    }

    public APInvoice postInvoice(Long id, int expectedVersion, Actor actor) {
        return postInvoice(id, expectedVersion, Dates.today(), actor);
    }

    public APInvoice postInvoice(Long id, int expectedVersion, Date postingDate, Actor actor) {
        require(actor, "BILLING");
        APInvoice invoice = new APLedger(dao).lockInvoice(id);
        Checks.version(invoice.getVersion(), expectedVersion);
        draft(invoice);
        Date date = APLedger.pastDate(postingDate, "買掛計上日");
        Checks.state(!date.before(invoice.getInvoiceDate()), "ap.postingDate", "計上日は請求日以降です。");
        validateMatching(invoice);
        APLedger.calculate(invoice);
        if (invoice.getAbsoluteVarianceAmount().signum() > 0) {
            Checks.state("APPROVED".equals(invoice.getVarianceStatus()) && invoice.getReviewFingerprint() != null
                    && invoice.getReviewFingerprint().equals(APLedger.fingerprint(invoice)),
                    "ap.varianceApproval", "差異を別担当の管理者が承認してから計上してください。");
        }
        invoice.setStatus("POSTED");
        invoice.setPostedDate(date);
        invoice.setPostedBy(actor.getLogin());
        invoice.setPostedAt(new Date());
        audit(actor, "AP_INVOICE_POST", invoice, "amount=" + invoice.getTotalAmount() + ", date=" + Dates.format(date) + ", fully matched");
        dao.flush();
        return new APLedger(dao).detail(invoice);
    }

    public void cancelDraft(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "BILLING");
        APInvoice invoice = new APLedger(dao).lockInvoice(id);
        Checks.version(invoice.getVersion(), expectedVersion);
        draft(invoice);
        invoice.setStatus("CANCELLED");
        invoice.setCancelledBy(actor.getLogin());
        invoice.setCancelledAt(new Date());
        invoice.setCancellationReason(Checks.text(reason, "取消理由", 500));
        audit(actor, "AP_INVOICE_CANCEL", invoice, invoice.getCancellationReason() + "; receipt claims released");
        dao.flush();
    }

    public APInvoice getInvoice(Long id, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        return new APLedger(dao).detail(dao.get(APInvoice.class, id));
    }

    public Page<APInvoice> searchInvoices(APSearch search, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        Checks.state(search != null, "ap.search", "検索条件を指定してください。");
        Map<String, Object> parameters = WholesaleDao.params("text", search.getLikeText());
        String where = " where (i.number like :text escape '!' or i.supplierInvoiceNumber like :text escape '!' "
                + "or i.supplierName like :text escape '!')";
        if (search.getSupplierId() != null) { where += " and i.supplier.id=:supplier"; parameters.put("supplier", search.getSupplierId()); }
        if (search.getStatus().length() > 0) {
            invoiceStatus(search.getStatus());
            where += " and i.status=:status"; parameters.put("status", search.getStatus());
        }
        if (search.getFrom() != null) { where += " and i.invoiceDate>=:from"; parameters.put("from", search.getFrom()); }
        if (search.getTo() != null) { where += " and i.invoiceDate<=:to"; parameters.put("to", search.getTo()); }
        Page<APInvoice> result = dao.page("from APInvoice i" + where + " order by i.invoiceDate desc,i.id desc",
                "select count(i.id) from APInvoice i" + where, parameters, search);
        for (APInvoice invoice : result.getItems()) { new APLedger(dao).detail(invoice); }
        return result;
    }

    public APCredit proposeCredit(Long invoiceId, int expectedInvoiceVersion, APCreditInput input, Actor actor) {
        require(actor, "BILLING");
        Checks.state(input != null, "ap.creditInput", "値引情報を指定してください。");
        APLedger ledger = new APLedger(dao);
        APInvoice invoice = ledger.lockInvoice(invoiceId);
        Checks.version(invoice.getVersion(), expectedInvoiceVersion);
        Checks.state("POSTED".equals(invoice.getStatus()), "ap.creditInvoice", "計上済み請求だけを値引できます。");
        String reference = APLedger.reference(input.getSupplierCreditNumber(), "仕入先値引番号");
        Checks.state(dao.count("select count(c.id) from APCredit c where c.supplier=:supplier and c.supplierCreditNumber=:reference",
                WholesaleDao.params("supplier", invoice.getSupplier(), "reference", reference)) == 0,
                "ap.duplicateCredit", "同じ仕入先値引番号が既に登録されています。");
        Date date = APLedger.pastDate(input.getCreditDate(), "値引日");
        Checks.state(!date.before(invoice.getInvoiceDate()), "ap.creditDate", "値引日は元請求日以降です。");
        Checks.nonempty(input.getLines(), "値引明細");
        APCredit credit = new APCredit();
        credit.setNumber("NEW-" + UUID.randomUUID().toString());
        credit.setSupplier(invoice.getSupplier());
        credit.setInvoice(invoice);
        credit.setSupplierCreditNumber(reference);
        credit.setCreditDate(date);
        credit.setReason(Checks.text(input.getReason(), "財務値引理由", 500));
        credit.setCreatedBy(actor.getLogin());
        credit.setCreatedById(actor.getUserId());
        credit.setCreatedAt(new Date());
        Set<Long> selected = new HashSet<Long>();
        for (APCreditLineInput item : input.getLines()) {
            Checks.state(item != null && selected.add(item.getInvoiceLineId()), "ap.creditLine", "値引明細が不正または重複しています。");
            APInvoiceLine source = dao.get(APInvoiceLine.class, item.getInvoiceLineId());
            Checks.state(source.getInvoice().getId().equals(invoiceId), "ap.creditSource", "別請求の明細は値引できません。");
            BigDecimal net = Checks.money(item.getNetAmount(), "税抜値引額", false);
            BigDecimal claimed = ledger.sum("select sum(l.netAmount) from APCreditLine l where l.invoiceLine=:line "
                    + "and l.credit.status<>'CANCELLED'", WholesaleDao.params("line", source));
            Checks.state(claimed.add(net).compareTo(source.getNetAmount()) <= 0,
                    "ap.overCredit", "申請中分を含む累計値引額が請求明細金額を超えています。");
            APCreditLine line = new APCreditLine();
            line.setCredit(credit);
            line.setInvoiceLine(source);
            line.setLineNumber(credit.getLines().size() + 1);
            line.setDescription(source.getDescription());
            line.setNetAmount(net);
            line.setTaxRate(source.getTaxRate());
            credit.getLines().add(line);
        }
        ledger.priceCredit(credit);
        dao.save(credit);
        credit.setNumber(documentNumber("APC", credit.getId()));
        audit(actor, "AP_CREDIT_PROPOSE", credit, credit.getReason() + "; financial allowance only, no stock movement");
        dao.flush();
        return ledger.detail(credit);
    }

    public APCredit approveCredit(Long id, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        APLedger ledger = new APLedger(dao);
        APCredit credit = ledger.lockCredit(id);
        Checks.version(credit.getVersion(), expectedVersion);
        Checks.state("DRAFT".equals(credit.getStatus()), "ap.creditNotDraft", "申請中値引だけを承認できます。");
        Checks.state(!actor.getUserId().equals(credit.getCreatedById()), "ap.selfApproval", "自分で申請した値引は承認できません。");
        APInvoice invoice = credit.getInvoice();
        ledger.reconcile(invoice);
        ledger.priceCredit(credit);
        Checks.state(credit.getTotalAmount().compareTo(invoice.getOutstandingAmount()) <= 0,
                "ap.creditOutstanding", "値引額が未払残高を超えます。必要なら先に誤った支払を取消してください。");
        credit.setStatus("POSTED");
        credit.setPostedDate(Dates.today());
        credit.setApprovedBy(actor.getLogin());
        credit.setApprovedById(actor.getUserId());
        credit.setApprovedAt(new Date());
        dao.flush();
        ledger.reconcile(invoice);
        audit(actor, "AP_CREDIT_APPROVE", credit, "amount=" + credit.getTotalAmount() + "; financial allowance");
        dao.flush();
        return ledger.detail(credit);
    }

    public void cancelCredit(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "BILLING");
        APCredit credit = new APLedger(dao).lockCredit(id);
        Checks.version(credit.getVersion(), expectedVersion);
        Checks.state("DRAFT".equals(credit.getStatus()), "ap.creditNotDraft", "未計上値引だけを取消できます。");
        credit.setStatus("CANCELLED");
        credit.setCancelledBy(actor.getLogin());
        credit.setCancelledAt(new Date());
        credit.setCancellationReason(Checks.text(reason, "値引取消理由", 500));
        audit(actor, "AP_CREDIT_CANCEL", credit, credit.getCancellationReason());
        dao.flush();
    }

    public APCredit getCredit(Long id, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        return new APLedger(dao).detail(dao.get(APCredit.class, id));
    }

    public Page<APCredit> searchCredits(APSearch search, Actor actor) {
        require(actor, "BILLING", "MANAGER");
        Checks.state(search != null, "ap.search", "検索条件を指定してください。");
        Map<String, Object> parameters = WholesaleDao.params("text", search.getLikeText());
        String where = " where (c.number like :text escape '!' or c.supplierCreditNumber like :text escape '!')";
        if (search.getSupplierId() != null) { where += " and c.supplier.id=:supplier"; parameters.put("supplier", search.getSupplierId()); }
        if (search.getStatus().length() > 0) { invoiceStatus(search.getStatus()); where += " and c.status=:status"; parameters.put("status", search.getStatus()); }
        if (search.getFrom() != null) { where += " and c.creditDate>=:from"; parameters.put("from", search.getFrom()); }
        if (search.getTo() != null) { where += " and c.creditDate<=:to"; parameters.put("to", search.getTo()); }
        Page<APCredit> result = dao.page("from APCredit c" + where + " order by c.id desc",
                "select count(c.id) from APCredit c" + where, parameters, search);
        for (APCredit credit : result.getItems()) { new APLedger(dao).detail(credit); }
        return result;
    }

    private void draft(APInvoice invoice) {
        Checks.state("DRAFT".equals(invoice.getStatus()), "ap.notDraft", "下書き仕入先請求だけを変更できます。");
    }

    private void changed(APInvoice invoice, Actor actor) {
        invoice.setChangeNumber(invoice.getChangeNumber() + 1);
        invoice.setLastChangedBy(actor.getLogin());
        invoice.setLastChangedById(actor.getUserId());
        invoice.setLastChangedAt(new Date());
        APLedger.invalidateReview(invoice);
    }

    private void invoiceStatus(String status) {
        Checks.state("DRAFT".equals(status) || "POSTED".equals(status) || "CANCELLED".equals(status),
                "ap.status", "買掛伝票状態が不正です。");
    }

    private void validateReceipt(APInvoice invoice, APInvoiceLine line, PurchaseReceiptLine receipt) {
        Checks.state(receipt.getReceipt().getOrder().getSupplier().getId().equals(invoice.getSupplier().getId()),
                "ap.matchSupplier", "別仕入先の入荷は照合できません。");
        Checks.state(receipt.getOrderLine().getProduct().getId().equals(line.getProduct().getId()),
                "ap.matchProduct", "請求商品と入荷商品が一致していません。");
        Checks.state(receipt.getAcceptedQuantity() > 0 && receipt.getStockReceipt() != null,
                "ap.notAccepted", "実際に検収入庫した数量だけを照合できます。");
        Checks.state(!receipt.getReceipt().getReceiptDate().after(invoice.getInvoiceDate()),
                "ap.receiptDate", "請求日より後の入荷は照合できません。");
    }

    private void validateMatching(APInvoice invoice) {
        Checks.state(invoice.isFullyMatched(), "ap.incompleteMatch", "全請求数量を検収済み入荷と照合してください。");
        for (APInvoiceLine line : invoice.getLines()) {
            for (APMatch match : line.getMatches()) {
                PurchaseReceiptLine source = match.getReceiptLine();
                validateReceipt(invoice, line, source);
                Checks.state(source.getUnitCost().compareTo(match.getReceiptUnitCost()) == 0
                        && source.getOrderLine().getUnitCost().compareTo(match.getOrderedUnitCost()) == 0,
                        "ap.sourceChanged", "入荷・発注原価が照合時から変わっています。再照合してください。");
                long total = dao.count("select sum(m.quantity) from APMatch m where m.receiptLine=:receipt "
                        + "and m.invoiceLine.invoice.status<>'CANCELLED'", WholesaleDao.params("receipt", source));
                Checks.state(total <= source.getAcceptedQuantity(), "ap.receiptOvermatch", "検収数量を超える請求照合があります。");
            }
        }
    }
}
