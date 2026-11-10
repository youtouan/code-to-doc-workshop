package jp.co.tsubame.wholesale.web.action;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.APMatchInput;
import jp.co.tsubame.wholesale.common.APReceiptAvailability;
import jp.co.tsubame.wholesale.common.APSearch;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.entity.APInvoice;
import jp.co.tsubame.wholesale.entity.APInvoiceLine;
import jp.co.tsubame.wholesale.entity.APMatch;
import jp.co.tsubame.wholesale.web.APInputs;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.APMatchForm;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class APMatchAction extends APAction {
    protected boolean isRead(String op) { return "list".equals(op) || "edit".equals(op); }
    protected boolean isMutation(String op) { return "save".equals(op) || "clear".equals(op) || "addLine".equals(op); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        actor(request).require("BILLING");
        APMatchForm form = (APMatchForm) base;
        if ("save".equals(form.getOp()) || "clear".equals(form.getOp())) {
            invoices(request).replaceMatches(id(form), version(form), "clear".equals(form.getOp())
                    ? Collections.<APMatchInput>emptyList() : APInputs.matches(form), actor(request));
            return redirect(request, response, "/apInvoices.do", id(form), "入荷照合を更新しました。差異承認は再評価されます。");
        }
        APInvoice invoice = source(form, request);
        if ("addLine".equals(form.getOp())) {
            Inputs.arrays(500, form.getInvoiceLineId(), form.getReceiptLineId(), form.getQuantity());
            form.rows(Math.min(500, form.getInvoiceLineId().length + 5));
        } else {
            identity(form, invoice);
            int count = 0;
            for (APInvoiceLine line : invoice.getLines()) { count += line.getMatches().size(); }
            form.rows(Math.min(500, Math.max(5, count + 3)));
            int index = 0;
            for (APInvoiceLine line : invoice.getLines()) {
                for (APMatch match : line.getMatches()) {
                    form.getInvoiceLineId()[index] = String.valueOf(line.getId());
                    form.getReceiptLineId()[index] = String.valueOf(match.getReceiptLine().getId());
                    form.getQuantity()[index] = String.valueOf(match.getQuantity()); index++;
                }
            }
        }
        return view(request, "ap/matches", "発注・検収入荷・請求の照合");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        source((APMatchForm) base, request);
        return view(request, "ap/matches", "入荷照合内容の確認");
    }
    private APInvoice source(APMatchForm form, HttpServletRequest request) {
        APInvoice invoice = invoices(request).getInvoice(id(form), actor(request));
        if (!"DRAFT".equals(invoice.getStatus())) { throw Inputs.invalid("状態", "下書き請求のみ照合できます。"); }
        request.setAttribute("apInvoice", invoice);
        APSearch search = new APSearch(); search.setSupplierId(invoice.getSupplier().getId()); search.setTo(invoice.getInvoiceDate()); search.setSize(100);
        Page<APReceiptAvailability> available = reports(request).searchAvailableReceipts(search, actor(request));
        Map<Long, String> choices = new LinkedHashMap<Long, String>();
        for (APReceiptAvailability row : available.getItems()) {
            choices.put(row.getReceiptLineId(), row.getReceiptNumber() + " / " + row.getProductCode() + " / 未照合 " + row.getAvailableQuantity());
        }
        for (APInvoiceLine line : invoice.getLines()) {
            for (APMatch match : line.getMatches()) {
                if (!choices.containsKey(match.getReceiptLine().getId())) {
                    choices.put(match.getReceiptLine().getId(), match.getReceiptNumber() + " / " + line.getProductCode() + " / この請求で照合済");
                }
            }
        }
        request.setAttribute("receiptChoices", choices); request.setAttribute("availableReceiptCount", Long.valueOf(available.getTotal()));
        return invoice;
    }
}
