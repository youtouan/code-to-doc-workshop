package jp.co.tsubame.wholesale.service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Money;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.QuotationCommand;
import jp.co.tsubame.wholesale.common.QuotationLineCommand;
import jp.co.tsubame.wholesale.common.QuotationRules;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.TaxAmounts;
import jp.co.tsubame.wholesale.dao.QuotationLedger;
import jp.co.tsubame.wholesale.dao.QuotationLocks;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Quotation;
import jp.co.tsubame.wholesale.entity.QuotationEvent;
import jp.co.tsubame.wholesale.entity.QuotationLine;
import jp.co.tsubame.wholesale.entity.QuotationRevision;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.Warehouse;

public class QuotationService extends BaseService {
    private CatalogService catalogService;
    private OrderService orderService;
    public void setCatalogService(CatalogService value) { catalogService = value; }
    public void setOrderService(OrderService value) { orderService = value; }

    public Quotation getQuotation(Long id, Actor actor) {
        reader(actor);
        return QuotationLocks.initialize(dao.get(Quotation.class, id));
    }

    public QuotationRevision getRevision(Long id, int revisionNumber, Actor actor) {
        Quotation quote = getQuotation(id, actor);
        for (QuotationRevision revision : quote.getRevisions()) {
            if (revision.getRevisionNumber() == revisionNumber) { return revision; }
        }
        throw new jp.co.tsubame.wholesale.common.BusinessException("notFound", "見積版が見つかりません。");
    }

    public List<QuotationEvent> listEvents(Long id, Actor actor) {
        Quotation quote = getQuotation(id, actor);
        return dao.list("from QuotationEvent e where e.quotation=:quote order by e.id",
                WholesaleDao.params("quote", quote));
    }

    public Page<Quotation> searchQuotations(Search search, Actor actor) {
        reader(actor);
        Checks.state(search != null, "validation.search", "検索条件を指定してください。");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        String where = " from Quotation q join q.revisions r where r.revisionNumber=q.revisionNumber";
        if (search.getText().length() > 0) {
            where += " and (lower(q.number) like :text escape '!' or lower(r.customerName) like :text escape '!'"
                    + " or lower(r.externalReference) like :text escape '!')";
            parameters.put("text", search.getLikeText().toLowerCase(java.util.Locale.ROOT));
        }
        if (search.getStatus().length() > 0) {
            Checks.state(search.getStatus().matches("DRAFT|SUBMITTED|APPROVED|ACCEPTED|WITHDRAWN|REJECTED|EXPIRED|CANCELLED|CONVERTED"),
                    "quotation.status", "見積状態が不正です。");
            where += " and q.status=:status";
            parameters.put("status", search.getStatus());
        }
        if (search.getCustomerId() != null) {
            where += " and q.customer.id=:customer";
            parameters.put("customer", search.getCustomerId());
        }
        if (search.getWarehouseId() != null) {
            where += " and q.warehouse.id=:warehouse";
            parameters.put("warehouse", search.getWarehouseId());
        }
        if (search.getFrom() != null) { where += " and r.quoteDate>=:from"; parameters.put("from", search.getFrom()); }
        if (search.getTo() != null) { where += " and r.quoteDate<=:to"; parameters.put("to", search.getTo()); }
        Checks.state(search.getFrom() == null || search.getTo() == null || !search.getTo().before(search.getFrom()),
                "validation.interval", "検索期間が逆転しています。");
        Page<Quotation> result = dao.page("select q" + where + " order by q.id desc",
                "select count(q.id)" + where, parameters, search);
        for (Quotation quote : result.getItems()) { QuotationLocks.initialize(quote); }
        return result;
    }

    public Quotation saveDraft(Long id, int expectedVersion, QuotationCommand input, Actor actor) {
        writer(actor);
        return saveRevision(id, expectedVersion, input, "下書き保存", false, actor);
    }

    public Quotation revise(Long id, int expectedVersion, QuotationCommand input, String reason, Actor actor) {
        writer(actor);
        Checks.state(id != null, "validation.id", "改訂元見積を指定してください。");
        return saveRevision(id, expectedVersion, input, Checks.text(reason, "改訂理由", 500), true, actor);
    }

