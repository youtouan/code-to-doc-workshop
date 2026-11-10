package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.service.PurchasingService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.SupplierForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class SupplierAction extends BaseAction {
    protected boolean isMutation(String op) { return "save".equals(op); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        SupplierForm form = (SupplierForm) base;
        if (isMutation(form.getOp())) { actor(request).require("MANAGER"); }
        PurchasingService service = service(request, "purchasingService", PurchasingService.class);
        if ("list".equals(form.getOp())) {
            request.setAttribute("statuses", new String[] {"ACTIVE", "INACTIVE", "HOLD"});
            request.setAttribute("results", service.searchSuppliers(Inputs.search(form), actor(request)));
            return view(request, "purchasing/suppliers", "仕入先一覧");
        }
        if ("save".equals(form.getOp())) {
            Supplier input = new Supplier();
            input.setId(Inputs.optionalId(form.getId(), "仕入先ID"));
            input.setCode(Inputs.text(form.getCode(), "仕入先コード", 30, true));
            input.setName(Inputs.text(form.getName(), "名称", 100, true));
            input.setActive(Inputs.bool(form.getActive(), "利用状態")); input.setOnHold(Inputs.bool(form.getOnHold(), "発注保留"));
            input.setClosingDay(Integer.parseInt(Inputs.choice(form.getClosingDay(), "締日", "10", "20", "31")));
            input.setPaymentTermDays(Inputs.integer(form.getPaymentTermDays(), "支払サイト", 0, 365));
            input.setDefaultLeadTimeDays(Inputs.integer(form.getDefaultLeadTimeDays(), "標準納期日数", 0, 365));
            input.setMinimumOrderAmount(Inputs.money(form.getMinimumOrderAmount(), "最低発注額", false));
            input.setTaxRounding(Inputs.choice(form.getTaxRounding(), "税端数処理", "DOWN", "UP", "HALF_UP"));
            input.setPostalCode(Inputs.text(form.getPostalCode(), "郵便番号", 12, false));
            input.setAddress(Inputs.text(form.getAddress(), "住所", 250, false));
            input.setTelephone(Inputs.text(form.getTelephone(), "電話番号", 30, false));
            input.setOrderingInstructions(Inputs.text(form.getOrderingInstructions(), "発注条件", 1000, false));
            input.setNotes(Inputs.text(form.getNotes(), "備考", 1000, false));
            Supplier saved = service.saveSupplier(input, version(form), actor(request));
            return redirect(request, response, "/suppliers.do", saved.getId(), "仕入先を保存しました。");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("MANAGER");
            return view(request, "purchasing/supplier-edit", "仕入先の新規登録");
        }
        Supplier supplier = service.getSupplier(id(form), actor(request));
        identity(form, supplier); request.setAttribute("supplier", supplier);
        if ("edit".equals(form.getOp())) {
            actor(request).require("MANAGER"); populate(form, supplier);
            return view(request, "purchasing/supplier-edit", "仕入先の編集");
        }
        request.setAttribute("supplierProducts", service.listSupplierProducts(supplier.getId(), actor(request)));
        return view(request, "purchasing/supplier-detail", "仕入先詳細 · " + supplier.getName());
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        if ("list".equals(form.getOp())) { return view(request, "purchasing/suppliers", "仕入先検索条件の確認"); }
        if (!"save".equals(form.getOp())) { return view(request, "error", "仕入先の確認"); }
        return view(request, "purchasing/supplier-edit", "仕入先内容の確認");
    }
    private void populate(SupplierForm form, Supplier supplier) {
        form.setCode(supplier.getCode()); form.setName(supplier.getName());
        form.setActive(String.valueOf(supplier.isActive())); form.setOnHold(String.valueOf(supplier.isOnHold()));
        form.setClosingDay(String.valueOf(supplier.getClosingDay()));
        form.setPaymentTermDays(String.valueOf(supplier.getPaymentTermDays()));
        form.setDefaultLeadTimeDays(String.valueOf(supplier.getDefaultLeadTimeDays()));
        form.setMinimumOrderAmount(supplier.getMinimumOrderAmount().toPlainString());
        form.setTaxRounding(supplier.getTaxRounding()); form.setPostalCode(supplier.getPostalCode());
        form.setAddress(supplier.getAddress()); form.setTelephone(supplier.getTelephone());
        form.setOrderingInstructions(supplier.getOrderingInstructions()); form.setNotes(supplier.getNotes());
    }
}
