package jp.co.tsubame.wholesale.web.action;

import java.util.Calendar;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.Invoice;
import jp.co.tsubame.wholesale.service.BillingService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.InvoiceForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class InvoiceAction extends BaseAction {
    protected boolean isRead(String op) {
        return "list".equals(op) || "detail".equals(op) || "new".equals(op) || "credits".equals(op);
    }
    protected boolean isMutation(String op) { return "prepare".equals(op) || "finalize".equals(op) || "cancel".equals(op); }
    private BillingService billing(HttpServletRequest request) { return service(request, "billingService", BillingService.class); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        InvoiceForm form = (InvoiceForm) base;
        BillingService service = billing(request);
        if ("cancel".equals(form.getOp())) { actor(request).require("BILLING"); }
        else if (isMutation(form.getOp())) { actor(request).require("BILLING", "BATCH"); }
        if ("list".equals(form.getOp())) {
            references(request); request.setAttribute("statuses", new String[] {"DRAFT", "FINALIZED", "VOID"});
            request.setAttribute("searchCustomer", Boolean.TRUE); request.setAttribute("searchDates", Boolean.TRUE);
            request.setAttribute("results", service.searchInvoices(Inputs.search(form), actor(request)));
            return view(request, "billing/invoices", "請求一覧");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("BILLING", "BATCH");
            references(request);
            if (form.getPeriodEnd().length() == 0) {
                form.setPeriodEnd(Dates.format(defaultPeriodEnd(Dates.today())));
            }
            request.setAttribute("closingCustomers", service.listClosingCustomers(
                    Inputs.date(form.getPeriodEnd(), "締日", false), actor(request)));
            return view(request, "billing/prepare", "請求書の作成");
        }
        if ("credits".equals(form.getOp())) {
            actor(request).require("BILLING", "MANAGER");
            references(request);
            if (form.getCustomerId().length() > 0) {
                request.setAttribute("credits", service.listCredits(Inputs.id(form.getCustomerId(), "得意先"), actor(request)));
            }
            return view(request, "billing/credits", "返品クレジット一覧");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        if ("prepare".equals(form.getOp())) {
            Invoice invoice = service.prepare(Inputs.id(form.getCustomerId(), "得意先"),
                    Inputs.date(form.getPeriodEnd(), "締日", false), actor(request));
            return redirect(request, response, "/invoices.do", invoice.getId(), "請求書を作成しました。明細を確認して確定してください。");
        }
        if ("finalize".equals(form.getOp())) { service.finalizeInvoice(id(form), version(form), actor(request)); }
        else { service.cancelDraft(id(form), version(form), reason(form), actor(request)); }
        return redirect(request, response, "/invoices.do", id(form), "請求処理を実行しました。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        InvoiceForm form = (InvoiceForm) base;
        if ("list".equals(form.getOp())) { return view(request, "billing/invoices", "請求検索条件の確認"); }
        if ("prepare".equals(form.getOp()) || "new".equals(form.getOp())) {
            references(request);
            return view(request, "billing/prepare", "請求作成条件の確認");
        }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "請求条件の確認");
    }
    private ActionForward detail(InvoiceForm form, HttpServletRequest request, boolean refresh) {
        Invoice invoice = billing(request).getInvoice(id(form), actor(request));
        request.setAttribute("invoice", invoice);
        if (actor(request).hasRole("ADMIN") || actor(request).hasRole("BILLING") || actor(request).hasRole("MANAGER")) {
            request.setAttribute("credits", billing(request).listCredits(invoice.getCustomer().getId(), actor(request)));
        }
        if (refresh) { identity(form, invoice); }
        return view(request, "billing/invoice-detail", "請求詳細 · " + invoice.getNumber());
    }
    static Date defaultPeriodEnd(Date onDate) {
        Date day = Dates.day(onDate);
        if (Dates.sameDay(day, Dates.monthEnd(day))) { return day; }
        int dayNumber = Dates.calendar(day).get(Calendar.DAY_OF_MONTH);
        if (dayNumber >= 20) { return Dates.closingDate(day, 20); }
        if (dayNumber >= 10) { return Dates.closingDate(day, 10); }
        return Dates.monthEnd(Dates.addMonths(day, -1));
    }
}
