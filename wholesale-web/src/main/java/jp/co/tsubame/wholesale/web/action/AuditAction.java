package jp.co.tsubame.wholesale.web.action;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.service.AuditService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.AuditForm;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class AuditAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "detail".equals(op); }
    protected boolean isMutation(String op) { return false; }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) {
        actor(request).require("MANAGER");
        AuditForm form = (AuditForm) base;
        AuditService service = service(request, "auditService", AuditService.class);
        if ("detail".equals(form.getOp())) {
            request.setAttribute("event", service.getEvent(id(form), actor(request)));
            return view(request, "support/audit-detail", "監査イベント詳細");
        }
        Search search = Inputs.search(form);
        search.setStatus(Inputs.text(form.getOperation(), "操作区分", 60, false));
        request.setAttribute("results", service.searchEvents(
                Inputs.text(form.getEntityType(), "対象種別", 100, false),
                Inputs.optionalId(form.getEntityId(), "対象ID"),
                Inputs.text(form.getActorLogin(), "操作者ID", 50, false), search, actor(request)));
        Map<String, String> filters = new LinkedHashMap<String, String>();
        filters.put("entityType", form.getEntityType()); filters.put("entityId", form.getEntityId());
        filters.put("actorLogin", form.getActorLogin()); filters.put("operation", form.getOperation());
        request.setAttribute("extraPageParameters", filters);
        return view(request, "support/audit", "監査イベント照会");
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        return view(request, "list".equals(form.getOp()) ? "support/audit" : "error", "監査条件の確認");
    }
}
