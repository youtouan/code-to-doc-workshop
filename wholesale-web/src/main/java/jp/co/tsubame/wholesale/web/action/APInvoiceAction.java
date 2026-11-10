package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.APInvoiceLine;
import jp.co.tsubame.wholesale.web.APInputs;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.APInvoiceForm;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class APInvoiceAction extends APAction {
    protected boolean isMutation(String op) {
        return "save".equals(op) || "addLine".equals(op) || "approveVariance".equals(op) || "post".equals(op) || "cancel".equals(op);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        APInvoiceForm form = (APInvoiceForm) base;
        if ("approveVariance".equals(form.getOp())) { actor(request).require("MANAGER"); }
        else if (isMutation(form.getOp())) { actor(request).require("BILLING"); }
        apReferences(form, request);
        if ("list".equals(form.getOp())) {
            request.setAttribute("statuses", new String[] {"DRAFT", "POSTED", "CANCELLED"});
            request.setAttribute("results", invoices(request).searchInvoices(APInputs.search(form), actor(request)));
            return view(request, "ap/invoices", "仕入先請求・買掛計上");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("BILLING"); form.setInvoiceDate(Dates.format(Dates.today())); form.rows(5);
            return edit(form, request);
        }
        if ("edit".equals(form.getOp())) {
            actor(request).require("BILLING");
            APInvoice invoice = invoices(request).getInvoice(id(form), actor(request));
            if (!"DRAFT".equals(invoice.getStatus())) { throw Inputs.invalid("状態", "下書き請求のみ編集できます。"); }
            identity(form, invoice); form.setSupplierId(String.valueOf(invoice.getSupplier().getId()));
            form.setSupplierInvoiceNumber(invoice.getSupplierInvoiceNumber());
            form.setInvoiceDate(Dates.format(invoice.getInvoiceDate())); form.setDueDate(Dates.format(invoice.getDueDate()));
            form.setNote(invoice.getNotes()); form.rows(Math.max(5, invoice.getLines().size()));
            for (int i = 0; i < invoice.getLines().size(); i++) {
                APInvoiceLine line = invoice.getLines().get(i);
                form.getProductId()[i] = String.valueOf(line.getProduct().getId()); form.getQuantity()[i] = String.valueOf(line.getQuantity());
                form.getDescription()[i] = line.getDescription(); form.getUnitPrice()[i] = line.getUnitPrice().toPlainString();
                form.getTaxRate()[i] = line.getTaxRate().toPlainString();
            }
            return edit(form, request);
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        if ("addLine".equals(form.getOp())) {
            Inputs.arrays(200, form.getProductId(), form.getQuantity(), form.getDescription(), form.getUnitPrice(), form.getTaxRate());
            form.rows(Math.min(200, form.getProductId().length + 5)); return edit(form, request);
        }
        APInvoice saved;
        if ("save".equals(form.getOp())) {
            saved = invoices(request).saveDraft(Inputs.optionalId(form.getId(), "仕入先請求"), version(form), APInputs.invoice(form), actor(request));
        } else if ("approveVariance".equals(form.getOp())) {
            saved = invoices(request).approveVariance(id(form), version(form), reason(form), actor(request));
        } else if ("post".equals(form.getOp())) {
            saved = invoices(request).postInvoice(id(form), version(form), actor(request));
        } else {
            invoices(request).cancelDraft(id(form), version(form), reason(form), actor(request));
            return redirect(request, response, "/apInvoices.do", id(form), "仕入先請求下書きを取消しました。");
        }
        return redirect(request, response, "/apInvoices.do", saved.getId(), "仕入先請求処理を実行しました。照合・差異・計上状態をご確認ください。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        APInvoiceForm form = (APInvoiceForm) base;
        if ("list".equals(form.getOp())) { return view(request, "ap/invoices", "仕入先請求検索条件の確認"); }
        if ("save".equals(form.getOp()) || "addLine".equals(form.getOp())) { return edit(form, request); }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "仕入先請求の確認");
    }
    private ActionForward edit(APInvoiceForm form, HttpServletRequest request) {
        request.setAttribute("products", catalog(request).listActiveProducts(actor(request)));
        if (form.getId().length() > 0) { request.setAttribute("apInvoice", invoices(request).getInvoice(id(form), actor(request))); }
        return view(request, "ap/invoice-edit", "仕入先請求下書き");
    }
    private ActionForward detail(APInvoiceForm form, HttpServletRequest request, boolean refresh) {
        APInvoice invoice = invoices(request).getInvoice(id(form), actor(request)); request.setAttribute("apInvoice", invoice);
        if (refresh) { identity(form, invoice); }
        return view(request, "ap/invoice-detail", "仕入先請求詳細 · " + invoice.getNumber());
    }
}
