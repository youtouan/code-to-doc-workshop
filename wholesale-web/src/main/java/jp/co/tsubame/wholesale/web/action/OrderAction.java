package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.OrderInput;
import jp.co.tsubame.wholesale.common.OrderLineInput;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.OrderAmendmentService;
import jp.co.tsubame.wholesale.service.ShippingService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.OrderForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class OrderAction extends BaseAction {
    protected boolean isMutation(String op) {
        return "save".equals(op) || "addLine".equals(op) || "submit".equals(op) || "approve".equals(op)
                || "reject".equals(op) || "withdraw".equals(op) || "cancel".equals(op) || "allocate".equals(op)
                || "release".equals(op) || "reschedule".equals(op) || "copy".equals(op);
    }
    private OrderService orders(HttpServletRequest request) {
        return service(request, "orderService", OrderService.class);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        OrderForm form = (OrderForm) base;
        if ("approve".equals(form.getOp()) || "reject".equals(form.getOp())) { actor(request).require("MANAGER"); }
        else if ("allocate".equals(form.getOp())) { actor(request).require("SALES", "WAREHOUSE", "MANAGER", "BATCH"); }
        else if ("release".equals(form.getOp())) { actor(request).require("SALES", "WAREHOUSE", "MANAGER"); }
        else if (isMutation(form.getOp())) { actor(request).require("SALES", "MANAGER"); }
        OrderService service = orders(request);
        if ("list".equals(form.getOp())) {
            references(request);
            request.setAttribute("searchCustomer", Boolean.TRUE);
            request.setAttribute("searchWarehouse", Boolean.TRUE);
            request.setAttribute("searchDates", Boolean.TRUE);
            request.setAttribute("statuses", new String[] {"DRAFT", "SUBMITTED", "APPROVED", "PART_ALLOCATED",
                    "ALLOCATED", "PART_SHIPPED", "SHIPPED", "CANCELLED", "CLOSED_PARTIAL"});
            request.setAttribute("results", service.searchOrders(Inputs.search(form), actor(request)));
            return view(request, "orders/list", "受注・承認一覧");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("SALES", "MANAGER");
            form.setOrderDate(Dates.format(Dates.today()));
            form.setRequestedDate(Dates.format(Dates.addDays(Dates.today(), 3)));
            form.rows(5);
            return edit(form, request);
        }
        if ("edit".equals(form.getOp())) {
            actor(request).require("SALES", "MANAGER");
            SalesOrder order = service.getOrder(id(form), actor(request));
            if (!order.isEditable()) { throw Inputs.invalid("状態", "この受注は下書き編集できません。"); }
            populate(form, order);
            return edit(form, request);
        }
        if ("addLine".equals(form.getOp())) {
            actor(request).require("SALES", "MANAGER");
            Inputs.arrays(form.getProductId(), form.getQuantity(), form.getPriceOverride(), form.getPriceReason());
            form.rows(Math.min(Inputs.MAX_LINES, form.getProductId().length + 5));
            return edit(form, request);
        }
        if ("save".equals(form.getOp())) {
            SalesOrder saved = service.saveDraft(Inputs.optionalId(form.getId(), "受注ID"), version(form),
                    input(form, request), actor(request));
            return redirect(request, response, "/orders.do", saved.getId(), "受注下書きを保存しました。");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        Long id = id(form);
        int version = version(form);
        SalesOrder result;
        if ("submit".equals(form.getOp())) { result = service.submit(id, version, actor(request)); }
        else if ("approve".equals(form.getOp())) { result = service.approve(id, version, actor(request)); }
        else if ("reject".equals(form.getOp())) { result = service.reject(id, version, reason(form), actor(request)); }
        else if ("withdraw".equals(form.getOp())) { result = service.withdraw(id, version, reason(form), actor(request)); }
        else if ("cancel".equals(form.getOp())) { result = service.cancel(id, version, reason(form), actor(request)); }
        else if ("allocate".equals(form.getOp())) { result = service.allocate(id, version, actor(request)); }
        else if ("release".equals(form.getOp())) { result = service.releaseAllocation(id, version, reason(form), actor(request)); }
        else if ("reschedule".equals(form.getOp())) {
            result = service.reschedule(id, version, Inputs.date(form.getRequestedDate(), "変更後納期", false), reason(form), actor(request));
        } else if ("copy".equals(form.getOp())) { result = service.copyToDraft(id, actor(request)); }
        else { throw Inputs.invalid("操作", "受注操作を確認してください。"); }
        return redirect(request, response, "/orders.do", result.getId(), "受注処理を実行しました。最新の状態をご確認ください。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        OrderForm form = (OrderForm) base;
        if ("list".equals(form.getOp())) { return view(request, "orders/list", "受注検索条件の確認"); }
        if ("save".equals(form.getOp()) || "addLine".equals(form.getOp())) {
            if (form.getProductId() == null || form.getProductId().length == 0 || form.getProductId().length > 100) {
                return view(request, "error", "明細形式の確認");
            }
            return edit(form, request);
        }
        if (Inputs.optionalId(form.getId(), "受注ID") != null) { return detail(form, request, false); }
        return view(request, "error", "受注の確認");
    }
    private ActionForward edit(OrderForm form, HttpServletRequest request) {
        references(request);
        return view(request, "orders/edit", form.getId().length() == 0 ? "受注の新規登録" : "受注下書きの編集");
    }
    private ActionForward detail(OrderForm form, HttpServletRequest request, boolean refreshIdentity) {
        SalesOrder order = orders(request).getOrder(id(form), actor(request));
        request.setAttribute("order", order);
        request.setAttribute("amendments", service(request, "orderAmendmentService", OrderAmendmentService.class)
                .listForOrder(order.getId(), actor(request)));
        request.setAttribute("shipments", service(request, "shippingService", ShippingService.class)
                .listOrderShipments(order.getId(), actor(request)));
        if (!actor(request).hasRole("BILLING") || actor(request).hasRole("ADMIN")
                || actor(request).hasRole("MANAGER") || actor(request).hasRole("SALES")
                || actor(request).hasRole("WAREHOUSE") || actor(request).hasRole("BATCH")) {
            request.setAttribute("reservations", service(request, "inventoryService", InventoryService.class)
                    .listReservations(order.getId(), actor(request)));
        }
        if (refreshIdentity) {
            identity(form, order);
            form.setRequestedDate(Dates.format(order.getRequestedDate()));
        }
        return view(request, "orders/detail", "受注詳細 · " + order.getNumber());
    }
    public OrderInput input(OrderForm form, HttpServletRequest request) {
        actor(request).require("SALES", "MANAGER");
        OrderInput input = new OrderInput();
        input.setCustomerId(Inputs.id(form.getCustomerId(), "得意先"));
        input.setWarehouseId(Inputs.id(form.getWarehouseId(), "出荷倉庫"));
        input.setOrderDate(Inputs.date(form.getOrderDate(), "受注日", false));
        input.setRequestedDate(Inputs.date(form.getRequestedDate(), "希望納期", false));
        input.setDeliveryAddress(Inputs.text(form.getDeliveryAddress(), "納品先住所", 250, false));
        input.setExternalReference(Inputs.text(form.getExternalReference(), "先方注文番号", 100, false));
        input.setNotes(Inputs.text(form.getNotes(), "備考", 1000, false));
        Inputs.arrays(form.getProductId(), form.getQuantity(), form.getPriceOverride(), form.getPriceReason());
        for (int i = 0; i < form.getProductId().length; i++) {
            String product = form.getProductId()[i];
            String quantity = form.getQuantity()[i];
            if ("".equals(product) && "".equals(quantity) && "".equals(form.getPriceOverride()[i])
                    && "".equals(form.getPriceReason()[i])) { continue; }
            OrderLineInput line = new OrderLineInput();
            line.setProductId(Inputs.id(product, "商品（" + (i + 1) + "行）"));
            line.setQuantity(Inputs.quantity(quantity, "数量（" + (i + 1) + "行）", false));
            line.setPriceOverride(Inputs.money(form.getPriceOverride()[i], "交渉単価（" + (i + 1) + "行）", true));
            line.setPriceReason(Inputs.text(form.getPriceReason()[i], "価格変更理由", 250, false));
            if (line.getPriceOverride() != null) { actor(request).require("MANAGER"); }
            input.getLines().add(line);
        }
        if (input.getLines().isEmpty()) { throw Inputs.invalid("明細", "商品と数量を1行以上入力してください。"); }
        return input;
    }
    private void populate(OrderForm form, SalesOrder order) {
        identity(form, order);
        form.setCustomerId(String.valueOf(order.getCustomer().getId()));
        form.setWarehouseId(String.valueOf(order.getWarehouse().getId()));
        form.setOrderDate(Dates.format(order.getOrderDate())); form.setRequestedDate(Dates.format(order.getRequestedDate()));
        form.setDeliveryAddress(order.getDeliveryAddress()); form.setExternalReference(order.getExternalReference());
        form.setNotes(order.getNotes()); form.rows(Math.min(100, Math.max(5, order.getLines().size() + 1)));
        for (int i = 0; i < order.getLines().size(); i++) {
            SalesOrderLine line = order.getLines().get(i);
            form.getProductId()[i] = String.valueOf(line.getProduct().getId());
            form.getQuantity()[i] = String.valueOf(line.getQuantity());
            // Negotiated pricing is deliberately opt-in; normal edits recalculate current catalog prices.
            form.getPriceOverride()[i] = "";
            form.getPriceReason()[i] = "";
        }
    }
}
