package jp.co.tsubame.wholesale.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Money;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.common.OrderLineInput;
import jp.co.tsubame.wholesale.common.OrderAmendmentRules;
import jp.co.tsubame.wholesale.common.OrderStates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.TaxAmounts;
import jp.co.tsubame.wholesale.common.QuotationRules;
import jp.co.tsubame.wholesale.dao.OrderAmendmentLocks;
import jp.co.tsubame.wholesale.dao.QuotationLedger;
import jp.co.tsubame.wholesale.dao.QuotationLocks;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.dao.OrderLocks;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.OrderAmendment;
import jp.co.tsubame.wholesale.entity.OrderAmendmentLine;
import jp.co.tsubame.wholesale.entity.Quotation;
import jp.co.tsubame.wholesale.entity.QuotationLine;
import jp.co.tsubame.wholesale.entity.QuotationRevision;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.Warehouse;
import org.hibernate.Hibernate;

public class OrderService extends BaseService {
    private CatalogService catalogService;
    private InventoryService inventoryService;
    private CreditService creditService;

    public void setCatalogService(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public void setInventoryService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public void setCreditService(CreditService creditService) {
        this.creditService = creditService;
    }

    public SalesOrder saveDraft(Long id, int expectedVersion, OrderInput input, Actor actor) {
        return saveDraftInternal(id, expectedVersion, input, actor, null);
    }

    private SalesOrder saveDraftInternal(Long id, int expectedVersion, OrderInput input, Actor actor,
            QuotationRevision trustedRevision) {
        require(actor, "SALES", "MANAGER");
        Checks.state(input != null, "order.input", "受注内容がありません。");
        Checks.nonempty(input.getLines(), "受注明細");
        List<Long> referenceProducts = new ArrayList<Long>();
        for (OrderLineInput line : input.getLines()) {
            Checks.state(line != null, "order.line", "受注明細が不正です。");
            referenceProducts.add(line.getProductId());
        }
        dao.lockReferences(Collections.singleton(input.getWarehouseId()), referenceProducts);
        Customer customer = dao.lock(Customer.class, input.getCustomerId());
        Warehouse warehouse = dao.get(Warehouse.class, input.getWarehouseId());
        Checks.state(customer.isActive() && !customer.isOnHold(), "order.customer", "この得意先の受注は停止中です。");
        Checks.state(warehouse.isActive(), "order.warehouse", "停止中の倉庫は指定できません。");
        Date orderDate = Checks.date(input.getOrderDate(), "受注日");
        Date requestedDate = Checks.date(input.getRequestedDate(), "納期");
        Checks.state(!requestedDate.before(orderDate), "order.deliveryDate", "納期は受注日以降にしてください。");
        Checks.state(!orderDate.after(Dates.today()), "order.future", "未来日の受注は登録できません。");
        Checks.state(!requestedDate.after(Dates.addDays(orderDate, 365)), "order.deliveryHorizon", "納期は受注日から365日以内です。");
        SalesOrder order;
        if (id == null) {
            order = new SalesOrder();
            order.setNumber("NEW-" + UUID.randomUUID().toString());
            order.setStatus("DRAFT");
            order.setCreatedBy(actor.getLogin());
            order.setCreatedAt(new Date());
        } else {
            order = dao.lock(SalesOrder.class, id);
            Checks.version(order.getVersion(), expectedVersion);
            Checks.state(order.isEditable(), "order.notDraft", "下書きの受注だけ編集できます。");
            Checks.state(order.getCustomer().getId().equals(customer.getId()), "order.customerChange",
                    "保存後の得意先変更はできません。新しい受注を作成してください。");
            order.getLines().clear();
            dao.flush();
        }
        order.setCustomer(customer);
        order.setWarehouse(warehouse);
        order.setCustomerName(trustedRevision == null ? customer.getName() : trustedRevision.getCustomerName());
        order.setDeliveryAddress(input.getDeliveryAddress() == null || input.getDeliveryAddress().trim().length() == 0
                ? customer.getAddress() : Checks.text(input.getDeliveryAddress(), "納入先住所", 300));
        order.setOrderDate(orderDate);
        order.setRequestedDate(requestedDate);
        order.setExternalReference(Checks.optionalText(input.getExternalReference(), "客先注文番号", 80));
        order.setNotes(Checks.optionalText(input.getNotes(), "備考", 1000));
        order.setTaxRounding(trustedRevision == null ? customer.getTaxRounding() : trustedRevision.getTaxRounding());
        Map<Long, QuotationLine> trustedLines = new HashMap<Long, QuotationLine>();
        if (trustedRevision != null) {
            for (QuotationLine line : trustedRevision.getLines()) { trustedLines.put(line.getProduct().getId(), line); }
            Checks.state(trustedLines.size() == input.getLines().size(), "quotation.snapshotChanged", "承認済み見積明細が一致しません。");
        }
        Set<Long> products = new HashSet<Long>();
        int position = 0;
        for (OrderLineInput source : input.getLines()) {
            Checks.state(source != null, "order.line", "受注明細が不正です。");
            Product product = dao.get(Product.class, source.getProductId());
            Checks.state(products.add(product.getId()), "order.duplicateProduct", "同じ商品は1行にまとめてください。");
            Checks.state(product.isActive(), "order.product", "停止中の商品は受注できません。");
            int quantity = Checks.quantity(source.getQuantity(), "受注数量");
            Checks.state(quantity % product.getPackSize() == 0, "order.pack",
                    product.getCode() + "の数量は入数" + product.getPackSize() + "の倍数にしてください。");
            SalesOrderLine line = new SalesOrderLine();
            QuotationLine trusted = trustedRevision == null ? null : trustedLines.get(product.getId());
            Checks.state(trustedRevision == null || (trusted != null && trusted.getQuantity() == quantity),
                    "quotation.snapshotChanged", "承認済み見積数量が一致しません。");
            line.setOrder(order);
            line.setLineNumber(++position);
            line.setProduct(product);
            line.setProductCode(trusted == null ? product.getCode() : trusted.getProductCode());
            line.setProductName(trusted == null ? product.getName() : trusted.getProductName());
            line.setUnit(trusted == null ? product.getUnit() : trusted.getUnit());
            line.setPackSize(trusted == null ? product.getPackSize() : trusted.getPackSize());
            line.setQuantity(quantity);
            line.setTaxRate(trusted == null ? Money.rate(product.getTaxCategory()) : trusted.getTaxRate());
            if (trusted != null) {
                line.setUnitPrice(trusted.getUnitPrice());
                line.setPriceReason(trusted.getNegotiationReason());
            } else if (source.getPriceOverride() != null) {
                require(actor, "MANAGER");
                line.setUnitPrice(Checks.money(source.getPriceOverride(), "個別単価", true));
                line.setPriceReason(Checks.text(source.getPriceReason(), "単価変更理由", 300));
            } else {
                line.setUnitPrice(catalogService.price(customer.getId(), product.getId(), quantity, orderDate, actor));
                line.setPriceReason("");
            }
            order.getLines().add(line);
        }
        calculateTotals(order);
        dao.save(order);
        if (id == null) {
            order.setNumber(documentNumber("SO", order.getId()));
        }
        audit(actor, id == null ? "ORDER_CREATE" : "ORDER_EDIT", order, "lines=" + order.getLines().size());
        dao.flush();
        return order;
    }

    public SalesOrder convertApprovedQuotation(Long id, int expectedVersion, Actor actor) {
        require(actor, "SALES", "MANAGER");
        QuotationRules.actorId(actor);
        Quotation quote = QuotationLocks.lock(dao, id);
        if ("CONVERTED".equals(quote.getStatus())) {
            Checks.state(quote.getConvertedOrder() != null, "quotation.conversionMissing", "変換先受注が見つかりません。");
            Hibernate.initialize(quote.getConvertedOrder().getLines());
            return quote.getConvertedOrder();
        }
        Checks.version(quote.getVersion(), expectedVersion);
        Checks.state("ACCEPTED".equals(quote.getStatus()), "quotation.notAccepted", "顧客承諾済みの見積のみ受注変換できます。");
        QuotationRules.active(quote);
        QuotationRules.current(quote);
        QuotationRules.approvedIntegrity(quote);
        Checks.state(quote.getAcceptedById() != null && quote.getAcceptedAt() != null && quote.getAcceptedOn() != null
                && !quote.getAcceptedOn().before(quote.getQuoteDate()) && !quote.getAcceptedOn().after(quote.getValidUntil())
                && !quote.getAcceptedOn().after(Dates.today()), "quotation.acceptanceDate", "有効な顧客承諾記録が必要です。");
        Checks.text(quote.getAcceptanceReference(), "顧客承諾参照番号", 120);
        QuotationRevision revision = quote.getCurrentRevision();
        Checks.state(!revision.getRequestedDate().before(Dates.today()),
                "quotation.deliveryExpired", "納期が過ぎています。改訂して再承認・承諾を記録してください。");
        OrderInput input = new OrderInput();
        input.setCustomerId(quote.getCustomer().getId());
        input.setWarehouseId(quote.getWarehouse().getId());
        input.setOrderDate(Dates.today());
        input.setRequestedDate(revision.getRequestedDate());
        input.setDeliveryAddress(revision.getDeliveryAddress());
        input.setExternalReference(revision.getExternalReference());
        input.setNotes(revision.getNotes());
        for (QuotationLine approvedLine : revision.getLines()) {
            OrderLineInput line = new OrderLineInput();
            line.setProductId(approvedLine.getProduct().getId());
            line.setQuantity(approvedLine.getQuantity());
            input.getLines().add(line);
        }
        // Trusted pricing comes only from this locked accepted revision, never from a command flag.
        SalesOrder order = saveDraftInternal(null, 0, input, actor, revision);
        Checks.state(order.getNetAmount().compareTo(revision.getNetAmount()) == 0
                && order.getTaxAmount().compareTo(revision.getTaxAmount()) == 0,
                "quotation.snapshotChanged", "変換先受注と承認済み見積の金額が一致しません。");
        quote.setConvertedOrder(order);
        quote.setConvertedAt(new Date());
        quote.setUpdatedAt(new Date());
        quote.setStatus("CONVERTED");
        QuotationLedger.event(dao, quote, actor, "QUOTE_CONVERT", "ACCEPTED", order.getNumber());
        audit(actor, "QUOTE_CONVERT", quote, "order=" + order.getNumber() + " revision=" + quote.getRevisionNumber());
        dao.flush();
        return order;
    }

    public OrderAmendment applyApprovedOrderAmendment(Long id, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        Long actorId = QuotationRules.actorId(actor);
        OrderAmendment amendment = OrderAmendmentLocks.lock(dao, id);
        if ("APPLIED".equals(amendment.getStatus())) { return amendment; }
        Checks.version(amendment.getVersion(), expectedVersion);
        Checks.state("REQUESTED".equals(amendment.getStatus()), "amendment.state", "申請中の変更のみ承認できます。");
        Checks.state(!actorId.equals(amendment.getRequestedById()), "approval.self", "自分の受注変更申請は承認できません。");
        SalesOrder order = amendment.getOrder();
        OrderAmendmentRules.unchanged(order, amendment);
        OrderAmendmentRules.eligible(order);
        Checks.state(dao.count("select count(s.id) from Shipment s where s.order=:order and s.status='INSTRUCTED'",
                WholesaleDao.params("order", order)) == 0,
                "amendment.instructions", "未確定の出荷指示を倉庫担当者が取り消してから承認してください。");
        Map<Long, Integer> targets = OrderAmendmentRules.targets(amendment);
        OrderAmendmentRules.validateTargets(order, targets);
        Date requested = Checks.date(amendment.getRequestedDate(), "変更後納期");
        Checks.state(!requested.before(order.getOrderDate()) && !requested.after(Dates.addDays(order.getOrderDate(), 365)),
                "order.deliveryDate", "納期は受注日から365日以内にしてください。");
        TaxAmounts proposed = OrderAmendmentRules.totals(order, targets);
        BigDecimal delta = OrderAmendmentRules.remainingExposure(order, targets)
                .subtract(OrderAmendmentRules.remainingExposure(order, Collections.<Long, Integer>emptyMap()));
        Checks.state(proposed.getNetAmount().compareTo(amendment.getProposedNetAmount()) == 0
                && proposed.getTaxAmount().compareTo(amendment.getProposedTaxAmount()) == 0
                && delta.compareTo(amendment.getExposureDelta()) == 0,
                "amendment.proposalChanged", "変更申請の計算結果が一致しません。再申請してください。");
        boolean increasing = false;
        for (OrderAmendmentLine line : amendment.getLines()) {
            increasing |= line.getTargetQuantity() > line.getOriginalQuantity();
        }
        if (increasing) {
            Customer customer = order.getCustomer();
            Checks.state(customer.getCreditLimit() != null && customer.getCreditLimit().signum() > 0,
                    "credit.noLimit", "増量には正の与信限度額が必要です。");
            BigDecimal exposure = creditService.previewExposure(customer.getId(), actor).add(delta);
            Checks.state(exposure.compareTo(customer.getCreditLimit()) <= 0,
                    "credit.limit", "増量後の与信見込残高が限度額を超えています: " + exposure.toPlainString());
        }
        for (SalesOrderLine line : sortedLines(order)) {
            Integer target = targets.get(line.getId());
            if (target == null) { continue; }
            int newOpen = target.intValue() - line.getShippedQuantity() - line.getCancelledQuantity();
            int release = Math.max(0, line.getAllocatedQuantity() - newOpen);
            if (release > 0) {
                inventoryService.release(line, release, amendment.getNumber() + " 受注変更による余剰引当解除", actor);
            }
            line.setQuantity(target.intValue());
        }
        order.setRequestedDate(requested);
        calculateTotals(order);
        OrderStates.updateFulfilment(order);
        dao.flush();
        // A zero-priced quantity change can leave every header property unchanged.
        if (order.getVersion() == amendment.getBaseOrderVersion()) {
            dao.session().lock(order, org.hibernate.LockMode.FORCE);
        }
        amendment.setStatus("APPLIED");
        amendment.setDecidedById(actorId);
        amendment.setDecidedBy(actor.getLogin());
        amendment.setDecidedAt(new Date());
        amendment.setDecisionReason(amendment.getReason());
        amendment.setAppliedOrderVersion(Integer.valueOf(order.getVersion()));
        audit(actor, "ORDER_AMEND", order, amendment.getNumber() + " exposureDelta=" + delta.toPlainString());
        audit(actor, "AMENDMENT_APPLY", amendment, order.getNumber() + " " + amendment.getReason());
        dao.flush();
        return amendment;
    }

    public SalesOrder submit(Long id, int expectedVersion, Actor actor) {
        require(actor, "SALES", "MANAGER");
        SalesOrder order = lockOrder(id);
        Checks.version(order.getVersion(), expectedVersion);
        Checks.state(order.isEditable(), "order.notDraft", "下書きだけ承認申請できます。");
        validateActive(order);
        Checks.nonempty(order.getLines(), "受注明細");
        order.setStatus("SUBMITTED");
        audit(actor, "ORDER_SUBMIT", order, order.getExternalReference());
        dao.flush();
        return order;
    }

    public SalesOrder withdraw(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "SALES", "MANAGER");
        SalesOrder order = lockOrder(id);
        Checks.version(order.getVersion(), expectedVersion);
        Checks.state("SUBMITTED".equals(order.getStatus()), "order.notSubmitted", "承認待ちだけ取り下げできます。");
        Checks.state(order.getCreatedBy().equals(actor.getLogin()) || actor.hasRole("MANAGER") || actor.hasRole("ADMIN"),
                "order.withdrawOwner", "起票者または管理者だけ取り下げできます。");
        order.setStatus("DRAFT");
        audit(actor, "ORDER_WITHDRAW", order, Checks.text(reason, "取下理由", 500));
        dao.flush();
        return order;
    }

