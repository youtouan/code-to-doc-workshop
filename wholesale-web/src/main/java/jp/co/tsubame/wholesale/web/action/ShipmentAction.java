package jp.co.tsubame.wholesale.web.action;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.ShipmentLineInput;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.SalesOrderLine;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.ShipmentLine;
import jp.co.tsubame.wholesale.service.OrderService;
import jp.co.tsubame.wholesale.service.ShippingService;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.ShipmentForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class ShipmentAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "detail".equals(op) || "new".equals(op); }
    protected boolean isMutation(String op) { return "instruct".equals(op) || "confirm".equals(op) || "cancel".equals(op); }
    private ShippingService shipping(HttpServletRequest request) {
        return service(request, "shippingService", ShippingService.class);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        ShipmentForm form = (ShipmentForm) base;
        ShippingService service = shipping(request);
        if ("confirm".equals(form.getOp())) { actor(request).require("WAREHOUSE", "BATCH"); }
        else if (isMutation(form.getOp())) { actor(request).require("WAREHOUSE", "MANAGER"); }
        if ("list".equals(form.getOp())) {
            references(request);
            request.setAttribute("statuses", new String[] {"INSTRUCTED", "CONFIRMED", "CANCELLED"});
            request.setAttribute("searchCustomer", Boolean.TRUE); request.setAttribute("searchDates", Boolean.TRUE);
            request.setAttribute("searchWarehouse", Boolean.TRUE);
            request.setAttribute("results", service.searchShipments(Inputs.search(form), actor(request)));
            return view(request, "shipping/list", "出荷指示・実績一覧");
        }
        if ("new".equals(form.getOp())) {
            actor(request).require("WAREHOUSE", "MANAGER");
            SalesOrder order = source(form, request);
            form.setVersion(String.valueOf(order.getVersion()));
            form.setPlannedDate(Dates.format(Dates.today()));
            String[] ids = new String[order.getLines().size()];
            String[] quantities = new String[ids.length];
            @SuppressWarnings("unchecked")
            Map<Long, Integer> capacity = (Map<Long, Integer>) request.getAttribute("instructionCapacity");
            for (int i = 0; i < ids.length; i++) {
                SalesOrderLine line = order.getLines().get(i);
                ids[i] = String.valueOf(line.getId());
                quantities[i] = String.valueOf(capacity.get(line.getId()));
            }
            form.setLineId(ids); form.setQuantity(quantities);
            request.setAttribute("carriers", service.listCarriers(actor(request)));
            return view(request, "shipping/edit", "出荷指示の作成");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        Shipment saved;
        if ("instruct".equals(form.getOp())) {
            List<ShipmentLineInput> lines = lines(form);
            saved = service.instruct(Inputs.id(form.getOrderId(), "受注"), version(form),
                    Inputs.date(form.getPlannedDate(), "出荷予定日", false),
                    Inputs.text(form.getCarrier(), "運送会社", 60, true),
                    Inputs.text(form.getNote(), "出荷備考", 1000, false), lines, actor(request));
        } else if ("confirm".equals(form.getOp())) {
            saved = service.confirm(id(form), version(form), Inputs.date(form.getShippedDate(), "出荷日", false),
                    Inputs.text(form.getTrackingNumber(), "送り状番号", 100, true), actor(request));
        } else {
            saved = service.cancelInstruction(id(form), version(form), reason(form), actor(request));
        }
        return redirect(request, response, "/shipments.do", saved.getId(), "出荷処理を実行しました。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        ShipmentForm form = (ShipmentForm) base;
        if ("list".equals(form.getOp())) { return view(request, "shipping/list", "出荷検索条件の確認"); }
        if ("instruct".equals(form.getOp())) {
            source(form, request);
            request.setAttribute("carriers", shipping(request).listCarriers(actor(request)));
            return view(request, "shipping/edit", "出荷指示内容の確認");
        }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "出荷条件の確認");
    }
    public static List<ShipmentLineInput> lines(ShipmentForm form) {
        Inputs.arrays(form.getLineId(), form.getQuantity());
        Inputs.uniqueIds(form.getLineId(), "受注明細");
        List<ShipmentLineInput> lines = new ArrayList<ShipmentLineInput>();
        for (int i = 0; i < form.getLineId().length; i++) {
            String value = form.getQuantity()[i];
            int quantity = value == null || value.length() == 0 ? 0 : Inputs.quantity(value, "出荷数量", true);
            if (quantity == 0) { continue; }
            ShipmentLineInput line = new ShipmentLineInput();
            line.setOrderLineId(Inputs.id(form.getLineId()[i], "受注明細")); line.setQuantity(quantity);
            lines.add(line);
        }
        if (lines.isEmpty()) { throw Inputs.invalid("明細", "出荷数量を1行以上指定してください。"); }
        return lines;
    }
    private SalesOrder source(ShipmentForm form, HttpServletRequest request) {
        SalesOrder order = service(request, "orderService", OrderService.class)
                .getOrder(Inputs.id(form.getOrderId(), "受注"), actor(request));
        request.setAttribute("order", order);
        Map<Long, Integer> capacity = new HashMap<Long, Integer>();
        for (SalesOrderLine line : order.getLines()) { capacity.put(line.getId(), Integer.valueOf(line.getAllocatedQuantity())); }
        for (Shipment shipment : shipping(request).listOrderShipments(order.getId(), actor(request))) {
            if (!"INSTRUCTED".equals(shipment.getStatus())) { continue; }
            for (ShipmentLine line : shipment.getLines()) {
                Long lineId = line.getOrderLine().getId();
                Integer remaining = capacity.get(lineId);
                if (remaining != null) { capacity.put(lineId, Integer.valueOf(Math.max(0, remaining.intValue() - line.getQuantity()))); }
            }
        }
        request.setAttribute("instructionCapacity", capacity);
        return order;
    }
    private ActionForward detail(ShipmentForm form, HttpServletRequest request, boolean refresh) {
        Shipment shipment = shipping(request).getShipment(id(form), actor(request));
        request.setAttribute("shipment", shipment);
        if (refresh) {
            identity(form, shipment);
            form.setShippedDate(Dates.format(Dates.today()));
            form.setTrackingNumber(shipment.getTrackingNumber());
        }
        return view(request, "shipping/detail", "出荷詳細 · " + shipment.getNumber());
    }
}
