package jp.co.tsubame.wholesale.web.action;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import jp.co.tsubame.wholesale.service.APInvoiceService;
import jp.co.tsubame.wholesale.service.APReportService;
import jp.co.tsubame.wholesale.service.PurchasingService;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.APForm;

public abstract class APAction extends BaseAction {
    protected APInvoiceService invoices(HttpServletRequest request) {
        return service(request, "apInvoiceService", APInvoiceService.class);
    }
    protected APReportService reports(HttpServletRequest request) {
        return service(request, "apReportService", APReportService.class);
    }
    protected void apReferences(APForm form, HttpServletRequest request) {
        actor(request).require("BILLING", "MANAGER");
        PurchasingService purchasing = service(request, "purchasingService", PurchasingService.class);
        List<Supplier> suppliers = new ArrayList<Supplier>(purchasing.listActiveSuppliers(actor(request)));
        request.setAttribute("suppliers", suppliers);
        Long selected = Inputs.optionalId(form.getSupplierId(), "仕入先");
        if (selected != null) {
            Supplier found = null;
            for (Supplier supplier : suppliers) { if (selected.equals(supplier.getId())) { found = supplier; break; } }
            if (found == null) { found = purchasing.getSupplier(selected, actor(request)); suppliers.add(found); }
            request.setAttribute("selectedSupplier", found);
        }
        Map<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("supplierId", form.getSupplierId()); extra.put("asOf", form.getAsOf()); extra.put("overdueOnly", form.getOverdueOnly());
        request.setAttribute("extraPageParameters", extra);
    }
}
