package jp.co.tsubame.wholesale.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.entity.Product;
import jp.co.tsubame.wholesale.entity.PurchaseOrder;
import jp.co.tsubame.wholesale.entity.PurchaseOrderLine;
import jp.co.tsubame.wholesale.entity.Supplier;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.PurchasingService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.PurchaseForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class PurchaseAction extends BaseAction {
    protected boolean isMutation(String op) {
        return "save".equals(op) || "addLine".equals(op) || "submit".equals(op) || "approve".equals(op)
                || "reject".equals(op) || "cancel".equals(op) || "close".equals(op);
    }
    private PurchasingService purchasing(HttpServletRequest request) { return service(request, "purchasingService", PurchasingService.class); }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        PurchaseForm form = (PurchaseForm) base;
        if ("approve".equals(form.getOp()) || "reject".equals(form.getOp()) || "close".equals(form.getOp())) {
            actor(request).require("MANAGER");
        } else if (isMutation(form.getOp())) { actor(request).require("WAREHOUSE", "MANAGER"); }
        PurchasingService service = purchasing(request);
        if ("list".equals(form.getOp())) {
            references(request); request.setAttribute("searchWarehouse", Boolean.TRUE); request.setAttribute("searchDates", Boolean.TRUE);
            request.setAttribute("statuses", new String[] {"DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "PART_RECEIVED", "RECEIVED", "CLOSED", "CANCELLED"});
            request.setAttribute("results", service.searchOrders(Inputs.search(form), actor(request)));
            return view(request, "purchasing/orders", "仕入発注一覧");
        }
        if ("new".equals(form.getOp()) || "edit".equals(form.getOp())) {
            actor(request).require("WAREHOUSE", "MANAGER");
            if ("edit".equals(form.getOp())) {
                PurchaseOrder order = service.getOrder(id(form), actor(request));
                if (!order.isEditable()) { throw Inputs.invalid("状態", "編集可能な発注ではありません。"); }
                identity(form, order);
                form.setSupplierId(String.valueOf(order.getSupplier().getId()));
                form.setWarehouseId(String.valueOf(order.getWarehouse().getId()));
                form.setOrderDate(Dates.format(order.getOrderDate())); form.setExpectedDate(Dates.format(order.getExpectedDate()));
                form.setNote(order.getNotes()); form.rows(Math.max(5, order.getLines().size()));
                for (int i = 0; i < order.getLines().size(); i++) {
                    PurchaseOrderLine line = order.getLines().get(i);
                    form.getProductId()[i] = String.valueOf(line.getProduct().getId()); form.getQuantity()[i] = String.valueOf(line.getQuantity());
                    form.getLineExpectedDate()[i] = Dates.format(line.getExpectedDate()); form.getLineNote()[i] = line.getNotes();
                }
            } else {
                form.setOrderDate(Dates.format(Dates.today()));
                if (form.getExpectedDate().length() == 0) { form.setExpectedDate(Dates.format(Dates.addDays(Dates.today(), 7))); }
                form.rows(Math.max(5, form.getProductId().length));
            }
            return edit(request);
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        if ("addLine".equals(form.getOp())) {
            actor(request).require("WAREHOUSE", "MANAGER");
            Inputs.arrays(form.getProductId(), form.getQuantity(), form.getLineExpectedDate(), form.getLineNote());
            form.rows(Math.min(100, form.getProductId().length + 5)); return edit(request);
        }
        PurchaseOrder result;
        if ("save".equals(form.getOp())) { result = service.saveOrder(input(form), version(form), actor(request)); }
        else if ("submit".equals(form.getOp())) { result = service.submitOrder(id(form), version(form), actor(request)); }
        else if ("approve".equals(form.getOp())) { result = service.approveOrder(id(form), version(form), actor(request)); }
        else if ("reject".equals(form.getOp())) { result = service.rejectOrder(id(form), version(form), reason(form), actor(request)); }
        else if ("cancel".equals(form.getOp())) { result = service.cancelOrder(id(form), version(form), reason(form), actor(request)); }
        else { result = service.closeOutstanding(id(form), version(form), reason(form), actor(request)); }
        return redirect(request, response, "/purchases.do", result.getId(), "仕入発注処理を実行しました。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        PurchaseForm form = (PurchaseForm) base;
        if ("list".equals(form.getOp())) { return view(request, "purchasing/orders", "仕入発注検索条件の確認"); }
        if ("save".equals(form.getOp()) || "addLine".equals(form.getOp())) { return edit(request); }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "仕入発注条件の確認");
    }
    private ActionForward edit(HttpServletRequest request) {
        references(request); request.setAttribute("suppliers", purchasing(request).listActiveSuppliers(actor(request)));
        return view(request, "purchasing/order-edit", "仕入発注下書き");
    }
    private PurchaseOrder input(PurchaseForm form) {
        PurchaseOrder order = new PurchaseOrder(); order.setId(Inputs.optionalId(form.getId(), "発注ID"));
        Supplier supplier = new Supplier(); supplier.setId(Inputs.id(form.getSupplierId(), "仕入先")); order.setSupplier(supplier);
        Warehouse warehouse = new Warehouse(); warehouse.setId(Inputs.id(form.getWarehouseId(), "入荷倉庫")); order.setWarehouse(warehouse);
        order.setOrderDate(Inputs.date(form.getOrderDate(), "発注日", false)); order.setExpectedDate(Inputs.date(form.getExpectedDate(), "入荷予定日", false));
        order.setNotes(Inputs.text(form.getNote(), "備考", 1000, false));
        Inputs.arrays(form.getProductId(), form.getQuantity(), form.getLineExpectedDate(), form.getLineNote());
        for (int i = 0; i < form.getProductId().length; i++) {
            if ("".equals(form.getProductId()[i]) && "".equals(form.getQuantity()[i])
                    && "".equals(form.getLineExpectedDate()[i]) && "".equals(form.getLineNote()[i])) { continue; }
            PurchaseOrderLine line = new PurchaseOrderLine();
            Product product = new Product(); product.setId(Inputs.id(form.getProductId()[i], "商品")); line.setProduct(product);
            line.setQuantity(Inputs.quantity(form.getQuantity()[i], "発注数量", false));
            line.setExpectedDate(Inputs.date(form.getLineExpectedDate()[i], "明細入荷予定日", true));
            line.setNotes(Inputs.text(form.getLineNote()[i], "明細備考", 500, false)); order.getLines().add(line);
        }
        if (order.getLines().isEmpty()) { throw Inputs.invalid("明細", "発注明細を1行以上入力してください。"); }
        return order;
    }
    private ActionForward detail(PurchaseForm form, HttpServletRequest request, boolean refresh) {
        PurchaseOrder order = purchasing(request).getOrder(id(form), actor(request));
        request.setAttribute("purchase", order); request.setAttribute("receipts", purchasing(request).listReceipts(order.getId(), actor(request)));
        if (refresh) { identity(form, order); }
        return view(request, "purchasing/order-detail", "仕入発注詳細 · " + order.getNumber());
    }
}
