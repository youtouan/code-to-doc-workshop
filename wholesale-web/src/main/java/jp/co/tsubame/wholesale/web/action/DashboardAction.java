package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.service.DashboardService;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class DashboardAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op); }
    protected boolean isMutation(String op) { return false; }
    protected ActionForward perform(ActionMapping mapping, BaseForm form, HttpServletRequest request,
                                    HttpServletResponse response) {
        request.setAttribute("dashboard", service(request, "dashboardService", DashboardService.class)
                .getDashboard(actor(request)));
        return view(request, "dashboard", "業務ダッシュボード");
    }
}
