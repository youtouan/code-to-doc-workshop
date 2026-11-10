package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.service.ReceivablesService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.StatementForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class StatementAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "detail".equals(op) || "ageing".equals(op) || "cash".equals(op); }
    protected boolean isMutation(String op) { return false; }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) {
        StatementForm form = (StatementForm) base;
        actor(request).require("BILLING", "MANAGER");
        references(request);
        if (form.getFrom().length() == 0) { form.setFrom(Dates.format(Dates.addDays(Dates.today(), -30))); }
        if (form.getTo().length() == 0) { form.setTo(Dates.format(Dates.today())); }
        if (form.getAsOf().length() == 0) { form.setAsOf(Dates.format(Dates.today())); }
        ReceivablesService service = service(request, "receivablesService", ReceivablesService.class);
        if ("cash".equals(form.getOp())) {
            request.setAttribute("results", service.searchCashMovements(Inputs.search(form), actor(request)));
            return view(request, "billing/cash", "入出金履歴");
        }
        if ("detail".equals(form.getOp())) {
            request.setAttribute("statement", service.getStatement(Inputs.id(form.getCustomerId(), "得意先"),
                    Inputs.date(form.getFrom(), "開始日", false), Inputs.date(form.getTo(), "終了日", false), actor(request)));
        }
        if ("ageing".equals(form.getOp())) {
            request.setAttribute("ageing", service.getAgeing(Inputs.id(form.getCustomerId(), "得意先"),
                    Inputs.date(form.getAsOf(), "基準日", false), actor(request)));
        }
        return view(request, "billing/statements", "得意先元帳・滞留債権");
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        references(request);
        return view(request, "billing/statements", "照会条件の確認");
    }
}
