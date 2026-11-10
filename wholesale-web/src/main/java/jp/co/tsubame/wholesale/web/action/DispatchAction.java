package jp.co.tsubame.wholesale.web.action;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.common.Search;
import jp.co.tsubame.wholesale.entity.DispatchManifest;
import jp.co.tsubame.wholesale.entity.DispatchStop;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.service.DispatchManifestService;
import jp.co.tsubame.wholesale.service.ShippingService;
import jp.co.tsubame.wholesale.web.DispatchInputs;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.DispatchForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class DispatchAction extends BaseAction {
    protected boolean isRead(String op) { return super.isRead(op) || "candidates".equals(op) || "print".equals(op); }
    protected boolean isMutation(String op) {
        return "save".equals(op) || "addLine".equals(op) || "release".equals(op)
                || "replan".equals(op) || "cancel".equals(op) || "confirm".equals(op);
    }
    private DispatchManifestService dispatch(HttpServletRequest request) {
        return service(request, "dispatchManifestService", DispatchManifestService.class);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        DispatchForm form = (DispatchForm) base;
        if ("confirm".equals(form.getOp())) { actor(request).require("WAREHOUSE", "BATCH"); }
        else if (isMutation(form.getOp()) || "new".equals(form.getOp()) || "edit".equals(form.getOp())) { actor(request).require("WAREHOUSE", "MANAGER"); }
        DispatchManifestService service = dispatch(request);
        if ("list".equals(form.getOp()) || "candidates".equals(form.getOp())) {
            references(request); request.setAttribute("searchCustomer", Boolean.TRUE); request.setAttribute("searchWarehouse", Boolean.TRUE);
            request.setAttribute("searchDates", Boolean.TRUE);
            boolean candidates = "candidates".equals(form.getOp()); request.setAttribute("candidateMode", Boolean.valueOf(candidates));
            request.setAttribute("statuses", candidates ? new String[] {"INSTRUCTED"} : new String[] {"DRAFT", "RELEASED", "DISPATCHED", "CANCELLED"});
            request.setAttribute("searchOperation", form.getOp());
            request.setAttribute("results", candidates ? service.listCandidates(actor(request), Inputs.search(form))
                    : service.search(actor(request), Inputs.search(form)));
            return view(request, "dispatch/list", candidates ? "配送表に未割当の出荷指示" : "配送表一覧");
        }
        if ("print".equals(form.getOp())) {
            request.setAttribute("printView", service.getPrintView(actor(request), id(form)));
            return view(request, "dispatch/print", "配送表・積込確認票");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        if ("new".equals(form.getOp())) {
            form.setPlannedDispatchDate(Dates.format(Dates.today()));
            if (form.getShipmentChoice().length > 0 && form.getShipmentChoice()[0].length() > 0) {
                Shipment first = service(request, "shippingService", ShippingService.class)
                        .getShipment(DispatchInputs.choice(form.getShipmentChoice()[0]).getShipmentId(), actor(request));
                form.setWarehouseId(String.valueOf(first.getWarehouse().getId())); form.setCarrier(first.getCarrier());
                if (first.getPlannedDate().after(Dates.today())) { form.setPlannedDispatchDate(Dates.format(first.getPlannedDate())); }
            }
            form.rows(Math.max(5, form.getShipmentChoice().length)); return edit(form, request);
        }
        if ("edit".equals(form.getOp())) {
            DispatchManifest manifest = service.get(actor(request), id(form));
            if (!"DRAFT".equals(manifest.getStatus())) { throw Inputs.invalid("状態", "編集には先に再計画で下書きへ戻してください。"); }
            identity(form, manifest); form.setWarehouseId(String.valueOf(manifest.getWarehouse().getId()));
            form.setCarrier(manifest.getCarrier()); form.setPlannedDispatchDate(Dates.format(manifest.getPlannedDispatchDate()));
            form.setNote(manifest.getNote()); form.rows(Math.max(5, manifest.getStops().size()));
            for (int i = 0; i < manifest.getStops().size(); i++) {
                DispatchStop stop = manifest.getStops().get(i);
                form.getShipmentChoice()[i] = token(stop.getShipment()); form.getStopNote()[i] = stop.getNote();
            }
            return edit(form, request);
        }
        if ("addLine".equals(form.getOp())) {
            Inputs.arrays(form.getShipmentChoice(), form.getStopNote()); form.rows(Math.min(100, form.getShipmentChoice().length + 5));
            return edit(form, request);
        }
        DispatchManifest saved;
        if ("save".equals(form.getOp())) { saved = service.saveDraft(actor(request), DispatchInputs.plan(form)); }
        else if ("release".equals(form.getOp())) { saved = service.release(actor(request), id(form), version(form)); }
        else if ("replan".equals(form.getOp())) { saved = service.replan(actor(request), id(form), version(form), reason(form)); }
        else if ("cancel".equals(form.getOp())) { saved = service.cancel(actor(request), id(form), version(form), reason(form)); }
        else { saved = service.confirm(actor(request), id(form), version(form), DispatchInputs.confirmation(form)); }
        return redirect(request, response, "/dispatchManifests.do", saved.getId(), "配送表の処理を実行しました。現在の状態をご確認ください。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        DispatchForm form = (DispatchForm) base;
        if ("list".equals(form.getOp()) || "candidates".equals(form.getOp())) { return view(request, "dispatch/list", "配送表検索条件の確認"); }
        if ("save".equals(form.getOp()) || "addLine".equals(form.getOp())) { return edit(form, request); }
        if (form.getId().length() > 0) { return detail(form, request, false); }
        return view(request, "error", "配送表の確認");
    }
    private ActionForward edit(DispatchForm form, HttpServletRequest request) {
        request.setAttribute("warehouses", catalog(request).listActiveWarehouses(actor(request)));
        request.setAttribute("carriers", service(request, "shippingService", ShippingService.class).listCarriers(actor(request)));
        Search search = new Search(); search.setWarehouseId(Inputs.optionalId(form.getWarehouseId(), "配送元倉庫")); search.setSize(100);
        Page<Shipment> candidates = dispatch(request).listCandidates(actor(request), search);
        Map<String, String> choices = new LinkedHashMap<String, String>();
        for (Shipment shipment : candidates.getItems()) { choices.put(token(shipment), caption(shipment)); }
        if (form.getId().length() > 0) {
            DispatchManifest manifest = dispatch(request).get(actor(request), id(form));
            for (DispatchStop stop : manifest.getStops()) { choices.put(token(stop.getShipment()), caption(stop.getShipment())); }
        }
        request.setAttribute("shipmentChoices", choices); request.setAttribute("candidateCount", Long.valueOf(candidates.getTotal()));
        return view(request, "dispatch/edit", "配送表下書き・手動配送順");
    }
    private ActionForward detail(DispatchForm form, HttpServletRequest request, boolean refresh) {
        DispatchManifest manifest = dispatch(request).get(actor(request), id(form)); request.setAttribute("manifest", manifest);
        request.setAttribute("readiness", dispatch(request).getReadiness(actor(request), manifest.getId()));
        request.setAttribute("today", Dates.today());
        if (refresh) {
            identity(form, manifest); form.setDispatchDate(Dates.format(Dates.today()));
            String[] ids = new String[manifest.getStops().size()], tracking = new String[ids.length];
            for (int i = 0; i < ids.length; i++) { ids[i] = String.valueOf(manifest.getStops().get(i).getShipment().getId()); tracking[i] = ""; }
            form.setShipmentId(ids); form.setTrackingReference(tracking);
        }
        return view(request, "dispatch/detail", "配送表詳細 · " + manifest.getNumber());
    }
    private static String token(Shipment shipment) { return shipment.getId() + ":" + shipment.getVersion(); }
    private static String caption(Shipment shipment) {
        return shipment.getNumber() + " / " + shipment.getOrder().getCustomerName() + " / "
                + shipment.getWarehouse().getCode() + " / " + shipment.getCarrier() + " / " + Dates.format(shipment.getPlannedDate());
    }
}
