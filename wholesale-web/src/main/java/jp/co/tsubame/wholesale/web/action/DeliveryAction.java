package jp.co.tsubame.wholesale.web.action;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.DeliverySearch;
import jp.co.tsubame.wholesale.common.DeliverySummary;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.entity.DeliveryAttempt;
import jp.co.tsubame.wholesale.service.DeliveryAttemptService;
import jp.co.tsubame.wholesale.web.DeliveryInputs;
import jp.co.tsubame.wholesale.web.Inputs;
import jp.co.tsubame.wholesale.web.PreciseDates;
import jp.co.tsubame.wholesale.web.Web;
import jp.co.tsubame.wholesale.web.form.BaseForm;
import jp.co.tsubame.wholesale.web.form.DeliveryForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class DeliveryAction extends BaseAction {
    protected boolean isRead(String op) { return "list".equals(op) || "detail".equals(op) || "new".equals(op); }
    protected boolean isMutation(String op) {
        return "record".equals(op) || "editCorrection".equals(op) || "correct".equals(op) || "reverse".equals(op);
    }
    private DeliveryAttemptService delivery(HttpServletRequest request) {
        return service(request, "deliveryAttemptService", DeliveryAttemptService.class);
    }
    protected ActionForward perform(ActionMapping mapping, BaseForm base, HttpServletRequest request,
                                    HttpServletResponse response) throws Exception {
        DeliveryForm form = (DeliveryForm) base;
        if ("record".equals(form.getOp()) || "new".equals(form.getOp())) { actor(request).require("WAREHOUSE", "SALES", "BATCH"); }
        else if (isMutation(form.getOp())) { actor(request).require("MANAGER"); }
        if ("list".equals(form.getOp())) {
            references(request); request.setAttribute("queueMode", Boolean.TRUE); parameters(form, request);
            request.setAttribute("results", delivery(request).searchQueue(actor(request), DeliveryInputs.search(form)));
            return view(request, "delivery/queue", "手動配送報告・再対応キュー");
        }
        if ("detail".equals(form.getOp())) { return detail(form, request, true); }
        if ("new".equals(form.getOp())) {
            form.setAsOfRecordedAt("");
            DeliverySummary summary = summary(form, request, null);
            if (summary.getShippedBusinessDate() == null) { throw Inputs.invalid("出荷", "出荷確定後に配送報告を記録してください。"); }
            form.setExpectedLatestEventId(summary.getLatestEventId() == null ? "" : summary.getLatestEventId().toString());
            form.setTargetEventId(""); form.setRequestKey(Web.randomToken());
            return view(request, "delivery/edit", "配送試行の手動記録");
        }
        if ("editCorrection".equals(form.getOp())) {
            DeliveryAttempt target = delivery(request).getAttempt(actor(request), Inputs.id(form.getTargetEventId(), "訂正対象記録"));
            Long shipment = target.getShipment().getId();
            Long requestedShipment = Inputs.optionalId(form.getShipmentId(), "出荷");
            if (requestedShipment != null && !requestedShipment.equals(shipment)) {
                throw Inputs.invalid("訂正対象記録", "表示中の出荷に属する配送記録を選択してください。");
            }
            if ("REVERSAL".equals(target.getEventType())) {
                throw Inputs.invalid("訂正対象記録", "取消イベント自体は訂正できません。");
            }
            form.setShipmentId(String.valueOf(shipment)); form.setTargetEventId(String.valueOf(target.getId()));
            form.setAsOfRecordedAt("");
            DeliverySummary current = summary(form, request, null);
            form.setExpectedLatestEventId(current.getLatestEventId() == null ? "" : current.getLatestEventId().toString());
            form.setAttemptAt(PreciseDates.format(target.getAttemptAt())); form.setOutcome(target.getOutcome());
            form.setReportingCompany(target.getReportingCompany()); form.setEvidenceReference(target.getEvidenceReference());
            form.setReason(target.getReason()); form.setNextAttemptDate(Dates.format(target.getNextAttemptDate()));
            form.setRequestKey(Web.randomToken()); form.setCorrectionReason("");
            return view(request, "delivery/edit", "配送報告の訂正入力");
        }
        DeliveryAttempt result;
        if ("record".equals(form.getOp())) { result = delivery(request).record(actor(request), DeliveryInputs.observation(form)); }
        else if ("correct".equals(form.getOp())) {
            result = delivery(request).correct(actor(request), Inputs.id(form.getTargetEventId(), "訂正対象記録"),
                    DeliveryInputs.observation(form), Inputs.text(form.getCorrectionReason(), "訂正理由", 500, true));
        } else {
            result = delivery(request).reverse(actor(request), Inputs.id(form.getTargetEventId(), "取消対象記録"),
                    Inputs.optionalId(form.getExpectedLatestEventId(), "最新記録ID"),
                    Inputs.text(form.getRequestKey(), "処理キー", 120, true), Inputs.text(form.getCorrectionReason(), "取消理由", 500, true));
        }
        return redirect(request, response, "/deliveryAttempts.do", result.getShipment().getId(), "手動配送記録を追加しました。既存記録は変更・削除していません。");
    }
    protected ActionForward failure(BaseForm base, HttpServletRequest request) {
        DeliveryForm form = (DeliveryForm) base;
        if ("list".equals(form.getOp())) { return view(request, "delivery/queue", "配送報告の検索条件確認"); }
        if ("editCorrection".equals(form.getOp())) { return view(request, "error", "訂正対象記録の確認"); }
        if ("record".equals(form.getOp()) || "correct".equals(form.getOp())) {
            summary(form, request, null); return view(request, "delivery/edit", "手動配送報告内容の確認");
        }
        return detail(form, request, false);
    }
    private DeliverySummary summary(DeliveryForm form, HttpServletRequest request, Date cutoff) {
        Long shipment = form.getShipmentId().length() > 0 ? Inputs.id(form.getShipmentId(), "出荷") : id(form);
        DeliverySummary summary = delivery(request).getSummary(actor(request), shipment, cutoff);
        form.setShipmentId(String.valueOf(shipment)); form.setId(String.valueOf(shipment)); request.setAttribute("deliverySummary", summary);
        return summary;
    }
    private ActionForward detail(DeliveryForm form, HttpServletRequest request, boolean refresh) {
        Date cutoff = DeliveryInputs.cutoff(form);
        DeliverySummary summary = summary(form, request, cutoff);
        DeliverySearch search = DeliveryInputs.search(form);
        Page<DeliveryAttempt> history = delivery(request).listHistory(actor(request), summary.getShipmentId(), search);
        request.setAttribute("results", history); request.setAttribute("historical", Boolean.valueOf(cutoff != null));
        if (refresh) { form.setExpectedLatestEventId(summary.getLatestEventId() == null ? "" : summary.getLatestEventId().toString()); }
        Map<Long, String> keys = new LinkedHashMap<Long, String>();
        for (DeliveryAttempt event : history.getItems()) {
            keys.put(event.getId(), "reverse".equals(form.getOp()) && String.valueOf(event.getId()).equals(form.getTargetEventId())
                    ? form.getRequestKey() : Web.randomToken());
        }
        request.setAttribute("reversalKeys", keys); parameters(form, request);
        return view(request, "delivery/detail", "配送手動記録 · " + summary.getShipmentNumber());
    }
    private void parameters(DeliveryForm form, HttpServletRequest request) {
        Map<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("shipmentId", form.getShipmentId()); extra.put("asOfRecordedAt", form.getAsOfRecordedAt());
        extra.put("dueOnOrBefore", form.getDueOnOrBefore()); request.setAttribute("extraPageParameters", extra);
    }
}
