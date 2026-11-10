package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.PurchasingReceiptCommand;
import jp.co.tsubame.wholesale.common.PurchasingReceiptLineCommand;
import jp.co.tsubame.wholesale.entity.PurchaseOrder;
import jp.co.tsubame.wholesale.entity.PurchaseReceipt;
import jp.co.tsubame.wholesale.service.PurchasingService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.Web;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.PurchaseReceiptForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class PurchaseReceiptAction extends BaseAction {
    protected boolean isRead(String op) { return "new".equals(op) || "detail".equals(op); }
    protected boolean isMutation(String op) { return "receive".equals(op); }
    private PurchasingService purchasing(HttpServletRequest request) { return service(request, "purchasingService", PurchasingService.class); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        PurchaseReceiptForm form = (PurchaseReceiptForm) base;
        if (isMutation(form.getOp())) { actor(request).require("WAREHOUSE", "MANAGER", "BATCH"); }
        if ("detail".equals(form.getOp())) {
            request.setAttribute("receipt", purchasing(request).getReceipt(id(form), actor(request)));
            return view(request, "purchasing/receipt-detail", "仕入入荷・検品詳細");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("WAREHOUSE", "MANAGER", "BATCH");
            PurchaseOrder order = source(form, request);
            form.setVersion(String.valueOf(order.getVersion())); form.setRequestKey(Web.randomToken());
            form.setReceiptDate(Dates.format(Dates.today()));
            int size = order.getLines().size();
            String[] ids = new String[size], accepted = new String[size], rejected = new String[size],
                    reasons = new String[size], notes = new String[size];
            for (int i = 0; i < size; i++) {
                ids[i] = String.valueOf(order.getLines().get(i).getId()); accepted[i] = "0";
                rejected[i] = "0"; reasons[i] = ""; notes[i] = "";
            }
            form.setLineId(ids); form.setAcceptedQuantity(accepted); form.setRejectedQuantity(rejected);
            form.setRejectionReason(reasons); form.setLineNote(notes);
            return view(request, "purchasing/receipt-edit", "入荷・検品の登録");
        }
        PurchasingReceiptCommand command = new PurchasingReceiptCommand();
        command.setRequestKey(Inputs.text(form.getRequestKey(), "受付キー", 100, true));
        command.setOrderId(Inputs.id(form.getOrderId(), "仕入発注")); command.setExpectedVersion(version(form));
        command.setReceiptDate(Inputs.date(form.getReceiptDate(), "入荷日", false));
        command.setSupplierDeliveryNumber(Inputs.text(form.getSupplierDeliveryNumber(), "仕入先納品番号", 100, false));
        command.setNotes(Inputs.text(form.getNotes(), "検品備考", 1000, false));
        Inputs.arrays(form.getLineId(), form.getAcceptedQuantity(), form.getRejectedQuantity(), form.getRejectionReason(), form.getLineNote());
        Inputs.uniqueIds(form.getLineId(), "発注明細");
        for (int i = 0; i < form.getLineId().length; i++) {
            int accepted = optionalQuantity(form.getAcceptedQuantity()[i], "良品数量");
            int rejected = optionalQuantity(form.getRejectedQuantity()[i], "不良数量");
            if (accepted == 0 && rejected == 0) { continue; }
            PurchasingReceiptLineCommand line = new PurchasingReceiptLineCommand();
            line.setOrderLineId(Inputs.id(form.getLineId()[i], "発注明細"));
            line.setAcceptedQuantity(accepted); line.setRejectedQuantity(rejected);
            line.setRejectionReason(Inputs.text(form.getRejectionReason()[i], "不良理由", 500, rejected > 0));
            line.setNotes(Inputs.text(form.getLineNote()[i], "明細備考", 500, false)); command.getLines().add(line);
        }
        if (command.getLines().isEmpty()) { throw Inputs.invalid("検品数量", "良品または不良数量を1行以上入力してください。"); }
        PurchaseReceipt receipt = purchasing(request).receive(command, actor(request));
        return redirect(request, response, "/purchaseReceipts.do", receipt.getId(), "入荷・検品を登録し、良品を入庫しました。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        source((PurchaseReceiptForm) base, request);
        return view(request, "purchasing/receipt-edit", "検品内容の確認");
    }
    private PurchaseOrder source(PurchaseReceiptForm form, HttpServletRequest request) {
        PurchaseOrder order = purchasing(request).getOrder(Inputs.id(form.getOrderId(), "仕入発注"), actor(request));
        request.setAttribute("purchase", order); return order;
    }
    private int optionalQuantity(String value, String field) {
        return value == null || value.length() == 0 ? 0 : Inputs.quantity(value, field, true);
    }
}
