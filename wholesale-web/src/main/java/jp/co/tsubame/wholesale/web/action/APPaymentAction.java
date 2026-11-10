package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.APSearch;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.APPaymentVoucher;
import jp.co.tsubame.wholesale.service.APSettlementService;
import jp.co.tsubame.wholesale.web.APInputs;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.Web;
import jp.co.tsubame.wholesale.web.form.APPaymentForm;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class APPaymentAction extends APAction {
    protected boolean isRead(String op) { return "list".equals(op) || "new".equals(op) || "detail".equals(op); }
    protected boolean isMutation(String op) { return "pay".equals(op) || "addLine".equals(op) || "cancel".equals(op); }
    private APSettlementService settlements(HttpServletRequest request) {
        return service(request, "apSettlementService", APSettlementService.class);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        APPaymentForm form = (APPaymentForm) base;
        if (isMutation(form.getOp())) { actor(request).require("BILLING"); }
        apReferences(form, request);
        if ("list".equals(form.getOp())) {
            request.setAttribute("statuses", new String[] {"POSTED", "CANCELLED"});
            request.setAttribute("results", settlements(request).searchPayments(APInputs.search(form), actor(request)));
            return view(request, "ap/payments", "仕入先への支払・消込");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("BILLING"); form.rows(5); form.setPaymentDate(Dates.format(Dates.today()));
            form.setRequestKey(Web.randomToken()); return edit(form, request);
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        if ("addLine".equals(form.getOp())) {
            Inputs.arrays(200, form.getInvoiceId(), form.getAllocationAmount());
            form.rows(Math.min(200, form.getInvoiceId().length + 5)); return edit(form, request);
        }
        if ("pay".equals(form.getOp())) {
            APPaymentVoucher payment = settlements(request).pay(APInputs.payment(form), actor(request));
            return redirect(request, response, "/apPayments.do", payment.getId(), "仕入先支払と全請求の消込を計上しました。");
        }
        settlements(request).cancelPayment(id(form), version(form), reason(form), actor(request));
        return redirect(request, response, "/apPayments.do", id(form), "支払を取消し、全消込を戻しました。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        APPaymentForm form = (APPaymentForm) base;
        if ("list".equals(form.getOp())) { return view(request, "ap/payments", "支払検索条件の確認"); }
        if ("pay".equals(form.getOp()) || "addLine".equals(form.getOp())) { return edit(form, request); }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "支払の確認");
    }
    private ActionForward edit(APPaymentForm form, HttpServletRequest request) {
        if (form.getSupplierId().length() > 0) {
            APSearch search = new APSearch(); search.setSupplierId(Inputs.id(form.getSupplierId(), "仕入先")); search.setSize(100);
            request.setAttribute("openItems", reports(request).searchOpenItems(search, Dates.today(), false, actor(request)));
        }
        return view(request, "ap/payment-edit", "仕入先支払の登録");
    }
    private ActionForward detail(APPaymentForm form, HttpServletRequest request, boolean refresh) {
        APPaymentVoucher payment = settlements(request).getPayment(id(form), actor(request)); request.setAttribute("apPayment", payment);
        if (refresh) { identity(form, payment); }
        return view(request, "ap/payment-detail", "仕入先支払詳細 · " + payment.getNumber());
    }
}
