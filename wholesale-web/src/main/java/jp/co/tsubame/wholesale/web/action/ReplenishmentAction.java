package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.service.PurchasingService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class ReplenishmentAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op); }
    protected boolean isMutation(String op) { return false; }
    protected ActionForward perform(ActionMapping mapping, BaseForm form, HttpServletRequest request,
                                    HttpServletResponse response) {
        references(request);
        if (form.getFrom().length() == 0) { form.setFrom(Dates.format(Dates.today())); }
        if (form.getWarehouseId().length() > 0) {
            PurchasingService service = service(request, "purchasingService", PurchasingService.class);
            request.setAttribute("suggestions", service.previewReorder(Inputs.id(form.getWarehouseId(), "倉庫"),
                    Inputs.date(form.getFrom(), "基準日", false), actor(request)));
            request.setAttribute("overdueLines", service.listOverdueLines(Inputs.id(form.getWarehouseId(), "倉庫"),
                    Inputs.date(form.getFrom(), "基準日", false), actor(request)));
        }
        return view(request, "purchasing/replenishment", "補充候補・入荷遅延");
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        references(request); return view(request, "purchasing/replenishment", "補充条件の確認");
    }
}
