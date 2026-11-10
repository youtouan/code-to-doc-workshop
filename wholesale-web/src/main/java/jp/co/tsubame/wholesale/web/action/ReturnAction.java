package jp.co.tsubame.wholesale.web.action;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.ReturnLineInput;
import jp.co.tsubame.wholesale.entity.SalesReturn;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.ShipmentLine;
import jp.co.tsubame.wholesale.service.ShippingService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.ReturnForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class ReturnAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "detail".equals(op) || "new".equals(op); }
    protected boolean isMutation(String op) {
        return "request".equals(op) || "approve".equals(op) || "reject".equals(op)
                || "cancel".equals(op) || "receive".equals(op);
    }
    private ShippingService shipping(HttpServletRequest request) {
        return service(request, "shippingService", ShippingService.class);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        ReturnForm form = (ReturnForm) base;
        ShippingService service = shipping(request);
        if ("receive".equals(form.getOp())) { actor(request).require("WAREHOUSE"); }
        else if ("approve".equals(form.getOp()) || "reject".equals(form.getOp())) { actor(request).require("MANAGER"); }
        else if (isMutation(form.getOp())) { actor(request).require("SALES", "WAREHOUSE", "MANAGER"); }
        if ("list".equals(form.getOp())) {
            references(request); request.setAttribute("searchCustomer", Boolean.TRUE);
            request.setAttribute("searchDates", Boolean.TRUE);
            request.setAttribute("statuses", new String[] {"REQUESTED", "APPROVED", "RECEIVED", "REJECTED", "CANCELLED"});
            request.setAttribute("results", service.searchReturns(Inputs.search(form), actor(request)));
            return view(request, "returns/list", "返品・検品一覧");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("SALES", "WAREHOUSE", "MANAGER");
            Shipment shipment = source(form, request);
            String[] ids = new String[shipment.getLines().size()];
            String[] quantities = new String[ids.length];
            String[] restock = new String[ids.length];
            for (int i = 0; i < ids.length; i++) {
                ShipmentLine line = shipment.getLines().get(i);
                ids[i] = String.valueOf(line.getId()); quantities[i] = "0"; restock[i] = "false";
            }
            form.setLineId(ids); form.setQuantity(quantities); form.setRestock(restock);
            return view(request, "returns/edit", "返品申請の作成");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        SalesReturn result;
        if ("request".equals(form.getOp())) {
            result = service.requestReturn(Inputs.id(form.getShipmentId(), "出荷伝票"),
                    Inputs.choice(form.getReturnReason(), "返品理由", "DAMAGED", "CUSTOMER_CHANGE", "MISSHIP", "EXPIRED"),
                    Inputs.text(form.getNotes(), "返品備考", 1000, false), lines(form), actor(request));
        } else if ("approve".equals(form.getOp())) { result = service.approveReturn(id(form), version(form), actor(request)); }
        else if ("reject".equals(form.getOp())) { result = service.rejectReturn(id(form), version(form), reason(form), actor(request)); }
        else if ("cancel".equals(form.getOp())) { result = service.cancelReturn(id(form), version(form), reason(form), actor(request)); }
        else {
            result = service.receiveReturn(id(form), version(form), Inputs.date(form.getReceivedDate(), "返品受領日", false), actor(request));
        }
        return redirect(request, response, "/returns.do", result.getId(), "返品処理を実行しました。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        ReturnForm form = (ReturnForm) base;
        if ("list".equals(form.getOp())) { return view(request, "returns/list", "返品検索条件の確認"); }
        if ("request".equals(form.getOp())) {
            source(form, request);
            return view(request, "returns/edit", "返品申請の確認");
        }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "返品条件の確認");
    }
    public static List<ReturnLineInput> lines(ReturnForm form) {
        Inputs.arrays(form.getLineId(), form.getQuantity(), form.getRestock());
        Inputs.uniqueIds(form.getLineId(), "出荷明細");
        List<ReturnLineInput> lines = new ArrayList<ReturnLineInput>();
        for (int i = 0; i < form.getLineId().length; i++) {
            String value = form.getQuantity()[i];
            int quantity = value == null || value.length() == 0 ? 0 : Inputs.quantity(value, "返品数量", true);
            boolean restock = Inputs.bool(form.getRestock()[i], "在庫復帰");
            if (quantity == 0) { continue; }
            if (restock && ("DAMAGED".equals(form.getReturnReason()) || "EXPIRED".equals(form.getReturnReason()))) {
                throw Inputs.invalid("在庫復帰", "破損・期限切れの商品は在庫復帰できません。");
            }
            ReturnLineInput line = new ReturnLineInput();
            line.setShipmentLineId(Inputs.id(form.getLineId()[i], "出荷明細"));
            line.setQuantity(quantity); line.setRestock(restock); lines.add(line);
        }
        if (lines.isEmpty()) { throw Inputs.invalid("明細", "返品数量を1行以上指定してください。"); }
        return lines;
    }
    private Shipment source(ReturnForm form, HttpServletRequest request) {
        Shipment shipment = shipping(request).getShipment(Inputs.id(form.getShipmentId(), "出荷伝票"), actor(request));
        request.setAttribute("shipment", shipment);
        return shipment;
    }
    private ActionForward detail(ReturnForm form, HttpServletRequest request, boolean refresh) {
        SalesReturn value = shipping(request).getReturn(id(form), actor(request));
        request.setAttribute("salesReturn", value);
        if (refresh) { identity(form, value); form.setReceivedDate(Dates.format(Dates.today())); }
        return view(request, "returns/detail", "返品詳細 · " + value.getNumber());
    }
}
