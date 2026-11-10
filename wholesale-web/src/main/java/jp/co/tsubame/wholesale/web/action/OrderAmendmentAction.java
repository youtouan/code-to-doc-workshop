package jp.co.tsubame.wholesale.web.action;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.entity.OrderAmendment;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.service.OrderAmendmentService;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.ShippingService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.QuotationInputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.OrderAmendmentForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class OrderAmendmentAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "new".equals(op) || "detail".equals(op); }
    protected boolean isMutation(String op) { return "request".equals(op) || "approve".equals(op) || "reject".equals(op) || "cancel".equals(op); }
    private OrderAmendmentService amendments(HttpServletRequest request) {
        return service(request, "orderAmendmentService", OrderAmendmentService.class);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        OrderAmendmentForm form = (OrderAmendmentForm) base;
        if ("approve".equals(form.getOp()) || "reject".equals(form.getOp())) { actor(request).require("MANAGER"); }
        else if (isMutation(form.getOp()) || "new".equals(form.getOp())) { actor(request).require("SALES", "MANAGER"); }
        if ("list".equals(form.getOp())) {
            references(request); request.setAttribute("searchCustomer", Boolean.TRUE); request.setAttribute("searchWarehouse", Boolean.TRUE);
            request.setAttribute("searchDates", Boolean.TRUE); request.setAttribute("statuses", new String[] {"REQUESTED","APPLIED","REJECTED","CANCELLED"});
            request.setAttribute("results", amendments(request).searchAmendments(Inputs.search(form), actor(request)));
            return view(request, "amendments/list", "承認済み受注の変更申請");
        }
        if ("new".equals(form.getOp())) {
            SalesOrder order = source(form, request);
            form.setExpectedOrderVersion(String.valueOf(order.getVersion()));
            String[] ids = new String[order.getLines().size()], quantities = new String[ids.length];
            for (int i = 0; i < ids.length; i++) { ids[i] = String.valueOf(order.getLines().get(i).getId()); quantities[i] = ""; }
            form.setOrderLineId(ids); form.setTargetQuantity(quantities);
            return view(request, "amendments/edit", "受注数量・納期の変更申請");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        OrderAmendment result;
        if ("request".equals(form.getOp())) { result = amendments(request).request(QuotationInputs.amendment(form), actor(request)); }
        else if ("approve".equals(form.getOp())) { result = amendments(request).approve(id(form), version(form), actor(request)); }
        else if ("reject".equals(form.getOp())) { result = amendments(request).reject(id(form), version(form), reason(form), actor(request)); }
        else { result = amendments(request).cancel(id(form), version(form), reason(form), actor(request)); }
        return redirect(request, response, "/orderAmendments.do", result.getId(), "受注変更申請の処理を実行しました。適用状態をご確認ください。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        OrderAmendmentForm form = (OrderAmendmentForm) base;
        if ("list".equals(form.getOp())) { return view(request, "amendments/list", "変更申請検索条件の確認"); }
        if ("request".equals(form.getOp())) { source(form, request); return view(request, "amendments/edit", "受注変更内容の確認"); }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "受注変更の確認");
    }
    private SalesOrder source(OrderAmendmentForm form, HttpServletRequest request) {
        SalesOrder order = service(request, "orderService", OrderService.class).getOrder(Inputs.id(form.getOrderId(), "受注"), actor(request));
        request.setAttribute("order", order); return order;
    }
    private ActionForward detail(OrderAmendmentForm form, HttpServletRequest request, boolean refresh) {
        OrderAmendment amendment = amendments(request).getAmendment(id(form), actor(request));
        request.setAttribute("amendment", amendment);
        List<Shipment> open = new ArrayList<Shipment>();
        for (Shipment shipment : service(request, "shippingService", ShippingService.class).listOrderShipments(amendment.getOrder().getId(), actor(request))) {
            if ("INSTRUCTED".equals(shipment.getStatus())) { open.add(shipment); }
        }
        request.setAttribute("openInstructions", open);
        if (refresh) { identity(form, amendment); }
        return view(request, "amendments/detail", "受注変更詳細 · " + amendment.getNumber());
    }
}
