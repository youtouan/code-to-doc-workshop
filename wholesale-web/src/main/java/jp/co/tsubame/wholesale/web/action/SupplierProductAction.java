package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.entity.SupplierProduct;
import jp.co.tsubame.wholesale.service.PurchasingService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.SupplierProductForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class SupplierProductAction extends BaseAction {
    protected boolean isRead(String op) { return "new".equals(op) || "edit".equals(op); }
    protected boolean isMutation(String op) { return "save".equals(op); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        SupplierProductForm form = (SupplierProductForm) base;
        actor(request).require("MANAGER");
        PurchasingService service = service(request, "purchasingService", PurchasingService.class);
        Supplier supplier = service.getSupplier(Inputs.id(form.getSupplierId(), "仕入先"), actor(request));
        request.setAttribute("supplier", supplier); references(request);
        if ("save".equals(form.getOp())) {
            SupplierProduct input = new SupplierProduct();
            input.setId(Inputs.optionalId(form.getId(), "調達条件"));
            input.setSupplier(supplier);
            Product product = new Product(); product.setId(Inputs.id(form.getProductId(), "商品")); input.setProduct(product);
            input.setSupplierProductCode(Inputs.text(form.getSupplierProductCode(), "仕入先商品コード", 60, false));
            input.setValidFrom(Inputs.date(form.getValidFrom(), "適用開始日", false));
            input.setValidTo(Inputs.date(form.getValidTo(), "適用終了日", true));
            input.setMinimumQuantity(Inputs.quantity(form.getMinimumQuantity(), "最低数量", false));
            input.setOrderPackSize(Inputs.quantity(form.getOrderPackSize(), "仕入入数", false));
            input.setLeadTimeDays(Inputs.integer(form.getLeadTimeDays(), "調達日数", 0, 365));
            input.setUnitCost(Inputs.money(form.getUnitCost(), "仕入単価", false));
            input.setPreferred(Inputs.bool(form.getPreferred(), "優先仕入先"));
            input.setActive(Inputs.bool(form.getActive(), "利用状態"));
            input.setNotes(Inputs.text(form.getNotes(), "備考", 1000, false));
            service.saveSupplierProduct(input, version(form), actor(request));
            return redirect(request, response, "/suppliers.do", supplier.getId(), "調達条件を保存しました。");
        }
        if ("edit".equals(form.getOp())) {
            SupplierProduct found = null;
            for (SupplierProduct item : service.listSupplierProducts(supplier.getId(), actor(request))) {
                if (id(form).equals(item.getId())) { found = item; break; }
            }
            if (found == null) { throw Inputs.invalid("調達条件", "指定した調達条件がありません。"); }
            identity(form, found); form.setProductId(String.valueOf(found.getProduct().getId()));
            form.setSupplierProductCode(found.getSupplierProductCode());
            form.setValidFrom(Dates.format(found.getValidFrom())); form.setValidTo(Dates.format(found.getValidTo()));
            form.setMinimumQuantity(String.valueOf(found.getMinimumQuantity())); form.setOrderPackSize(String.valueOf(found.getOrderPackSize()));
            form.setLeadTimeDays(String.valueOf(found.getLeadTimeDays())); form.setUnitCost(found.getUnitCost().toPlainString());
            form.setPreferred(String.valueOf(found.isPreferred())); form.setActive(String.valueOf(found.isActive()));
            form.setNotes(found.getNotes());
        } else { form.setValidFrom(Dates.format(Dates.today())); }
        return view(request, "purchasing/supplier-product-edit", "調達条件の編集");
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        references(request);
        return view(request, "purchasing/supplier-product-edit", "調達条件の確認");
    }
}
