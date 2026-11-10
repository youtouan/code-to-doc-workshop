package jp.co.tsubame.wholesale.web.action;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.PriceAgreement;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.PriceForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class PriceAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "edit".equals(op) || "new".equals(op); }
    protected boolean isMutation(String op) { return "save".equals(op) || "delete".equals(op); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        PriceForm form = (PriceForm) base;
        references(request);
        if ("save".equals(form.getOp())) {
            actor(request).require("MANAGER");
            PriceAgreement value = new PriceAgreement();
            value.setId(Inputs.optionalId(form.getId(), "価格条件ID"));
            Customer customer = new Customer(); customer.setId(Inputs.id(form.getCustomerId(), "得意先"));
            Product product = new Product(); product.setId(Inputs.id(form.getProductId(), "商品"));
            value.setCustomer(customer); value.setProduct(product);
            value.setValidFrom(Inputs.date(form.getValidFrom(), "適用開始日", false));
            value.setValidTo(Inputs.date(form.getValidTo(), "適用終了日", true));
            value.setMinimumQuantity(Inputs.quantity(form.getMinimumQuantity(), "最低数量", false));
            value.setUnitPrice(Inputs.money(form.getUnitPrice(), "契約単価", false));
            value.setNotes(Inputs.text(form.getNotes(), "備考", 1000, false));
            catalog(request).savePriceAgreement(value, version(form), actor(request));
            return saved(request, response, customer.getId(), "価格条件を保存しました。");
        }
        if ("delete".equals(form.getOp())) {
            actor(request).require("MANAGER");
            Long customerId = Inputs.id(form.getCustomerId(), "得意先");
            find(form, request);
            catalog(request).deletePriceAgreement(id(form), version(form), actor(request));
            return saved(request, response, customerId, "価格条件を削除しました。");
        }
        if ("new".equals(form.getOp()) || "edit".equals(form.getOp())) {
            actor(request).require("MANAGER");
            if ("edit".equals(form.getOp())) {
                PriceAgreement value = find(form, request);
                identity(form, value);
                form.setProductId(String.valueOf(value.getProduct().getId()));
                form.setValidFrom(Dates.format(value.getValidFrom())); form.setValidTo(Dates.format(value.getValidTo()));
                form.setMinimumQuantity(String.valueOf(value.getMinimumQuantity()));
                form.setUnitPrice(value.getUnitPrice().toPlainString()); form.setNotes(value.getNotes());
            } else {
                form.setValidFrom(Dates.format(Dates.today()));
            }
            return view(request, "catalog/price-edit", "得意先別価格条件の編集");
        }
        if (Inputs.optionalId(form.getCustomerId(), "得意先") != null) {
            request.setAttribute("agreements", catalog(request).listPriceAgreements(
                    Inputs.id(form.getCustomerId(), "得意先"), actor(request)));
        }
        return view(request, "catalog/prices", "得意先別価格条件");
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        references(request);
        if ("list".equals(form.getOp())) { return view(request, "catalog/prices", "価格条件の検索"); }
        return view(request, "catalog/price-edit", "価格条件の確認");
    }
    private PriceAgreement find(PriceForm form, HttpServletRequest request) {
        List<PriceAgreement> values = catalog(request).listPriceAgreements(
                Inputs.id(form.getCustomerId(), "得意先"), actor(request));
        Long selected = id(form);
        for (PriceAgreement value : values) {
            if (selected.equals(value.getId())) { return value; }
        }
        throw Inputs.invalid("価格条件", "この得意先に指定された価格条件はありません。");
    }
    private ActionForward saved(HttpServletRequest request, HttpServletResponse response, Long customer, String message) {
        request.getSession().setAttribute("flash", message);
        response.setStatus(303);
        response.setHeader("Location", request.getContextPath() + "/prices.do?customerId=" + customer);
        return null;
    }
}
