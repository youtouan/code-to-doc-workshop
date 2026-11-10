package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.Quotation;
import jp.co.tsubame.wholesale.entity.QuotationLine;
import jp.co.tsubame.wholesale.entity.QuotationRevision;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.service.QuotationService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.QuotationInputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.QuotationForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class QuotationAction extends BaseAction {
    protected boolean isRead(String op) { return super.isRead(op) || "reviseForm".equals(op) || "revision".equals(op); }
    protected boolean isMutation(String op) {
        return "save".equals(op) || "revise".equals(op) || "addLine".equals(op) || "submit".equals(op)
                || "approve".equals(op) || "withdraw".equals(op) || "reject".equals(op) || "cancel".equals(op)
                || "accept".equals(op) || "expire".equals(op) || "convert".equals(op);
    }
    private QuotationService quotations(HttpServletRequest request) { return service(request, "quotationService", QuotationService.class); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        QuotationForm form = (QuotationForm) base;
        if ("approve".equals(form.getOp()) || "reject".equals(form.getOp())) { actor(request).require("MANAGER"); }
        else if ("expire".equals(form.getOp())) { actor(request).require("SALES", "MANAGER", "BATCH"); }
        else if (isMutation(form.getOp()) || "new".equals(form.getOp()) || "edit".equals(form.getOp()) || "reviseForm".equals(form.getOp())) {
            actor(request).require("SALES", "MANAGER");
        }
        QuotationService service = quotations(request);
        if ("list".equals(form.getOp())) {
            references(request); request.setAttribute("searchCustomer", Boolean.TRUE); request.setAttribute("searchWarehouse", Boolean.TRUE);
            request.setAttribute("searchDates", Boolean.TRUE);
            request.setAttribute("statuses", new String[] {"DRAFT","SUBMITTED","APPROVED","ACCEPTED","WITHDRAWN","REJECTED","EXPIRED","CANCELLED","CONVERTED"});
            request.setAttribute("results", service.searchQuotations(Inputs.search(form), actor(request)));
            return view(request, "quotations/list", "見積・提案・承認一覧");
        }
        if ("new".equals(form.getOp())) {
            form.setQuoteDate(Dates.format(Dates.today())); form.setValidUntil(Dates.format(Dates.addDays(Dates.today(), 30)));
            form.setRequestedDate(Dates.format(Dates.addDays(Dates.today(), 7))); form.setSaveMode("save"); form.rows(5);
            return edit(form, request);
        }
        if ("edit".equals(form.getOp()) || "reviseForm".equals(form.getOp())) {
            Quotation quote = service.getQuotation(id(form), actor(request));
            if ("CONVERTED".equals(quote.getStatus()) || "CANCELLED".equals(quote.getStatus())
                    || ("edit".equals(form.getOp()) && !quote.isEditable())) {
                throw Inputs.invalid("見積状態", "この状態では編集できません。必要に応じて改訂を選択してください。");
            }
            populate(form, quote); form.setSaveMode("reviseForm".equals(form.getOp()) ? "revise" : "save");
            return edit(form, request);
        }
        if ("revision".equals(form.getOp())) {
            request.setAttribute("quote", service.getQuotation(id(form), actor(request)));
            request.setAttribute("quoteRevision", service.getRevision(id(form),
                    Inputs.integer(form.getRevisionNumber(), "版番号", 1, 100), actor(request)));
            return view(request, "quotations/revision", "見積の保存済み版");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        if ("addLine".equals(form.getOp())) {
            Inputs.choice(form.getSaveMode(), "保存方法", "save", "revise");
            Inputs.arrays(200, form.getProductId(), form.getQuantity(), form.getNegotiatedUnitPrice(), form.getNegotiationReason());
            form.rows(Math.min(200, form.getProductId().length + 5)); return edit(form, request);
        }
        if ("convert".equals(form.getOp())) {
            SalesOrder order = service.convertToOrder(id(form), version(form), actor(request));
            return redirect(request, response, "/orders.do", order.getId(), "見積に対応する受注下書きを表示します。通常の受注申請・承認へ進んでください。");
        }
        Quotation result;
        if ("save".equals(form.getOp())) {
            result = service.saveDraft(Inputs.optionalId(form.getId(), "見積"), version(form), QuotationInputs.quotation(form), actor(request));
        } else if ("revise".equals(form.getOp())) {
            result = service.revise(id(form), version(form), QuotationInputs.quotation(form), reason(form), actor(request));
        } else if ("submit".equals(form.getOp())) { result = service.submit(id(form), version(form), actor(request)); }
        else if ("approve".equals(form.getOp())) { result = service.approve(id(form), version(form), actor(request)); }
        else if ("accept".equals(form.getOp())) {
            result = service.accept(id(form), version(form), Inputs.date(form.getAcceptedOn(), "顧客承諾日", false),
                    Inputs.text(form.getCustomerReference(), "顧客承諾参照番号", 120, true), actor(request));
        } else if ("withdraw".equals(form.getOp())) { result = service.withdraw(id(form), version(form), reason(form), actor(request)); }
        else if ("reject".equals(form.getOp())) { result = service.reject(id(form), version(form), reason(form), actor(request)); }
        else if ("cancel".equals(form.getOp())) { result = service.cancel(id(form), version(form), reason(form), actor(request)); }
        else { result = service.expire(id(form), version(form), actor(request)); }
        return redirect(request, response, "/quotations.do", result.getId(), "見積処理を実行しました。版・状態・履歴をご確認ください。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        QuotationForm form = (QuotationForm) base;
        if ("list".equals(form.getOp())) { return view(request, "quotations/list", "見積検索条件の確認"); }
        if ("save".equals(form.getOp()) || "revise".equals(form.getOp()) || "addLine".equals(form.getOp())) {
            if (!"addLine".equals(form.getOp())) { form.setSaveMode(form.getOp()); }
            return edit(form, request);
        }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "見積の確認");
    }
    private ActionForward edit(QuotationForm form, HttpServletRequest request) {
        references(request); request.setAttribute("revising", Boolean.valueOf("revise".equals(form.getSaveMode())));
        return view(request, "quotations/edit", "revise".equals(form.getSaveMode()) ? "見積を新しい版へ改訂" : "見積下書き・交渉提案");
    }
    private ActionForward detail(QuotationForm form, HttpServletRequest request, boolean refresh) {
        Quotation quote = quotations(request).getQuotation(id(form), actor(request));
        request.setAttribute("quote", quote); request.setAttribute("quoteRevision", quote.getCurrentRevision());
        request.setAttribute("events", quotations(request).listEvents(quote.getId(), actor(request))); request.setAttribute("today", Dates.today());
        if (refresh) { identity(form, quote); form.setAcceptedOn(Dates.format(Dates.today())); }
        return view(request, "quotations/detail", "見積詳細 · " + quote.getNumber());
    }
    private void populate(QuotationForm form, Quotation quote) {
        identity(form, quote); QuotationRevision revision = quote.getCurrentRevision();
        form.setCustomerId(String.valueOf(quote.getCustomer().getId())); form.setWarehouseId(String.valueOf(quote.getWarehouse().getId()));
        form.setQuoteDate(Dates.format(revision.getQuoteDate())); form.setValidUntil(Dates.format(revision.getValidUntil()));
        form.setRequestedDate(Dates.format(revision.getRequestedDate())); form.setDeliveryAddress(revision.getDeliveryAddress());
        form.setExternalReference(revision.getExternalReference()); form.setNotes(revision.getNotes()); form.rows(Math.max(5, revision.getLines().size()));
        for (int i = 0; i < revision.getLines().size(); i++) {
            QuotationLine line = revision.getLines().get(i); form.getProductId()[i] = String.valueOf(line.getProduct().getId());
            form.getQuantity()[i] = String.valueOf(line.getQuantity());
            form.getNegotiatedUnitPrice()[i] = line.isNegotiated() ? line.getUnitPrice().toPlainString() : "";
            form.getNegotiationReason()[i] = line.isNegotiated() ? line.getNegotiationReason() : "";
        }
    }
}
