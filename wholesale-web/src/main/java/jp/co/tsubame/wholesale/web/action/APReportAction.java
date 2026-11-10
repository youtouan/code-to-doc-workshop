package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.APSearch;
import jp.co.tsubame.wholesale.common.APStatement;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.web.APInputs;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.APForm;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class APReportAction extends APAction {
    protected boolean isRead(String op) {
        return "list".equals(op) || "open".equals(op) || "receipts".equals(op) || "variance".equals(op) || "statement".equals(op);
    }
    protected boolean isMutation(String op) { return false; }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) {
        APForm form = (APForm) base; actor(request).require("BILLING", "MANAGER");
        String mode = "list".equals(form.getOp()) ? "open" : form.getOp(); request.setAttribute("reportMode", mode);
        if (form.getAsOf().length() == 0) { form.setAsOf(Dates.format(Dates.today())); }
        if ("statement".equals(mode)) {
            if (form.getFrom().length() == 0) { form.setFrom(Dates.format(Dates.addDays(Dates.today(), -30))); }
            if (form.getTo().length() == 0) { form.setTo(Dates.format(Dates.today())); }
        }
        apReferences(form, request);
        APSearch search = APInputs.search(form);
        if ("receipts".equals(mode)) {
            request.setAttribute("results", reports(request).searchAvailableReceipts(search, actor(request)));
        } else if ("variance".equals(mode)) {
            request.setAttribute("results", reports(request).searchVarianceQueue(search, actor(request)));
        } else if ("statement".equals(mode)) {
            if (form.getSupplierId().length() > 0) {
                APStatement statement = reports(request).getStatement(Inputs.id(form.getSupplierId(), "仕入先"),
                        Inputs.date(form.getFrom(), "開始日", false), Inputs.date(form.getTo(), "終了日", false),
                        search.getPage(), search.getSize(), actor(request));
                request.setAttribute("statement", statement); request.setAttribute("results", statement.getEntries());
            }
        } else {
            request.setAttribute("results", reports(request).searchOpenItems(search,
                    Inputs.date(form.getAsOf(), "基準日", false), Inputs.bool(form.getOverdueOnly(), "期限超過"), actor(request)));
        }
        return view(request, "ap/reports", "買掛レポート・仕入先元帳");
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        return view(request, "ap/reports", "買掛照会条件の確認");
    }
}
