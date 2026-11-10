package jp.co.tsubame.wholesale.service;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.OrderAmendmentCommand;
import jp.co.tsubame.wholesale.common.OrderAmendmentLineCommand;
import jp.co.tsubame.wholesale.common.OrderAmendmentRules;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.QuotationRules;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.common.TaxAmounts;
import jp.co.tsubame.wholesale.dao.OrderAmendmentLocks;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.OrderAmendment;
import jp.co.tsubame.wholesale.entity.OrderAmendmentLine;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;

public class OrderAmendmentService extends BaseService {
    private OrderService orderService;
    public void setOrderService(OrderService value) { orderService = value; }

    public OrderAmendment request(OrderAmendmentCommand input, Actor actor) {
        require(actor, "SALES", "MANAGER");
        QuotationRules.actorId(actor);
        Checks.state(input != null, "amendment.input", "変更内容を指定してください。");
        String reason = Checks.text(input.getReason(), "変更理由", 500);
        Checks.state(input.getLines() != null && input.getLines().size() <= 200,
                "amendment.lines", "変更明細は200件以内で指定してください。");
        SalesOrder order = OrderAmendmentLocks.lockSource(dao, input.getOrderId());
        Checks.version(order.getVersion(), input.getExpectedOrderVersion());
        OrderAmendmentRules.eligible(order);
        Checks.state(dao.count("select count(a.id) from OrderAmendment a where a.order=:order and a.status='REQUESTED'",
                WholesaleDao.params("order", order)) == 0, "amendment.pending", "未処理の変更申請を先に処理してください。");
        Map<Long, Integer> targets = new HashMap<Long, Integer>();
        for (OrderAmendmentLineCommand line : input.getLines()) {
            Checks.state(line != null && line.getOrderLineId() != null
                    && targets.put(line.getOrderLineId(), Integer.valueOf(line.getTargetQuantity())) == null,
                    "amendment.duplicateLine", "変更明細を重複せず指定してください。");
        }
        OrderAmendmentRules.validateTargets(order, targets);
        Date requestedDate = input.getRequestedDate() == null ? order.getRequestedDate()
                : Checks.date(input.getRequestedDate(), "変更後納期");
        Checks.state(!requestedDate.before(order.getOrderDate())
                && !requestedDate.after(Dates.addDays(order.getOrderDate(), 365)),
                "order.deliveryDate", "納期は受注日から365日以内にしてください。");
        OrderAmendment amendment = new OrderAmendment();
        amendment.setNumber("NEW-" + UUID.randomUUID().toString());
        amendment.setOrder(order);
        amendment.setBaseOrderVersion(order.getVersion());
        amendment.setSourceFingerprint(OrderAmendmentRules.fingerprint(order));
        amendment.setOriginalRequestedDate(order.getRequestedDate());
        amendment.setRequestedDate(requestedDate);
        amendment.setReason(reason);
        amendment.setRequestedById(actor.getUserId());
        amendment.setRequestedBy(actor.getLogin());
        amendment.setRequestedAt(new Date());
        amendment.setOriginalNetAmount(order.getNetAmount());
        amendment.setOriginalTaxAmount(order.getTaxAmount());
        TaxAmounts proposed = OrderAmendmentRules.totals(order, targets);
        amendment.setProposedNetAmount(proposed.getNetAmount());
        amendment.setProposedTaxAmount(proposed.getTaxAmount());
        amendment.setExposureDelta(OrderAmendmentRules.remainingExposure(order, targets)
                .subtract(OrderAmendmentRules.remainingExposure(order, Collections.<Long, Integer>emptyMap())));
        for (SalesOrderLine source : order.getLines()) {
            Integer quantity = targets.get(source.getId());
            if (quantity == null || quantity.intValue() == source.getQuantity()) { continue; }
            OrderAmendmentLine line = new OrderAmendmentLine();
            line.setAmendment(amendment);
            line.setOrderLine(source);
            line.setOriginalQuantity(source.getQuantity());
            line.setTargetQuantity(quantity.intValue());
            line.setOriginalAllocatedQuantity(source.getAllocatedQuantity());
            line.setOriginalShippedQuantity(source.getShippedQuantity());
            line.setOriginalCancelledQuantity(source.getCancelledQuantity());
            line.setPackSize(source.getPackSize());
            line.setUnitPrice(source.getUnitPrice());
            line.setTaxRate(source.getTaxRate());
            amendment.getLines().add(line);
        }
        Checks.state(!amendment.getLines().isEmpty() || !Dates.sameDay(requestedDate, order.getRequestedDate()),
                "amendment.noChange", "数量または納期を変更してください。");
        dao.save(amendment);
        amendment.setNumber(documentNumber("OA", amendment.getId()));
        audit(actor, "AMENDMENT_REQUEST", amendment, order.getNumber() + " baseVersion=" + order.getVersion() + " " + reason);
        dao.flush();
        return amendment;
    }

