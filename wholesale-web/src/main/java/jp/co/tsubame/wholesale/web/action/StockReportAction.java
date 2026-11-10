package jp.co.tsubame.wholesale.web.action;

import java.math.BigDecimal;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.StockReconciliationRow;
import jp.co.tsubame.wholesale.common.StockValuationRow;
import jp.co.tsubame.wholesale.service.StockControlReportService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class StockReportAction extends BaseAction {
    protected boolean isRead(String op) {
        return "list".equals(op) || "reconciliation".equals(op) || "valuation".equals(op);
    }
    protected boolean isMutation(String op) { return false; }
    protected ActionForward perform(ActionMapping mapping, BaseForm form, HttpServletRequest request,
                                    HttpServletResponse response) {
        actor(request).require("SALES", "WAREHOUSE", "MANAGER", "BILLING", "BATCH");
        request.setAttribute("warehouses", catalog(request).listActiveWarehouses(actor(request)));
        request.setAttribute("valuation", Boolean.valueOf("valuation".equals(form.getOp())));
        StockControlReportService service = service(request, "stockControlReportService", StockControlReportService.class);
        if ("valuation".equals(form.getOp())) {
            Page<StockValuationRow> results = service.listValuation(actor(request), Inputs.search(form));
            request.setAttribute("results", results);
            BigDecimal physical = BigDecimal.ZERO, outgoing = BigDecimal.ZERO, attributed = BigDecimal.ZERO;
            for (StockValuationRow row : results.getItems()) {
                physical = physical.add(row.getOnHandValue());
                outgoing = outgoing.add(row.getOutgoingTransitValue());
                attributed = attributed.add(row.getAttributedInventoryValue());
            }
            request.setAttribute("pagePhysicalValue", physical);
            request.setAttribute("pageOutgoingValue", outgoing);
            request.setAttribute("pageAttributedValue", attributed);
        } else {
            Page<StockReconciliationRow> results = service.listReconciliation(actor(request), Inputs.search(form));
            request.setAttribute("results", results);
            int mismatches = 0;
            for (StockReconciliationRow row : results.getItems()) { if (!row.isConsistent()) { mismatches++; } }
            request.setAttribute("pageMismatches", Integer.valueOf(mismatches));
        }
        request.setAttribute("reportAsOf", new Date());
        return view(request, "control/reports", "valuation".equals(form.getOp()) ? "在庫評価・移送中評価" : "在庫台帳の整合性照会");
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        return view(request, "control/reports", "在庫レポート条件の確認");
    }
}
