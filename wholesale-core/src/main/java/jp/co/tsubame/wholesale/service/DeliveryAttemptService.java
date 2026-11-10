package jp.co.tsubame.wholesale.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.Checks;
import jp.co.tsubame.wholesale.common.DeliveryAttemptCommand;
import jp.co.tsubame.wholesale.common.DeliveryRules;
import jp.co.tsubame.wholesale.common.DeliverySearch;
import jp.co.tsubame.wholesale.common.DeliverySummary;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.dao.DeliveryAttemptDao;
import jp.co.tsubame.wholesale.dao.DispatchLocks;
import jp.co.tsubame.wholesale.dao.WholesaleDao;
import jp.co.tsubame.wholesale.entity.DeliveryAttempt;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.ShipmentLine;

public class DeliveryAttemptService extends BaseService {
    private DispatchLocks locks;
    private DeliveryAttemptDao attempts;
    public void setLocks(DispatchLocks locks) { this.locks = locks; }
    public void setAttempts(DeliveryAttemptDao attempts) { this.attempts = attempts; }

    public DeliveryAttempt record(Actor actor, DeliveryAttemptCommand command) {
        require(actor, "WAREHOUSE", "SALES", "BATCH");
        return observe(actor, command, null, "");
    }

    public DeliveryAttempt correct(Actor actor, Long targetEventId, DeliveryAttemptCommand command, String reason) {
        require(actor, "MANAGER");
        Checks.state(targetEventId != null, "delivery.target", "訂正対象の報告を指定してください。");
        return observe(actor, command, targetEventId, Checks.text(reason, "訂正理由", 500));
    }

    private DeliveryAttempt observe(Actor actor, DeliveryAttemptCommand command, Long targetId, String reason) {
        Checks.state(command != null && command.getShipmentId() != null, "delivery.shipment", "出荷を指定してください。");
        Shipment shipment = lockShipment(command.getShipmentId());
        DeliveryAttempt event = DeliveryRules.observation(shipment, command);
        event.setEventType(targetId == null ? "ATTEMPT" : "CORRECTION");
        event.setSupersedesId(targetId);
        event.setCorrectionReason(reason);
        return append(actor, event, command.getExpectedLatestEventId(), command.getRequestKey());
    }

    public DeliveryAttempt reverse(Actor actor, Long targetEventId, Long expectedLatestEventId, String requestKey, String reason) {
        require(actor, "MANAGER");
        Checks.state(targetEventId != null, "delivery.target", "取消対象の報告を指定してください。");
        Long shipmentId = (Long) dao.query("select a.shipment.id from DeliveryAttempt a where a.id=:id",
                WholesaleDao.params("id", targetEventId)).uniqueResult();
        Checks.state(shipmentId != null, "notFound", "配送報告が見つかりません。");
        Shipment shipment = lockShipment(shipmentId);
        DeliveryAttempt target = dao.get(DeliveryAttempt.class, targetEventId);
        DeliveryAttempt event = new DeliveryAttempt();
        event.setShipment(shipment);
        event.setEventType("REVERSAL");
        event.setSupersedesId(targetEventId);
        event.setAttemptAt(target.getAttemptAt());
        event.setBusinessDate(target.getBusinessDate());
        event.setOutcome("REVERSED");
        event.setReportingCompany(target.getReportingCompany());
        event.setEvidenceReference(target.getEvidenceReference());
        event.setCorrectionReason(Checks.text(reason, "報告取消理由", 500));
        return append(actor, event, expectedLatestEventId, requestKey);
    }