    public SalesOrder approve(Long id, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        SalesOrder order = lockOrder(id);
        Checks.version(order.getVersion(), expectedVersion);
        Checks.state("SUBMITTED".equals(order.getStatus()), "order.notSubmitted", "承認待ちの受注だけ承認できます。");
        Checks.state(!order.getCreatedBy().equals(actor.getLogin()), "approval.self", "自分で起票した受注は承認できません。");
        validateActive(order);
        creditService.checkApproval(order, actor);
        order.setStatus("APPROVED");
        order.setApprovedAt(new Date());
        order.setApprovedBy(actor.getLogin());
        audit(actor, "ORDER_APPROVE", order, "total=" + order.getTotalAmount().toPlainString());
        dao.flush();
        return order;
    }

    public SalesOrder reject(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "MANAGER");
        SalesOrder order = lockOrder(id);
        Checks.version(order.getVersion(), expectedVersion);
        Checks.state("SUBMITTED".equals(order.getStatus()), "order.notSubmitted", "承認待ちだけ差し戻せます。");
        order.setStatus("DRAFT");
        audit(actor, "ORDER_REJECT", order, Checks.text(reason, "差戻理由", 500));
        dao.flush();
        return order;
    }

    public SalesOrder allocate(Long id, int expectedVersion, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BATCH");
        SalesOrder order = lockOrder(id);
        Checks.version(order.getVersion(), expectedVersion);
        Checks.state(OrderStates.isApproved(order.getStatus()), "order.notApproved", "承認済みの未完了受注だけ引当できます。");
        validateActive(order);
        int allocated = 0;
        for (SalesOrderLine line : sortedLines(order)) {
            if (line.getShortageQuantity() > 0) {
                allocated += inventoryService.reserve(line, line.getShortageQuantity(), actor);
            }
        }
        OrderStates.updateFulfilment(order);
        audit(actor, "ORDER_ALLOCATE", order, "allocated=" + allocated + ",open=" + order.getOpenQuantity());
        dao.flush();
        return order;
    }

    public SalesOrder releaseAllocation(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER");
        SalesOrder order = lockOrder(id);
        Checks.version(order.getVersion(), expectedVersion);
        Checks.state(OrderStates.isApproved(order.getStatus()), "order.notApproved", "承認済み受注の引当だけ解除できます。");
        String message = Checks.text(reason, "解除理由", 500);
        Checks.state(dao.count("select count(s.id) from Shipment s where s.order.id=:order and s.status='INSTRUCTED'",
                WholesaleDao.params("order", id)) == 0, "allocation.instruction", "未確定の出荷指示を先に取り消してください。");
        for (SalesOrderLine line : sortedLines(order)) {
            if (line.getAllocatedQuantity() > 0) {
                inventoryService.release(line, line.getAllocatedQuantity(), message, actor);
            }
        }
        OrderStates.updateFulfilment(order);
        audit(actor, "ORDER_RELEASE", order, message);
        dao.flush();
        return order;
    }

    public SalesOrder cancel(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "SALES", "MANAGER");
        SalesOrder order = lockOrder(id);
        Checks.version(order.getVersion(), expectedVersion);
        Checks.state(!"SHIPPED".equals(order.getStatus()) && !"CANCELLED".equals(order.getStatus())
                && !"CLOSED_PARTIAL".equals(order.getStatus()), "order.complete", "完了・取消済みの受注は取消できません。");
        String message = Checks.text(reason, "取消理由", 500);
        List<Shipment> instructions = dao.list("from Shipment s where s.order.id=:order and s.status='INSTRUCTED' order by s.id",
                WholesaleDao.params("order", id));
        for (Shipment candidate : instructions) {
            Shipment shipment = dao.lock(Shipment.class, candidate.getId());
            shipment.setStatus("CANCELLED");
            shipment.setCancellationReason(message);
            audit(actor, "SHIPMENT_CANCEL_WITH_ORDER", shipment, message);
        }
        for (SalesOrderLine line : sortedLines(order)) {
            if (line.getAllocatedQuantity() > 0) {
                inventoryService.release(line, line.getAllocatedQuantity(), message, actor);
            }
            line.setCancelledQuantity(line.getCancelledQuantity() + line.getOpenQuantity());
        }
        order.setCancellationReason(message);
        OrderStates.updateFulfilment(order);
        audit(actor, "ORDER_CANCEL", order, message);
        dao.flush();
        return order;
    }

    public SalesOrder reschedule(Long id, int expectedVersion, Date requestedDate, String reason, Actor actor) {
        require(actor, "SALES", "MANAGER");
        SalesOrder order = lockOrder(id);
        Checks.version(order.getVersion(), expectedVersion);
        Checks.state(OrderStates.isApproved(order.getStatus()), "order.notApproved", "納期変更は承認済みの未完了受注が対象です。");
        Date date = Checks.date(requestedDate, "納期");
        Checks.state(!date.before(order.getOrderDate()) && !date.after(Dates.addDays(order.getOrderDate(), 365)),
                "order.deliveryDate", "納期は受注日から365日以内にしてください。");
        String message = Checks.text(reason, "納期変更理由", 500);
        order.setRequestedDate(date);
        audit(actor, "ORDER_RESCHEDULE", order, Dates.format(date) + " " + message);
        dao.flush();
        return order;
    }

    public SalesOrder copyToDraft(Long id, Actor actor) {
        require(actor, "SALES", "MANAGER");
        SalesOrder original = getOrder(id, actor);
        OrderInput input = new OrderInput();
        input.setCustomerId(original.getCustomer().getId());
        input.setWarehouseId(original.getWarehouse().getId());
        input.setOrderDate(Dates.today());
        input.setRequestedDate(Dates.addDays(Dates.today(), 3));
        input.setDeliveryAddress(original.getDeliveryAddress());
        input.setNotes("複写元: " + original.getNumber());
        for (SalesOrderLine previous : original.getLines()) {
            OrderLineInput line = new OrderLineInput();
            line.setProductId(previous.getProduct().getId());
            line.setQuantity(previous.getQuantity());
            input.getLines().add(line);
        }
        return saveDraft(null, 0, input, actor);
    }

    public SalesOrder getOrder(Long id, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        SalesOrder order = dao.get(SalesOrder.class, id);
        Hibernate.initialize(order.getLines());
        return order;
    }

    public Page<SalesOrder> searchOrders(Search search, Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        Map<String, Object> params = WholesaleDao.params("text", search.getLikeText());
        String where = " from SalesOrder o where (o.number like :text escape '!' "
                + "or o.customerName like :text escape '!' or o.externalReference like :text escape '!')";
        if (search.getStatus().length() != 0) {
            where += " and o.status=:status";
            params.put("status", search.getStatus());
        }
        if (search.getCustomerId() != null) {
            where += " and o.customer.id=:customer";
            params.put("customer", search.getCustomerId());
        }
        if (search.getWarehouseId() != null) {
            where += " and o.warehouse.id=:warehouse";
            params.put("warehouse", search.getWarehouseId());
        }
        if (search.getFrom() != null) {
            where += " and o.orderDate>=:from";
            params.put("from", search.getFrom());
        }
        if (search.getTo() != null) {
            where += " and o.orderDate<=:to";
            params.put("to", search.getTo());
        }
        Page<SalesOrder> result = dao.page("select o" + where + " order by o.id desc",
                "select count(o.id)" + where, params, search);
        for (SalesOrder order : result.getItems()) {
            Hibernate.initialize(order.getLines());
        }
        return result;
    }

    public List<Long> listAllocationCandidates(Date throughDate, int limit, Actor actor) {
        require(actor, "WAREHOUSE", "MANAGER", "BATCH");
        Checks.state(limit > 0 && limit <= 1000, "batch.limit", "処理単位は1から1,000件までです。");
        List<Long> result = new ArrayList<Long>();
        List<?> ids = dao.query("select distinct o.id, o.requestedDate from SalesOrder o join o.lines l "
                + "where o.status in ('APPROVED','PART_ALLOCATED','PART_SHIPPED') and o.requestedDate<=:date "
                + "and l.quantity-l.shippedQuantity-l.cancelledQuantity>l.allocatedQuantity "
                + "order by o.requestedDate,o.id", WholesaleDao.params("date", Checks.date(throughDate, "対象日")))
                .setMaxResults(limit).list();
        for (Object row : ids) {
            result.add((Long) ((Object[]) row)[0]);
        }
        return result;
    }

    private SalesOrder lockOrder(Long id) {
        return OrderLocks.lock(dao, id);
    }

    private void validateActive(SalesOrder order) {
        Checks.state(order.getCustomer().isActive() && !order.getCustomer().isOnHold(),
                "order.customer", "この得意先の受注処理は停止中です。");
        Checks.state(order.getWarehouse().isActive(), "order.warehouse", "停止中の倉庫は処理できません。");
        for (SalesOrderLine line : order.getLines()) {
            Checks.state(line.getProduct().isActive(), "order.product", line.getProductCode() + "は停止中です。");
        }
    }

    private List<SalesOrderLine> sortedLines(SalesOrder order) {
        List<SalesOrderLine> sorted = new ArrayList<SalesOrderLine>(order.getLines());
        Collections.sort(sorted, new Comparator<SalesOrderLine>() {
            @Override
            public int compare(SalesOrderLine first, SalesOrderLine second) {
                return first.getProduct().getId().compareTo(second.getProduct().getId());
            }
        });
        return sorted;
    }

    private void calculateTotals(SalesOrder order) {
        TaxAmounts amounts = new TaxAmounts(order.getTaxRounding());
        for (SalesOrderLine line : order.getLines()) {
            amounts.add(line.getNetAmount(), line.getTaxRate());
        }
        Checks.state(amounts.getNetAmount().compareTo(new BigDecimal("9999999999999999.99")) <= 0,
                "order.amountOverflow", "受注合計金額が上限を超えています。");
        order.setNetAmount(amounts.getNetAmount());
        order.setTaxAmount(amounts.getTaxAmount());
    }
}