    public Quotation submit(Long id, int expectedVersion, Actor actor) {
        writer(actor);
        Quotation quote = locked(id, expectedVersion);
        Checks.state(quote.isEditable(), "quotation.notDraft", "下書き・取下げ・差戻しの見積のみ申請できます。");
        QuotationRules.active(quote);
        QuotationRules.current(quote);
        QuotationRules.integrity(quote);
        String previous = quote.getStatus();
        clearApproval(quote);
        quote.setSubmittedById(actor.getUserId());
        quote.setSubmittedBy(actor.getLogin());
        quote.setSubmittedAt(new Date());
        quote.setStatus("SUBMITTED");
        return finish(quote, actor, "QUOTE_SUBMIT", previous, "");
    }

    public Quotation approve(Long id, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        Long actorId = QuotationRules.actorId(actor);
        Quotation quote = locked(id, expectedVersion);
        state(quote, "SUBMITTED");
        Checks.state(!actorId.equals(quote.getCreatedById()) && !actorId.equals(quote.getSubmittedById())
                && !actorId.equals(quote.getCurrentRevision().getAuthoredById()),
                "approval.self", "作成者・改訂者・申請者は見積を承認できません。");
        QuotationRules.active(quote);
        QuotationRules.current(quote);
        QuotationRules.integrity(quote);
        quote.setApprovedById(actorId);
        quote.setApprovedBy(actor.getLogin());
        quote.setApprovedAt(new Date());
        quote.setApprovedFingerprint(quote.getCurrentRevision().getFingerprint());
        quote.setStatus("APPROVED");
        return finish(quote, actor, "QUOTE_APPROVE", "SUBMITTED", "");
    }

    public Quotation accept(Long id, int expectedVersion, Date acceptedOn, String customerReference, Actor actor) {
        writer(actor);
        Quotation quote = locked(id, expectedVersion);
        state(quote, "APPROVED");
        QuotationRules.active(quote);
        QuotationRules.current(quote);
        QuotationRules.approvedIntegrity(quote);
        Date date = Checks.date(acceptedOn, "顧客承諾日");
        Checks.state(!date.before(quote.getQuoteDate()) && !date.after(quote.getValidUntil())
                && !date.after(Dates.today()), "quotation.acceptanceDate", "顧客承諾日は見積期間内かつ本日以前にしてください。");
        quote.setAcceptanceReference(Checks.text(customerReference, "顧客承諾参照番号", 120));
        quote.setAcceptedOn(date);
        quote.setAcceptedById(actor.getUserId());
        quote.setAcceptedBy(actor.getLogin());
        quote.setAcceptedAt(new Date());
        quote.setStatus("ACCEPTED");
        return finish(quote, actor, "QUOTE_ACCEPT", "APPROVED",
                Dates.format(date) + " reference=" + quote.getAcceptanceReference());
    }

    public Quotation withdraw(Long id, int expectedVersion, String reason, Actor actor) {
        writer(actor);
        Quotation quote = locked(id, expectedVersion);
        Checks.state("SUBMITTED".equals(quote.getStatus()) || "APPROVED".equals(quote.getStatus())
                || "ACCEPTED".equals(quote.getStatus()), "quotation.withdrawState", "申請・承認・承諾済みの見積のみ取下げできます。");
        ownerOrManager(quote, actor);
        String previous = quote.getStatus();
        clearApproval(quote);
        quote.setStatus("WITHDRAWN");
        return finish(quote, actor, "QUOTE_WITHDRAW", previous, Checks.text(reason, "取下理由", 500));
    }