    private DeliveryAttempt append(Actor actor, DeliveryAttempt event, Long expectedLatestId, String requestKey) {
        String key = Checks.text(requestKey, "処理キー", 120);
        event.setRequestKey(key);
        event.setFingerprint(DeliveryRules.fingerprint(event));
        dao.lockKey("delivery-attempt", key);
        List<DeliveryAttempt> prior = dao.list("from DeliveryAttempt a where a.requestKey=:key", WholesaleDao.params("key", key));
        if (!prior.isEmpty()) {
            Checks.state(prior.get(0).getFingerprint().equals(event.getFingerprint()), "idempotency.conflict",
                    "同じ処理キーに異なる配送報告が指定されています。");
            return initialize(prior.get(0));
        }
        DeliveryAttempt latest = attempts.latestRecorded(event.getShipment().getId(), null);
        DeliveryRules.cursor(latest == null ? null : latest.getId(), expectedLatestId);
        if (event.getSupersedesId() != null) {
            DeliveryAttempt target = dao.get(DeliveryAttempt.class, event.getSupersedesId());
            Checks.state(target.getShipment().getId().equals(event.getShipment().getId())
                    && !"REVERSAL".equals(target.getEventType()), "delivery.target", "同じ出荷の有効な報告を指定してください。");
            Checks.state(dao.count("select count(a.id) from DeliveryAttempt a where a.supersedesId=:id",
                    WholesaleDao.params("id", target.getId())) == 0, "delivery.superseded", "この報告は既に訂正または取消されています。");
        }
        if ("DELIVERED".equals(event.getOutcome())) {
            long shipped = 0;
            long returned = 0;
            for (ShipmentLine line : event.getShipment().getLines()) {
                shipped += line.getQuantity();
                returned += line.getReturnedQuantity();
            }
            Checks.state(shipped > returned, "delivery.fullyReturned", "全数返品受入済みの出荷を新たに配達完了とは報告できません。");
        }
        DeliveryAttempt effective = attempts.effective(event.getShipment().getId(), null);
        Checks.state(!"ATTEMPT".equals(event.getEventType()) || effective == null || !"DELIVERED".equals(effective.getOutcome())
                || event.getAttemptAt().before(effective.getAttemptAt()), "delivery.alreadyDelivered",
                "配達完了報告後の結果変更は管理者の訂正・取消を使用してください。");
        Checks.state(latest == null || latest.getSequenceNumber() < 1000000, "delivery.eventLimit", "配送履歴の件数上限です。");
        event.setSequenceNumber(latest == null ? 1 : latest.getSequenceNumber() + 1);
        event.setRecordedBy(actor.getLogin());
        event.setRecordedAt(new Date(Math.max(System.currentTimeMillis(), latest == null ? 0L : latest.getRecordedAt().getTime() + 1L)));
        dao.save(event);
        audit(actor, "DELIVERY_" + event.getEventType(), event, event.getEvidenceBasis() + "; " + event.getEvidenceReference()
                + "; " + event.getCorrectionReason());
        dao.flush();
        return initialize(event);
    }

    public DeliverySummary getSummary(Actor actor, Long shipmentId, Date asOfRecordedAt) {
        reader(actor);
        Shipment shipment = DispatchLocks.initialize(dao.get(Shipment.class, shipmentId));
        DeliveryAttempt latest = attempts.latestRecorded(shipmentId, asOfRecordedAt);
        return new DeliverySummary(shipment, attempts.effective(shipmentId, asOfRecordedAt),
                latest == null ? null : latest.getId(), asOfRecordedAt);
    }

    public DeliveryAttempt getAttempt(Actor actor, Long eventId) {
        reader(actor);
        return initialize(dao.get(DeliveryAttempt.class, eventId));
    }

    public Page<DeliveryAttempt> listHistory(Actor actor, Long shipmentId, DeliverySearch search) {
        reader(actor);
        dao.get(Shipment.class, shipmentId);
        Page<DeliveryAttempt> page = attempts.history(shipmentId, search);
        for (DeliveryAttempt attempt : page.getItems()) { initialize(attempt); }
        return page;
    }

    public Page<DeliverySummary> searchQueue(Actor actor, DeliverySearch search) {
        reader(actor);
        Page<DeliveryAttempt> page = attempts.queue(search);
        List<DeliverySummary> summaries = new ArrayList<DeliverySummary>();
        for (DeliveryAttempt attempt : page.getItems()) {
            Shipment shipment = DispatchLocks.initialize(attempt.getShipment());
            DeliveryAttempt latest = attempts.latestRecorded(shipment.getId(), search.getAsOfRecordedAt());
            summaries.add(new DeliverySummary(shipment, attempt, latest == null ? null : latest.getId(), search.getAsOfRecordedAt()));
        }
        return new Page<DeliverySummary>(summaries, page.getTotal(), page.getNumber(), page.getSize());
    }

    private Shipment lockShipment(Long id) {
        Shipment shipment = locks.sources(Collections.singleton(id), Collections.<Long>emptyList()).get(id);
        Checks.state("CONFIRMED".equals(shipment.getStatus()), "delivery.notConfirmed", "出荷確定済みの出荷のみ配送結果を記録できます。");
        return shipment;
    }
    private DeliveryAttempt initialize(DeliveryAttempt event) { DispatchLocks.initialize(event.getShipment()); return event; }
    private void reader(Actor actor) { require(actor, "WAREHOUSE", "MANAGER", "SALES", "BILLING", "BATCH"); }
}