    public OrderAmendment getAmendment(Long id, Actor actor) {
        reader(actor);
        return OrderAmendmentLocks.initialize(dao.get(OrderAmendment.class, id));
    }

    public List<OrderAmendment> listForOrder(Long orderId, Actor actor) {
        reader(actor);
        dao.get(SalesOrder.class, orderId);
        List<OrderAmendment> rows = dao.list("from OrderAmendment a where a.order.id=:id order by a.id desc",
                WholesaleDao.params("id", orderId));
        for (OrderAmendment row : rows) { OrderAmendmentLocks.initialize(row); }
        return rows;
    }

    public Page<OrderAmendment> searchAmendments(Search search, Actor actor) {
        reader(actor);
        Checks.state(search != null, "validation.search", "検索条件を指定してください。");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        String where = " from OrderAmendment a where 1=1";
        if (search.getText().length() > 0) {
            where += " and (lower(a.number) like :text escape '!' or lower(a.order.number) like :text escape '!'"
                    + " or lower(a.order.customerName) like :text escape '!')";
            parameters.put("text", search.getLikeText().toLowerCase(java.util.Locale.ROOT));
        }
        if (search.getStatus().length() > 0) {
            Checks.state(search.getStatus().matches("REQUESTED|APPLIED|REJECTED|CANCELLED"),
                    "amendment.status", "変更申請状態が不正です。");
            where += " and a.status=:status"; parameters.put("status", search.getStatus());
        }
        if (search.getCustomerId() != null) { where += " and a.order.customer.id=:customer"; parameters.put("customer", search.getCustomerId()); }
        if (search.getWarehouseId() != null) { where += " and a.order.warehouse.id=:warehouse"; parameters.put("warehouse", search.getWarehouseId()); }
        if (search.getFrom() != null) { where += " and a.requestedAt>=:from"; parameters.put("from", search.getFrom()); }
        if (search.getTo() != null) { where += " and a.requestedAt<:until"; parameters.put("until", Dates.addDays(search.getTo(), 1)); }
        Checks.state(search.getFrom() == null || search.getTo() == null || !search.getTo().before(search.getFrom()),
                "validation.interval", "検索期間が逆転しています。");
        Page<OrderAmendment> rows = dao.page("select a" + where + " order by a.id desc",
                "select count(a.id)" + where, parameters, search);
        for (OrderAmendment row : rows.getItems()) { OrderAmendmentLocks.initialize(row); }
        return rows;
    }

    public OrderAmendment approve(Long id, int expectedVersion, Actor actor) {
        require(actor, "MANAGER");
        return orderService.applyApprovedOrderAmendment(id, expectedVersion, actor);
    }

    public OrderAmendment reject(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "MANAGER");
        return decide(id, expectedVersion, "REJECTED", reason, actor);
    }

    public OrderAmendment cancel(Long id, int expectedVersion, String reason, Actor actor) {
        require(actor, "SALES", "MANAGER");
        return decide(id, expectedVersion, "CANCELLED", reason, actor);
    }

    private OrderAmendment decide(Long id, int expectedVersion, String state, String reason, Actor actor) {
        Long actorId = QuotationRules.actorId(actor);
        OrderAmendment amendment = OrderAmendmentLocks.lock(dao, id);
        Checks.version(amendment.getVersion(), expectedVersion);
        Checks.state("REQUESTED".equals(amendment.getStatus()), "amendment.state", "申請中の変更のみ処理できます。");
        if ("CANCELLED".equals(state)) {
            Checks.state(actorId.equals(amendment.getRequestedById()) || actor.hasRole("MANAGER") || actor.hasRole("ADMIN"),
                    "amendment.owner", "申請者または管理者のみ取消できます。");
        }
        amendment.setDecisionReason(Checks.text(reason, "処理理由", 500));
        amendment.setStatus(state);
        amendment.setDecidedById(actorId);
        amendment.setDecidedBy(actor.getLogin());
        amendment.setDecidedAt(new Date());
        audit(actor, "AMENDMENT_" + state, amendment, amendment.getDecisionReason());
        dao.flush();
        return amendment;
    }

    private void reader(Actor actor) {
        require(actor, "SALES", "WAREHOUSE", "BILLING", "MANAGER", "BATCH");
    }
}
