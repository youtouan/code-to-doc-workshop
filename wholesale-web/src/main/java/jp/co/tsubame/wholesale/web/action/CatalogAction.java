package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.entity.BaseEntity;
import jp.co.tsubame.wholesale.entity.Customer;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.CatalogService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.CatalogForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class CatalogAction extends BaseAction {
    protected boolean isMutation(String op) { return "save".equals(op); }

    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        CatalogForm form = (CatalogForm) base;
        String kind = mapping.getPath().substring(1);
        request.setAttribute("kind", kind);
        boolean product = "products".equals(kind);
        request.setAttribute("canMaintain", Boolean.valueOf(actor(request).hasRole("ADMIN")
                || actor(request).hasRole("MANAGER") || (product && actor(request).hasRole("BATCH"))));
        CatalogService service = catalog(request);
        if ("list".equals(form.getOp())) {
            request.setAttribute("statuses", "customers".equals(kind)
                    ? new String[] {"ACTIVE", "INACTIVE", "HOLD"} : new String[] {"ACTIVE", "INACTIVE"});
            Search search = Inputs.search(form);
            if ("customers".equals(kind)) {
                request.setAttribute("results", service.searchCustomers(search, actor(request)));
            } else if (product) {
                request.setAttribute("results", service.searchProducts(search, actor(request)));
            } else {
                request.setAttribute("results", service.searchWarehouses(search, actor(request)));
            }
            return view(request, "catalog/list", title(kind) + "一覧");
        }
        if ("save".equals(form.getOp())) {
            requireMaintenance(kind, request);
            if (!validate(mapping, form, request)) {
                response.setStatus(422);
                return view(request, "catalog/edit", title(kind) + "編集");
            }
            BaseEntity saved;
            if ("customers".equals(kind)) {
                saved = service.saveCustomer(customer(form), version(form), actor(request));
            } else if (product) {
                saved = service.saveProduct(product(form), version(form), actor(request));
            } else {
                saved = service.saveWarehouse(warehouse(form), version(form), actor(request));
            }
            return redirect(request, response, "/" + kind + ".do", saved.getId(), title(kind) + "を保存しました。");
        }
        if ("new".equals(form.getOp())) {
            requireMaintenance(kind, request);
            return view(request, "catalog/edit", title(kind) + "新規登録");
        }
        BaseEntity entity;
        if ("customers".equals(kind)) {
            Customer customer = service.getCustomer(id(form), actor(request));
            entity = customer;
            populate(form, customer);
            request.setAttribute("agreements", service.listPriceAgreements(customer.getId(), actor(request)));
        } else if (product) {
            Product value = service.getProduct(id(form), actor(request));
            entity = value;
            populate(form, value);
        } else {
            Warehouse value = service.getWarehouse(id(form), actor(request));
            entity = value;
            form.setCode(value.getCode()); form.setName(value.getName());
            form.setActive(String.valueOf(value.isActive())); form.setAddress(value.getAddress());
        }
        identity(form, entity);
        request.setAttribute("record", entity);
        if ("edit".equals(form.getOp())) { requireMaintenance(kind, request); }
        return view(request, "catalog/" + ("edit".equals(form.getOp()) ? "edit" : "detail"), title(kind));
    }

    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        if ("list".equals(form.getOp())) {
            return view(request, "catalog/list", title((String) request.getAttribute("kind")) + "検索条件の確認");
        }
        if ("save".equals(form.getOp())) {
            return view(request, "catalog/edit", title((String) request.getAttribute("kind")) + "編集");
        }
        return view(request, "error", "検索条件の確認");
    }

    private void requireMaintenance(String kind, HttpServletRequest request) {
        if ("products".equals(kind)) { actor(request).require("MANAGER", "BATCH"); }
        else { actor(request).require("MANAGER"); }
    }
    private String title(String kind) {
        return "customers".equals(kind) ? "得意先" : "products".equals(kind) ? "商品" : "倉庫";
    }

    private Customer customer(CatalogForm form) {
        Customer value = new Customer();
        value.setId(Inputs.optionalId(form.getId(), "得意先ID"));
        value.setCode(Inputs.text(form.getCode(), "コード", 30, true));
        value.setName(Inputs.text(form.getName(), "名称", 100, true));
        value.setActive(Inputs.bool(form.getActive(), "利用状態"));
        value.setOnHold(Inputs.bool(form.getOnHold(), "取引保留"));
        value.setCreditLimit(Inputs.money(form.getCreditLimit(), "与信限度額", false));
        value.setClosingDay(Integer.parseInt(Inputs.choice(form.getClosingDay(), "締日", "10", "20", "31")));
        value.setPaymentTermDays(Inputs.integer(form.getPaymentTermDays(), "支払サイト", 0, 365));
        value.setTaxRounding(Inputs.choice(form.getTaxRounding(), "税端数処理", "DOWN", "UP", "HALF_UP"));
        value.setPostalCode(Inputs.text(form.getPostalCode(), "郵便番号", 12, false));
        value.setAddress(Inputs.text(form.getAddress(), "住所", 250, false));
        value.setTelephone(Inputs.text(form.getTelephone(), "電話番号", 30, false));
        value.setNotes(Inputs.text(form.getNotes(), "備考", 1000, false));
        return value;
    }
    private Product product(CatalogForm form) {
        Product value = new Product();
        value.setId(Inputs.optionalId(form.getId(), "商品ID"));
        value.setCode(Inputs.text(form.getCode(), "コード", 30, true));
        value.setName(Inputs.text(form.getName(), "名称", 100, true));
        value.setActive(Inputs.bool(form.getActive(), "利用状態"));
        value.setUnit(Inputs.text(form.getUnit(), "単位", 20, true));
        value.setTaxCategory(Inputs.choice(form.getTaxCategory(), "税区分", "STANDARD", "REDUCED", "EXEMPT"));
        value.setListPrice(Inputs.money(form.getListPrice(), "標準売価", false));
        value.setStandardCost(Inputs.money(form.getStandardCost(), "標準原価", false));
        value.setPackSize(Inputs.quantity(form.getPackSize(), "入数", false));
        value.setReorderPoint(Inputs.quantity(form.getReorderPoint(), "発注点", true));
        value.setReorderQuantity(Inputs.quantity(form.getReorderQuantity(), "標準補充数", true));
        value.setNotes(Inputs.text(form.getNotes(), "備考", 1000, false));
        return value;
    }
    private Warehouse warehouse(CatalogForm form) {
        Warehouse value = new Warehouse();
        value.setId(Inputs.optionalId(form.getId(), "倉庫ID"));
        value.setCode(Inputs.text(form.getCode(), "コード", 30, true));
        value.setName(Inputs.text(form.getName(), "名称", 100, true));
        value.setActive(Inputs.bool(form.getActive(), "利用状態"));
        value.setAddress(Inputs.text(form.getAddress(), "住所", 250, false));
        return value;
    }
    private void populate(CatalogForm form, Customer value) {
        form.setCode(value.getCode()); form.setName(value.getName());
        form.setActive(String.valueOf(value.isActive())); form.setOnHold(String.valueOf(value.isOnHold()));
        form.setCreditLimit(value.getCreditLimit().toPlainString()); form.setClosingDay(String.valueOf(value.getClosingDay()));
        form.setPaymentTermDays(String.valueOf(value.getPaymentTermDays())); form.setTaxRounding(value.getTaxRounding());
        form.setPostalCode(value.getPostalCode()); form.setAddress(value.getAddress());
        form.setTelephone(value.getTelephone()); form.setNotes(value.getNotes());
    }
    private void populate(CatalogForm form, Product value) {
        form.setCode(value.getCode()); form.setName(value.getName()); form.setActive(String.valueOf(value.isActive()));
        form.setUnit(value.getUnit()); form.setTaxCategory(value.getTaxCategory());
        form.setListPrice(value.getListPrice().toPlainString()); form.setStandardCost(value.getStandardCost().toPlainString());
        form.setPackSize(String.valueOf(value.getPackSize())); form.setReorderPoint(String.valueOf(value.getReorderPoint()));
        form.setReorderQuantity(String.valueOf(value.getReorderQuantity())); form.setNotes(value.getNotes());
    }
}
