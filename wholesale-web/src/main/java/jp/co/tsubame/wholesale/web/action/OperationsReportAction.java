package jp.co.tsubame.wholesale.web.action;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.ReportFilter;
import jp.co.tsubame.wholesale.common.SalesSummaryRow;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.service.OperationsReportService;
import jp.co.tsubame.wholesale.service.PurchasingService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.OperationsReportForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class OperationsReportAction extends BaseAction {
    protected boolean isRead(String op) {
        return "list".equals(op) || "sales".equals(op) || "backlog".equals(op)
                || "supplierQuality".equals(op) || "stockActivity".equals(op);
    }
    protected boolean isMutation(String op) { return false; }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) {
        OperationsReportForm form = (OperationsReportForm) base;
        Actor actor = actor(request);
        String mode = form.getOp();
        if ("list".equals(mode)) {
            mode = actor.hasRole("ADMIN") || actor.hasRole("SALES") || actor.hasRole("MANAGER") || actor.hasRole("BILLING")
                    ? "sales" : "backlog";
            form.setOp(mode);
        }
        authorize(mode, actor);
        if (form.getFrom().length() == 0) { form.setFrom(Dates.format(Dates.addDays(Dates.today(), -30))); }
        if (form.getTo().length() == 0) { form.setTo(Dates.format(Dates.today())); }
        request.setAttribute("reportMode", mode);
        request.setAttribute("latestReportDate", Dates.format("backlog".equals(mode) ? Dates.addDays(Dates.today(), 366) : Dates.today()));
        request.setAttribute("warehouses", catalog(request).listActiveWarehouses(actor));
        request.setAttribute("products", catalog(request).listActiveProducts(actor));
        if ("sales".equals(mode) || "backlog".equals(mode)) {
            request.setAttribute("customers", catalog(request).listActiveCustomers(actor));
        }
        if ("supplierQuality".equals(mode)) {
            request.setAttribute("suppliers", service(request, "purchasingService", PurchasingService.class).listActiveSuppliers(actor));
        }
        Map<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("dimension", form.getDimension()); extra.put("productId", form.getProductId());
        extra.put("supplierId", form.getSupplierId()); extra.put("exceptionsOnly", form.getExceptionsOnly());
        request.setAttribute("extraPageParameters", extra);
        ReportFilter filter = filter(form, mode);
        OperationsReportService reports = service(request, "operationsReportService", OperationsReportService.class);
        if ("sales".equals(mode)) {
            String dimension = Inputs.choice(form.getDimension(), "集計軸", "CUSTOMER", "PRODUCT", "WAREHOUSE");
            Page<SalesSummaryRow> results = reports.searchSales(dimension, filter, actor);
            request.setAttribute("results", results);
            BigDecimal total = BigDecimal.ZERO;
            for (SalesSummaryRow row : results.getItems()) { total = total.add(row.getNetAmount()); }
            request.setAttribute("pageNetAmount", total);
            request.setAttribute("dimensionRoute", "CUSTOMER".equals(dimension) ? "/customers.do"
                    : "PRODUCT".equals(dimension) ? "/products.do" : "/warehouses.do");
        } else if ("backlog".equals(mode)) {
            request.setAttribute("results", reports.searchBacklog(filter, actor));
        } else if ("supplierQuality".equals(mode)) {
            request.setAttribute("results", reports.searchSupplierQuality(filter, actor));
        } else {
            request.setAttribute("results", reports.searchStockActivity(filter, actor));
        }
        request.setAttribute("reportGeneratedAt", new Date());
        return reportView(mode, request);
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        String mode = (String) request.getAttribute("reportMode");
        return mode == null ? view(request, "error", "業務集計条件の確認") : reportView(mode, request);
    }
    static ReportFilter filter(OperationsReportForm form, String mode) {
        Search base = Inputs.search(form);
        ReportFilter filter = new ReportFilter();
        filter.setText(base.getText()); filter.setFrom(base.getFrom()); filter.setTo(base.getTo());
        filter.setCustomerId(base.getCustomerId()); filter.setWarehouseId(base.getWarehouseId());
        filter.setPage(base.getPage()); filter.setSize(base.getSize()); filter.setStatus(base.getStatus());
        filter.setProductId(Inputs.optionalId(form.getProductId(), "商品"));
        filter.setSupplierId(Inputs.optionalId(form.getSupplierId(), "仕入先"));
        filter.setExceptionsOnly(Inputs.bool(form.getExceptionsOnly(), "例外のみ"));
        if (!"stockActivity".equals(mode) && base.getStatus().length() > 0) {
            throw Inputs.invalid("移動区分", "移動区分は在庫活動レポートのみ指定できます。");
        }
        if (!"supplierQuality".equals(mode) && filter.getSupplierId() != null) {
            throw Inputs.invalid("仕入先", "仕入先条件は仕入品質レポートのみ指定できます。");
        }
        if (!"sales".equals(mode) && !"backlog".equals(mode) && filter.getCustomerId() != null) {
            throw Inputs.invalid("得意先", "得意先条件は売上・受注残レポートのみ指定できます。");
        }
        if ("stockActivity".equals(mode) && filter.isExceptionsOnly()) {
            throw Inputs.invalid("例外のみ", "在庫活動レポートには例外抽出条件はありません。");
        }
        if ("backlog".equals(mode)) { filter.validateBacklogPeriod(); }
        else { filter.validatePeriod(); }
        return filter;
    }
    private void authorize(String mode, Actor actor) {
        if ("sales".equals(mode)) { actor.require("SALES", "MANAGER", "BILLING"); }
        else if ("backlog".equals(mode)) { actor.require("SALES", "WAREHOUSE", "MANAGER", "BATCH"); }
        else if ("supplierQuality".equals(mode)) { actor.require("WAREHOUSE", "MANAGER", "BILLING"); }
        else { actor.require("WAREHOUSE", "MANAGER", "BILLING", "BATCH"); }
    }
    private ActionForward reportView(String mode, HttpServletRequest request) {
        if ("sales".equals(mode)) { return view(request, "reports/sales", "出荷・返品による売上集計"); }
        if ("backlog".equals(mode)) { return view(request, "reports/backlog", "現在の受注残・納期例外"); }
        if ("supplierQuality".equals(mode)) { return view(request, "reports/quality", "仕入先別の検収品質・入荷遅延"); }
        return view(request, "reports/activity", "台帳記録日時による在庫活動");
    }
}
