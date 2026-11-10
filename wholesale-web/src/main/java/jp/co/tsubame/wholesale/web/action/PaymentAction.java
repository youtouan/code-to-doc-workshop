package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.PaymentAllocation;
import jp.co.tsubame.wholesale.entity.PaymentReceipt;
import jp.co.tsubame.wholesale.service.ReceivablesService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.Web;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.PaymentForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class PaymentAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "detail".equals(op) || "new".equals(op); }
    protected boolean isMutation(String op) {
        return "receive".equals(op) || "allocate".equals(op) || "reverse".equals(op) || "cancel".equals(op);
    }
    private ReceivablesService receivables(HttpServletRequest request) {
        return service(request, "receivablesService", ReceivablesService.class);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        PaymentForm form = (PaymentForm) base;
        if (isMutation(form.getOp())) { actor(request).require("BILLING"); }
        ReceivablesService service = receivables(request);
        if ("list".equals(form.getOp())) {
            references(request); request.setAttribute("statuses", new String[] {"POSTED", "CANCELLED"});
            request.setAttribute("searchCustomer", Boolean.TRUE); request.setAttribute("searchDates", Boolean.TRUE);
            request.setAttribute("results", service.searchReceipts(Inputs.search(form), actor(request)));
            return view(request, "billing/payments", "入金・消込一覧");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("BILLING");
            references(request); form.setRequestKey(Web.randomToken()); form.setReceivedDate(Dates.format(Dates.today()));
            return view(request, "billing/payment-edit", "入金登録");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        if ("receive".equals(form.getOp())) {
            PaymentReceipt receipt = service.receive(Inputs.text(form.getRequestKey(), "受付キー", 100, true),
                    Inputs.id(form.getCustomerId(), "得意先"), Inputs.date(form.getReceivedDate(), "入金日", false),
                    Inputs.money(form.getAmount(), "入金額", false),
                    Inputs.choice(form.getMethod(), "入金方法", "BANK_TRANSFER", "CASH", "CHEQUE"),
                    Inputs.text(form.getReference(), "照合番号", 100, false), Inputs.text(form.getNotes(), "備考", 1000, false),
                    actor(request));
            return redirect(request, response, "/payments.do", receipt.getId(), "入金を登録しました。請求への消込を行えます。");
        }
        Long id = id(form);
        if ("allocate".equals(form.getOp())) {
            service.allocate(id, version(form), Inputs.id(form.getInvoiceId(), "請求書"),
                    Inputs.money(form.getAmount(), "消込額", false), actor(request));
        } else if ("reverse".equals(form.getOp())) {
            Long allocationId = Inputs.id(form.getAllocationId(), "消込");
            PaymentReceipt receipt = service.getReceipt(id, actor(request));
            boolean belongs = false;
            for (PaymentAllocation allocation : receipt.getAllocations()) {
                if (allocationId.equals(allocation.getId())) { belongs = true; break; }
            }
            if (!belongs) { throw Inputs.invalid("消込", "この入金に属する消込を指定してください。"); }
            service.reverseAllocation(allocationId, Inputs.integer(form.getAllocationVersion(), "消込更新番号", 0, Integer.MAX_VALUE),
                    reason(form), actor(request));
        } else { service.cancelReceipt(id, version(form), reason(form), actor(request)); }
        return redirect(request, response, "/payments.do", id, "入金・消込処理を実行しました。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        PaymentForm form = (PaymentForm) base;
        if ("list".equals(form.getOp())) { return view(request, "billing/payments", "入金検索条件の確認"); }
        if ("receive".equals(form.getOp())) {
            references(request);
            return view(request, "billing/payment-edit", "入金内容の確認");
        }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "入金条件の確認");
    }
    private ActionForward detail(PaymentForm form, HttpServletRequest request, boolean refresh) {
        PaymentReceipt receipt = receivables(request).getReceipt(id(form), actor(request));
        request.setAttribute("payment", receipt);
        request.setAttribute("openInvoices", receivables(request).listOpenInvoices(receipt.getCustomer().getId(), actor(request)));
        if (refresh) { identity(form, receipt); }
        return view(request, "billing/payment-detail", "入金詳細 · " + receipt.getNumber());
    }
}
