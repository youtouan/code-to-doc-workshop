package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.StockReceipt;
import jp.co.tsubame.wholesale.service.InventoryService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.Web;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.ReceiptForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class ReceiptAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "detail".equals(op) || "new".equals(op); }
    protected boolean isMutation(String op) { return "receive".equals(op); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        ReceiptForm form = (ReceiptForm) base;
        InventoryService service = service(request, "inventoryService", InventoryService.class);
        if ("receive".equals(form.getOp())) {
            actor(request).require("WAREHOUSE", "MANAGER", "BATCH");
            StockReceipt saved = service.receive(Inputs.text(form.getRequestKey(), "受付キー", 80, true),
                    Inputs.id(form.getWarehouseId(), "入庫倉庫"), Inputs.id(form.getProductId(), "商品"),
                    Inputs.quantity(form.getQuantity(), "数量", false), Inputs.money(form.getUnitCost(), "入庫単価", false),
                    Inputs.date(form.getReceiptDate(), "入庫日", false), Inputs.text(form.getReference(), "参照番号", 100, false),
                    Inputs.text(form.getNote(), "備考", 1000, false), actor(request));
            return redirect(request, response, "/receipts.do", saved.getId(), "入庫を登録しました。");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("WAREHOUSE", "MANAGER", "BATCH");
            references(request);
            form.setRequestKey(Web.randomToken());
            form.setReceiptDate(Dates.format(Dates.today()));
            return view(request, "inventory/receipt-edit", "直接入庫の登録");
        }
        if ("detail".equals(form.getOp())) {
            request.setAttribute("receipt", service.getReceipt(id(form), actor(request)));
            return view(request, "inventory/receipt-detail", "入庫伝票詳細");
        }
        references(request);
        request.setAttribute("searchWarehouse", Boolean.TRUE);
        request.setAttribute("searchDates", Boolean.TRUE);
        request.setAttribute("results", service.searchReceipts(Inputs.search(form), actor(request)));
        return view(request, "inventory/receipts", "直接入庫一覧");
    }
    protected ActionForward failure(BaseForm form, HttpServletRequest request) {
        references(request);
        if ("list".equals(form.getOp())) { return view(request, "inventory/receipts", "入庫検索条件の確認"); }
        if (!"receive".equals(form.getOp())) { return view(request, "error", "入庫の確認"); }
        return view(request, "inventory/receipt-edit", "入庫内容の確認");
    }
}
