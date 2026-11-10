package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.entity.StockBalance;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class StockAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "detail".equals(op); }
    protected boolean isMutation(String op) { return false; }
    protected ActionForward perform(ActionMapping mapping, BaseForm form, HttpServletRequest request,
                                    HttpServletResponse response) {
        InventoryService service = service(request, "inventoryService", InventoryService.class);
        references(request);
        if ("detail".equals(form.getOp())) {
            StockBalance balance = service.getStock(id(form), actor(request));
            request.setAttribute("stock", balance);
            request.setAttribute("results", service.searchMovements(balance.getId(), Inputs.search(form), actor(request)));
            return view(request, "inventory/detail", "在庫詳細・移動履歴");
        }
        request.setAttribute("statuses", new String[] {"SHORT", "BLOCKED", "AVAILABLE"});
        request.setAttribute("searchWarehouse", Boolean.TRUE);
        request.setAttribute("results", service.searchStock(Inputs.search(form), actor(request)));
        return view(request, "inventory/list", "在庫照会");
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        if ("list".equals(form.getOp())) { return view(request, "inventory/list", "在庫検索条件の確認"); }
        if (request.getAttribute("stock") != null) { return view(request, "inventory/detail", "移動履歴検索条件の確認"); }
        return view(request, "error", "在庫の確認");
    }
}
