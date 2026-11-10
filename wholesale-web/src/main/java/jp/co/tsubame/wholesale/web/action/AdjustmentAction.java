package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.StockAdjustmentCommand;
import jp.co.tsubame.wholesale.entity.StockAdjustment;
import jp.co.tsubame.wholesale.service.StockAdjustmentService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.AdjustmentForm;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class AdjustmentAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "detail".equals(op) || "new".equals(op); }
    protected boolean isMutation(String op) { return "propose".equals(op) || "approve".equals(op) || "reject".equals(op) || "cancel".equals(op); }
    private StockAdjustmentService adjustments(HttpServletRequest request) {
        return service(request, "stockAdjustmentService", StockAdjustmentService.class);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        AdjustmentForm form = (AdjustmentForm) base;
        if ("approve".equals(form.getOp()) || "reject".equals(form.getOp())) { actor(request).require("MANAGER"); }
        else if (isMutation(form.getOp())) { actor(request).require("WAREHOUSE", "MANAGER"); }
        StockAdjustmentService service = adjustments(request);
        if ("list".equals(form.getOp())) {
            references(request); request.setAttribute("searchWarehouse", Boolean.TRUE); request.setAttribute("searchDates", Boolean.TRUE);
            request.setAttribute("statuses", new String[] {"PROPOSED", "APPROVED", "REJECTED", "CANCELLED"});
            request.setAttribute("results", service.search(actor(request), Inputs.search(form)));
            return view(request, "control/adjustments", "在庫調整一覧");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("WAREHOUSE", "MANAGER"); references(request);
            return view(request, "control/adjustment-edit", "在庫調整の申請");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        StockAdjustment result;
        if ("propose".equals(form.getOp())) {
            StockAdjustmentCommand command = new StockAdjustmentCommand();
            command.setWarehouseId(Inputs.id(form.getWarehouseId(), "倉庫"));
            command.setProductId(Inputs.id(form.getProductId(), "商品"));
            command.setQuantityChange(Inputs.integer(form.getQuantityChange(), "増減数量", -100000000, 100000000));
            command.setReason(Inputs.text(form.getReason(), "調整理由", 500, true));
            result = service.propose(actor(request), command);
        } else if ("approve".equals(form.getOp())) { result = service.approve(actor(request), id(form), version(form), reason(form)); }
        else if ("reject".equals(form.getOp())) { result = service.reject(actor(request), id(form), version(form), reason(form)); }
        else { result = service.cancel(actor(request), id(form), version(form), reason(form)); }
        return redirect(request, response, "/adjustments.do", result.getId(), "在庫調整処理を実行しました。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        AdjustmentForm form = (AdjustmentForm) base;
        if ("list".equals(form.getOp())) { return view(request, "control/adjustments", "調整検索条件の確認"); }
        if ("propose".equals(form.getOp())) { references(request); return view(request, "control/adjustment-edit", "在庫調整内容の確認"); }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "調整条件の確認");
    }
    private ActionForward detail(AdjustmentForm form, HttpServletRequest request, boolean refresh) {
        StockAdjustment adjustment = adjustments(request).get(actor(request), id(form));
        request.setAttribute("adjustment", adjustment);
        if (refresh) { identity(form, adjustment); }
        return view(request, "control/adjustment-detail", "調整詳細 · " + adjustment.getNumber());
    }
}