    public Quotation reject(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "MANAGER");
        QuotationRules.actorId(actor);
        Quotation quote = locked(id, expectedVersion);
        state(quote, "SUBMITTED");
        clearApproval(quote);
        quote.setStatus("REJECTED");
        return finish(quote, actor, "QUOTE_REJECT", "SUBMITTED", Checks.text(reason, "差戻理由", 500));
    }

    public Quotation cancel(Long id, int expectedVersion, String reason, Actor actor) {
        writer(actor);
        Quotation quote = locked(id, expectedVersion);
        Checks.state(!"CONVERTED".equals(quote.getStatus()) && !"CANCELLED".equals(quote.getStatus()),
                "quotation.terminal", "受注変換済み・取消済みの見積は取消できません。");
        ownerOrManager(quote, actor);
        String previous = quote.getStatus();
        quote.setStatus("CANCELLED");
        return finish(quote, actor, "QUOTE_CANCEL", previous, Checks.text(reason, "取消理由", 500));
    }

    public Quotation expire(Long id, int expectedVersion, Actor actor) {
        require(actor, "SALES", "MANAGER", "BATCH");
        QuotationRules.actorId(actor);
        Quotation quote = locked(id, expectedVersion);
        Checks.state(!"CONVERTED".equals(quote.getStatus()) && !"CANCELLED".equals(quote.getStatus())
                && !"EXPIRED".equals(quote.getStatus()), "quotation.terminal", "終了済みの見積です。");
        Checks.state(quote.getValidUntil().before(Dates.today()), "quotation.notExpired", "有効期間内の見積です。");
        String previous = quote.getStatus();
        quote.setStatus("EXPIRED");
        return finish(quote, actor, "QUOTE_EXPIRE", previous, "validUntil=" + Dates.format(quote.getValidUntil()));
    }

    public SalesOrder convertToOrder(Long id, int expectedVersion, Actor actor) {
        writer(actor);
        return orderService.convertApprovedQuotation(id, expectedVersion, actor);
    }

    private Quotation saveRevision(Long id, int expectedVersion, QuotationCommand input,
            String reason, boolean explicitRevision, Actor actor) {
        QuotationRules.command(input);
        Quotation quote = QuotationLocks.lock(dao, id, input);
        String previous = quote == null ? "NEW" : quote.getStatus();
        if (quote == null) {
            Checks.version(0, expectedVersion);
            quote = new Quotation();
            quote.setNumber("NEW-" + UUID.randomUUID().toString());
            quote.setCreatedById(actor.getUserId());
            quote.setCreatedBy(actor.getLogin());
            quote.setCreatedAt(new Date());
        } else {
            Checks.version(quote.getVersion(), expectedVersion);
            Checks.state(!"CONVERTED".equals(previous) && !"CANCELLED".equals(previous),
                    "quotation.terminal", "受注変換済み・取消済みの見積は改訂できません。");
            Checks.state(explicitRevision || quote.isEditable(), "quotation.notDraft", "確定した提案は改訂操作を使用してください。");
        }
        Checks.state(quote.getRevisionNumber() < 100, "quotation.revisionLimit", "100版を超える場合は別見積を作成してください。");
        Customer customer = dao.get(Customer.class, input.getCustomerId());
        Warehouse warehouse = dao.get(Warehouse.class, input.getWarehouseId());
        Checks.state(customer.isActive() && !customer.isOnHold(), "quotation.customer", "得意先が無効または取引保留中です。");
        Checks.state(warehouse.isActive(), "quotation.warehouse", "見積の倉庫が無効です。");
        quote.setCustomer(customer);
        quote.setWarehouse(warehouse);
        quote.setRevisionNumber(quote.getRevisionNumber() + 1);
        quote.setStatus("DRAFT");
        quote.setSubmittedById(null);
        quote.setSubmittedBy(null);
        quote.setSubmittedAt(null);
        clearApproval(quote);
        quote.setUpdatedAt(new Date());
        dao.save(quote);
        if (id == null) { quote.setNumber(documentNumber("QT", quote.getId())); }
        QuotationRevision revision = new QuotationRevision();
        revision.setQuotation(quote);
        revision.setRevisionNumber(quote.getRevisionNumber());
        revision.setWarehouse(warehouse);
        revision.setCustomerName(customer.getName());
        revision.setQuoteDate(Dates.day(input.getQuoteDate()));
        revision.setValidUntil(Dates.day(input.getValidUntil()));
        revision.setRequestedDate(Dates.day(input.getRequestedDate()));
        String address = Checks.optionalText(input.getDeliveryAddress(), "納入先", 300);
        revision.setDeliveryAddress(address.length() == 0 ? customer.getAddress() : address);
        revision.setExternalReference(Checks.optionalText(input.getExternalReference(), "客先参照番号", 80));
        revision.setNotes(Checks.optionalText(input.getNotes(), "備考", 1000));
        revision.setTaxRounding(customer.getTaxRounding());
        revision.setAuthoredById(actor.getUserId());
        revision.setAuthoredBy(actor.getLogin());
        revision.setAuthoredAt(new Date());
        revision.setChangeReason(reason);
        TaxAmounts totals = new TaxAmounts(revision.getTaxRounding());
        int number = 0;
        for (QuotationLineCommand source : input.getLines()) {
            Product product = dao.get(Product.class, source.getProductId());
            Checks.state(product.isActive(), "quotation.product", "無効な商品は見積できません。");
            Checks.state(source.getQuantity() % product.getPackSize() == 0, "quotation.pack", "数量は商品入数の倍数にしてください。");
            QuotationLine line = new QuotationLine();
            line.setRevision(revision);
            line.setLineNumber(++number);
            line.setProduct(product);
            line.setProductCode(product.getCode());
            line.setProductName(product.getName());
            line.setUnit(product.getUnit());
            line.setPackSize(product.getPackSize());
            line.setQuantity(source.getQuantity());
            line.setTaxRate(Money.rate(product.getTaxCategory()));
            line.setCatalogUnitPrice(catalogService.price(customer.getId(), product.getId(),
                    source.getQuantity(), revision.getQuoteDate(), actor));
            line.setNegotiated(source.getNegotiatedUnitPrice() != null);
            line.setUnitPrice(line.isNegotiated() ? Checks.money(source.getNegotiatedUnitPrice(), "交渉単価", true)
                    : line.getCatalogUnitPrice());
            line.setNegotiationReason(line.isNegotiated() ? Checks.text(source.getNegotiationReason(), "交渉理由", 300) : "");
            totals.add(line.getNetAmount(), line.getTaxRate());
            revision.getLines().add(line);
        }
        QuotationRules.amounts(totals);
        revision.setNetAmount(totals.getNetAmount());
        revision.setTaxAmount(totals.getTaxAmount());
        revision.setFingerprint(QuotationRules.fingerprint(revision));
        quote.getRevisions().add(revision);
        dao.save(revision);
        return finish(quote, actor, explicitRevision ? "QUOTE_REVISE" : "QUOTE_SAVE", previous, reason);
    }

    private Quotation locked(Long id, int expectedVersion) {
        Quotation quote = QuotationLocks.lock(dao, id);
        Checks.version(quote.getVersion(), expectedVersion);
        return quote;
    }

    private Quotation finish(Quotation quote, Actor actor, String operation, String previous, String reason) {
        quote.setUpdatedAt(new Date(Math.max(System.currentTimeMillis(), quote.getUpdatedAt().getTime() + 1L)));
        QuotationLedger.event(dao, quote, actor, operation, previous, reason);
        audit(actor, operation, quote, "revision=" + quote.getRevisionNumber() + " " + reason);
        dao.flush();
        return QuotationLocks.initialize(quote);
    }

    private void clearApproval(Quotation quote) {
        quote.setApprovedById(null);
        quote.setApprovedBy(null);
        quote.setApprovedAt(null);
        quote.setApprovedFingerprint(null);
        quote.setAcceptedById(null);
        quote.setAcceptedBy(null);
        quote.setAcceptedAt(null);
        quote.setAcceptedOn(null);
        quote.setAcceptanceReference("");
    }

    private void ownerOrManager(Quotation quote, Actor actor) {
        Checks.state(actor.getUserId().equals(quote.getCreatedById()) || actor.hasRole("MANAGER") || actor.hasRole("ADMIN"),
                "quotation.owner", "起票者または管理者のみ操作できます。");
    }

    private void state(Quotation quote, String state) {
        Checks.state(state.equals(quote.getStatus()), "quotation.state", "現在の見積状態では操作できません。");
    }

    private void writer(Actor actor) {
        require(actor, "SALES", "MANAGER");
        QuotationRules.actorId(actor);
    }

    private void reader(Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "BILLING", "MANAGER", "BATCH");
    }
}
