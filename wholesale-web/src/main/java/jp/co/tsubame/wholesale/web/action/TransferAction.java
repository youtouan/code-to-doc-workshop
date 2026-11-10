package jp.co.tsubame.wholesale.web.action;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.StockControlLineCommand;
import jp.co.tsubame.wholesale.common.StockTransferCommand;
import jp.co.tsubame.wholesale.common.StockTransferReceiptCommand;
import jp.co.tsubame.wholesale.entity.StockTransfer;
import jp.co.tsubame.wholesale.entity.StockTransferLine;
import jp.co.tsubame.wholesale.service.StockTransferService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.Web;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.TransferForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class TransferAction extends BaseAction {
    protected boolean isMutation(String op) {
        return "save".equals(op) || "addLine".equals(op) || "submit".equals(op) || "approve".equals(op)
                || "dispatch".equals(op) || "receive".equals(op) || "loss".equals(op) || "cancel".equals(op);
    }
    private StockTransferService transfers(HttpServletRequest request) {
        return service(request, "stockTransferService", StockTransferService.class);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        TransferForm form = (TransferForm) base;
        if ("approve".equals(form.getOp()) || "loss".equals(form.getOp())) { actor(request).require("MANAGER"); }
        else if ("dispatch".equals(form.getOp()) || "receive".equals(form.getOp())) { actor(request).require("WAREHOUSE", "BATCH"); }
        else if (isMutation(form.getOp())) { actor(request).require("WAREHOUSE", "MANAGER"); }
        StockTransferService service = transfers(request);
        if ("list".equals(form.getOp())) {
            references(request); request.setAttribute("searchWarehouse", Boolean.TRUE); request.setAttribute("searchDates", Boolean.TRUE);
            request.setAttribute("statuses", new String[] {"DRAFT", "SUBMITTED", "APPROVED", "IN_TRANSIT", "PART_RECEIVED", "COMPLETED", "RECONCILED", "CANCELLED"});
            request.setAttribute("results", service.search(actor(request), Inputs.search(form)));
            return view(request, "control/transfers", "倉庫間移送一覧");
        }
        if ("new".equals(form.getOp()) || "edit".equals(form.getOp())) {
            actor(request).require("WAREHOUSE", "MANAGER"); references(request);
            if ("edit".equals(form.getOp())) {
                StockTransfer transfer = service.get(actor(request), id(form));
                if (!"DRAFT".equals(transfer.getStatus())) { throw Inputs.invalid("状態", "下書き以外は編集できません。"); }
                identity(form, transfer);
                form.setSourceWarehouseId(String.valueOf(transfer.getSourceWarehouse().getId()));
                form.setDestinationWarehouseId(String.valueOf(transfer.getDestinationWarehouse().getId()));
                form.setNote(transfer.getNote()); form.rows(Math.max(5, transfer.getLines().size()));
                for (int i = 0; i < transfer.getLines().size(); i++) {
                    StockTransferLine line = transfer.getLines().get(i);
                    form.getProductId()[i] = String.valueOf(line.getProduct().getId());
                    form.getQuantity()[i] = String.valueOf(line.getQuantity());
                }
            } else { form.rows(5); }
            return view(request, "control/transfer-edit", "倉庫間移送下書き");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        if ("addLine".equals(form.getOp())) {
            actor(request).require("WAREHOUSE", "MANAGER");
            Inputs.arrays(form.getProductId(), form.getQuantity());
            form.rows(Math.min(100, form.getProductId().length + 5)); references(request);
            return view(request, "control/transfer-edit", "倉庫間移送下書き");
        }
        StockTransfer result;
        if ("save".equals(form.getOp())) {
            StockTransferCommand command = new StockTransferCommand();
            command.setId(Inputs.optionalId(form.getId(), "移送ID")); command.setExpectedVersion(version(form));
            command.setSourceWarehouseId(Inputs.id(form.getSourceWarehouseId(), "移送元倉庫"));
            command.setDestinationWarehouseId(Inputs.id(form.getDestinationWarehouseId(), "移送先倉庫"));
            command.setNote(Inputs.text(form.getNote(), "備考", 500, false));
            command.setLines(lines(form, false)); result = service.saveDraft(actor(request), command);
        } else if ("submit".equals(form.getOp())) { result = service.submit(actor(request), id(form), version(form)); }
        else if ("approve".equals(form.getOp())) { result = service.approve(actor(request), id(form), version(form)); }
        else if ("dispatch".equals(form.getOp())) { result = service.dispatch(actor(request), id(form), version(form)); }
        else if ("cancel".equals(form.getOp())) { result = service.cancel(actor(request), id(form), version(form), reason(form)); }
        else {
            StockTransferReceiptCommand command = new StockTransferReceiptCommand();
            command.setRequestKey(Inputs.text(form.getRequestKey(), "受付キー", 120, true));
            command.setNote(Inputs.text(form.getNote(), "受領備考・損失理由", 500, "loss".equals(form.getOp())));
            command.setLines(lines(form, true));
            result = "receive".equals(form.getOp()) ? service.receive(actor(request), id(form), version(form), command)
                    : service.reconcileLoss(actor(request), id(form), version(form), command);
        }
        return redirect(request, response, "/transfers.do", result.getId(), "倉庫間移送処理を実行しました。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        TransferForm form = (TransferForm) base;
        if ("list".equals(form.getOp())) { return view(request, "control/transfers", "移送検索条件の確認"); }
        if ("save".equals(form.getOp()) || "addLine".equals(form.getOp())) {
            references(request); return view(request, "control/transfer-edit", "移送内容の確認");
        }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "移送条件の確認");
    }
    private List<StockControlLineCommand> lines(TransferForm form, boolean optional) {
        Inputs.arrays(form.getProductId(), form.getQuantity());
        List<StockControlLineCommand> lines = new ArrayList<StockControlLineCommand>();
        for (int i = 0; i < form.getProductId().length; i++) {
            String product = form.getProductId()[i], value = form.getQuantity()[i];
            if ("".equals(product) && "".equals(value)) { continue; }
            Long productId = Inputs.id(product, "商品");
            int quantity = optional && (value == null || value.length() == 0) ? 0 : Inputs.quantity(value, "数量", optional);
            if (quantity == 0) { continue; }
            StockControlLineCommand line = new StockControlLineCommand();
            line.setProductId(productId); line.setQuantity(quantity); lines.add(line);
        }
        if (lines.isEmpty()) { throw Inputs.invalid("明細", "処理数量を1行以上指定してください。"); }
        return lines;
    }
    private ActionForward detail(TransferForm form, HttpServletRequest request, boolean refresh) {
        StockTransfer transfer = transfers(request).get(actor(request), id(form));
        request.setAttribute("transfer", transfer);
        request.setAttribute("results", transfers(request).listReceipts(actor(request), transfer.getId(), Inputs.search(form)));
        if (refresh) {
            identity(form, transfer); form.setRequestKey(Web.randomToken()); form.setNote("");
            form.rows(Math.max(1, transfer.getLines().size()));
            for (int i = 0; i < transfer.getLines().size(); i++) {
                StockTransferLine line = transfer.getLines().get(i);
                form.getProductId()[i] = String.valueOf(line.getProduct().getId()); form.getQuantity()[i] = "0";
            }
        }
        return view(request, "control/transfer-detail", "移送詳細 · " + transfer.getNumber());
    }
}
