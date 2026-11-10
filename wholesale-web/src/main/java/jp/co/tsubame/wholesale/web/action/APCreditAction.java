package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.APCredit;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.web.APInputs;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.APCreditForm;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class APCreditAction extends APAction {
    protected boolean isRead(String op) { return "list".equals(op) || "new".equals(op) || "detail".equals(op); }
    protected boolean isMutation(String op) { return "propose".equals(op) || "approve".equals(op) || "cancel".equals(op); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        APCreditForm form = (APCreditForm) base;
        if ("approve".equals(form.getOp())) { actor(request).require("MANAGER"); }
        else if (isMutation(form.getOp())) { actor(request).require("BILLING"); }
        apReferences(form, request);
        if ("list".equals(form.getOp())) {
            request.setAttribute("statuses", new String[] {"DRAFT", "POSTED", "CANCELLED"});
            request.setAttribute("results", invoices(request).searchCredits(APInputs.search(form), actor(request)));
            return view(request, "ap/credits", "仕入先値引・買掛減額");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("BILLING"); APInvoice invoice = source(form, request);
            form.setVersion(String.valueOf(invoice.getVersion())); form.setCreditDate(Dates.format(Dates.today()));
            String[] ids = new String[invoice.getLines().size()], amounts = new String[ids.length];
            for (int i = 0; i < ids.length; i++) { ids[i] = String.valueOf(invoice.getLines().get(i).getId()); amounts[i] = ""; }
            form.setInvoiceLineId(ids); form.setNetAmount(amounts);
            return view(request, "ap/credit-edit", "財務値引の申請");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        APCredit result;
        if ("propose".equals(form.getOp())) {
            result = invoices(request).proposeCredit(Inputs.id(form.getInvoiceId(), "元請求"), version(form), APInputs.credit(form), actor(request));
        } else if ("approve".equals(form.getOp())) {
            result = invoices(request).approveCredit(id(form), version(form), actor(request));
        } else {
            invoices(request).cancelCredit(id(form), version(form), reason(form), actor(request));
            return redirect(request, response, "/apCredits.do", id(form), "値引申請を取消しました。");
        }
        return redirect(request, response, "/apCredits.do", result.getId(), "財務値引処理を実行しました。在庫移動はありません。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        APCreditForm form = (APCreditForm) base;
        if ("list".equals(form.getOp())) { return view(request, "ap/credits", "値引検索条件の確認"); }
        if ("propose".equals(form.getOp())) { source(form, request); return view(request, "ap/credit-edit", "値引内容の確認"); }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "値引の確認");
    }
    private APInvoice source(APCreditForm form, HttpServletRequest request) {
        APInvoice invoice = invoices(request).getInvoice(Inputs.id(form.getInvoiceId(), "元請求"), actor(request));
        request.setAttribute("apInvoice", invoice); return invoice;
    }
    private ActionForward detail(APCreditForm form, HttpServletRequest request, boolean refresh) {
        APCredit credit = invoices(request).getCredit(id(form), actor(request)); request.setAttribute("apCredit", credit);
        if (refresh) { identity(form, credit); }
        return view(request, "ap/credit-detail", "財務値引詳細 · " + credit.getNumber());
    }
}
