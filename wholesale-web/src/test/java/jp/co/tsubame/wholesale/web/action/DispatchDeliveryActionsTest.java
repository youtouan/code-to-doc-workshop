package jp.co.tsubame.wholesale.web.action;

import java.util.Collections;
import java.util.Date;
import jp.co.tsubame.wholesale.common.Actor;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.DeliveryAttemptCommand;
import jp.co.tsubame.wholesale.common.DeliverySearch;
import jp.co.tsubame.wholesale.common.DeliverySummary;
import jp.co.tsubame.wholesale.common.DispatchConfirmationCommand;
import jp.co.tsubame.wholesale.common.DispatchReadiness;
import jp.co.tsubame.wholesale.common.Page;
import jp.co.tsubame.wholesale.entity.DeliveryAttempt;
import jp.co.tsubame.wholesale.entity.DispatchManifest;
import jp.co.tsubame.wholesale.entity.DispatchStop;
import jp.co.tsubame.wholesale.entity.SalesOrder;
import jp.co.tsubame.wholesale.entity.Shipment;
import jp.co.tsubame.wholesale.entity.ShipmentLine;
import jp.co.tsubame.wholesale.entity.Warehouse;
import jp.co.tsubame.wholesale.service.DeliveryAttemptService;
import jp.co.tsubame.wholesale.service.DispatchManifestService;
import jp.co.tsubame.wholesale.web.HttpFixture;
import jp.co.tsubame.wholesale.web.PreciseDates;
import jp.co.tsubame.wholesale.web.form.DeliveryForm;
import jp.co.tsubame.wholesale.web.form.DispatchForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import static org.junit.Assert.*;

public class DispatchDeliveryActionsTest {
    @Test public void managerCannotRecordAndWarehouseCannotCorrect() throws Exception {
        HttpFixture manager = actor("MANAGER"); manager.method = "POST"; DeliveryForm record = observation(); record.setOp("record");
        new DeliveryAction().execute(mapping("/deliveryAttempts"), record, manager.request, manager.response);
        assertEquals(403, manager.status);
        HttpFixture warehouse = actor("WAREHOUSE"); warehouse.method = "POST"; DeliveryForm correction = observation(); correction.setOp("correct");
        new DeliveryAction().execute(mapping("/deliveryAttempts"), correction, warehouse.request, warehouse.response);
        assertEquals(403, warehouse.status);
    }
    @Test public void managerCannotConfirmDispatchWithoutWarehouseOrBatchRole() throws Exception {
        HttpFixture http = actor("MANAGER"); http.method = "POST"; DispatchForm form = new DispatchForm(); form.setOp("confirm");
        new DispatchAction().execute(mapping("/dispatchManifests"), form, http.request, http.response);
        assertEquals(403, http.status);
    }
    @Test public void ownDispatchConfirmationFormDoesNotInventTrackingReferences() throws Exception {
        final DispatchManifest manifest = manifest();
        HttpFixture http = actor("WAREHOUSE");
        context(http, "dispatchManifestService", new DispatchManifestService() {
            public DispatchManifest get(Actor actor, Long id) { return manifest; }
            public DispatchReadiness getReadiness(Actor actor, Long id) { return new DispatchReadiness(id, manifest.getVersion()); }
        });
        DispatchForm form = new DispatchForm(); form.setOp("detail"); form.setId("20");
        new DispatchAction().execute(mapping("/dispatchManifests"), form, http.request, http.response);
        assertEquals(200, http.status); assertEquals(1, form.getShipmentId().length);
        assertEquals("", form.getTrackingReference()[0]); assertEquals("3", form.getVersion());
    }
    @Test public void dispatchUsesOneAtomicServiceCallWithManualReferences() throws Exception {
        HttpFixture http = actor("BATCH"); http.method = "POST";
        context(http, "dispatchManifestService", new DispatchManifestService() {
            public DispatchManifest confirm(Actor actor, Long id, int version, DispatchConfirmationCommand command) {
                assertEquals(Long.valueOf(20), id); assertEquals(3, version);
                assertEquals("実際の自社番号", command.getTracking().get(0).getTrackingReference()); return manifest();
            }
        });
        DispatchForm form = new DispatchForm(); form.setOp("confirm"); form.setId("20"); form.setVersion("3");
        form.setDispatchDate("2020-01-02"); form.setShipmentId(new String[] {"5"}); form.setTrackingReference(new String[] {"実際の自社番号"});
        assertNull(new DispatchAction().execute(mapping("/dispatchManifests"), form, http.request, http.response));
        assertEquals(303, http.status); assertEquals("/wholesale/dispatchManifests.do?op=detail&id=20", http.headers.get("Location"));
    }
    @Test public void manualRecordPreservesJournalCursorAndRedirectsToActualShipment() throws Exception {
        HttpFixture http = actor("SALES"); http.method = "POST";
        context(http, "deliveryAttemptService", new DeliveryAttemptService() {
            public DeliveryAttempt record(Actor actor, DeliveryAttemptCommand command) {
                assertNull(command.getExpectedLatestEventId()); assertEquals("same-key", command.getRequestKey());
                assertEquals("証跡参照", command.getEvidenceReference());
                DeliveryAttempt event = new DeliveryAttempt(); event.setId(42L); event.setShipment(shipment()); return event;
            }
        });
        DeliveryForm form = observation(); form.setOp("record");
        new DeliveryAction().execute(mapping("/deliveryAttempts"), form, http.request, http.response);
        assertEquals(303, http.status); assertEquals("/wholesale/deliveryAttempts.do?op=detail&id=5", http.headers.get("Location"));
    }
    @Test public void correctionEditorLoadsPersistedPayloadAndCurrentCursorWithoutWriting() throws Exception {
        HttpFixture http = actor("MANAGER"); http.method = "POST";
        context(http, "deliveryAttemptService", new DeliveryAttemptService() {
            public DeliveryAttempt getAttempt(Actor actor, Long id) {
                assertEquals(Long.valueOf(40), id);
                DeliveryAttempt event = persistedAttempt(); event.setId(id); return event;
            }
            public DeliverySummary getSummary(Actor actor, Long id, Date cutoff) { return new DeliverySummary(shipment(), null, 99L, null); }
            public DeliveryAttempt correct(Actor actor, Long target, DeliveryAttemptCommand command, String reason) {
                fail("Opening the editor must not append a correction"); return null;
            }
        });
        DeliveryForm form = observation(); form.setOp("editCorrection"); form.setTargetEventId("40"); form.setExpectedLatestEventId("42");
        form.setReportingCompany("クライアントから差し替え"); form.setEvidenceReference("差し替え参照");
        ActionForward result = new DeliveryAction().execute(mapping("/deliveryAttempts"), form, http.request, http.response);
        assertEquals("/WEB-INF/jsp/delivery/edit.jsp", result.getPath()); assertEquals(200, http.status);
        assertEquals("99", form.getExpectedLatestEventId()); assertEquals("保存済み参照", form.getEvidenceReference());
        assertEquals("保存済み会社", form.getReportingCompany()); assertEquals("RESCHEDULED", form.getOutcome());
        assertEquals("2020-01-03T07:08:09.123", form.getAttemptAt()); assertEquals("2020-01-04", form.getNextAttemptDate());
        assertTrue(form.getRequestKey().matches("[a-f0-9]{64}"));
    }
    @Test public void correctionEditorRejectsAnotherShipmentAndReversalEvents() throws Exception {
        for (final boolean reversal : new boolean[] {false, true}) {
            HttpFixture http = actor("MANAGER"); http.method = "POST";
            context(http, "deliveryAttemptService", new DeliveryAttemptService() {
                public DeliveryAttempt getAttempt(Actor actor, Long id) {
                    DeliveryAttempt event = persistedAttempt();
                    if (reversal) { event.setEventType("REVERSAL"); }
                    else { event.getShipment().setId(6L); }
                    return event;
                }
                public DeliverySummary getSummary(Actor actor, Long id, Date cutoff) {
                    fail("Invalid editor targets must not be prepared"); return null;
                }
            });
            DeliveryForm form = observation(); form.setOp("editCorrection"); form.setTargetEventId("40");
            ActionForward result = new DeliveryAction().execute(mapping("/deliveryAttempts"), form, http.request, http.response);
            assertEquals(422, http.status); assertEquals("/WEB-INF/jsp/error.jsp", result.getPath());
        }
    }
    @Test public void failedCorrectionKeepsUserEditsAndDoesNotReloadOrRebaseTarget() throws Exception {
        HttpFixture http = actor("MANAGER"); http.method = "POST";
        context(http, "deliveryAttemptService", new DeliveryAttemptService() {
            public DeliveryAttempt correct(Actor actor, Long target, DeliveryAttemptCommand command, String reason) {
                throw new BusinessException("delivery.concurrent", "最新履歴を再表示してください。");
            }
            public DeliveryAttempt getAttempt(Actor actor, Long id) {
                fail("Failed save must not overwrite replacement input"); return null;
            }
            public DeliverySummary getSummary(Actor actor, Long id, Date cutoff) { return new DeliverySummary(shipment(), null, 99L, null); }
        });
        DeliveryForm form = observation(); form.setOp("correct"); form.setTargetEventId("40");
        form.setExpectedLatestEventId("42"); form.setCorrectionReason("入力内容を訂正");
        new DeliveryAction().execute(mapping("/deliveryAttempts"), form, http.request, http.response);
        assertEquals(409, http.status); assertEquals("42", form.getExpectedLatestEventId());
        assertEquals("same-key", form.getRequestKey()); assertEquals("証跡参照", form.getEvidenceReference());
        assertEquals("2020-01-02T12:00:01.123", form.getAttemptAt());
    }
    @Test public void concurrentJournalFailureIs409AndPreservesEnteredObservationAndKey() throws Exception {
        HttpFixture http = actor("WAREHOUSE"); http.method = "POST";
        context(http, "deliveryAttemptService", new DeliveryAttemptService() {
            public DeliveryAttempt record(Actor actor, DeliveryAttemptCommand command) {
                throw new BusinessException("delivery.concurrent", "最新履歴を再表示してください。");
            }
            public DeliverySummary getSummary(Actor actor, Long id, Date cutoff) { return new DeliverySummary(shipment(), null, 99L, null); }
        });
        DeliveryForm form = observation(); form.setOp("record"); form.setExpectedLatestEventId("42");
        ActionForward result = new DeliveryAction().execute(mapping("/deliveryAttempts"), form, http.request, http.response);
        assertEquals(409, http.status); assertEquals("/WEB-INF/jsp/delivery/edit.jsp", result.getPath());
        assertEquals("same-key", form.getRequestKey()); assertEquals("42", form.getExpectedLatestEventId());
        assertEquals("2020-01-02T12:00:01.123", form.getAttemptAt()); assertNull(http.headers.get("Location"));
    }
    @Test public void historicalSummaryKeepsCutoffAndCurrentReturnsSeparately() throws Exception {
        HttpFixture http = actor("BILLING");
        context(http, "deliveryAttemptService", new DeliveryAttemptService() {
            public DeliverySummary getSummary(Actor actor, Long id, Date cutoff) {
                assertEquals("2020-01-03T12:34:56.123", PreciseDates.format(cutoff));
                return new DeliverySummary(shipment(), null, 42L, cutoff);
            }
            public Page<DeliveryAttempt> listHistory(Actor actor, Long id, DeliverySearch search) {
                return new Page<DeliveryAttempt>(Collections.<DeliveryAttempt>emptyList(), 0, search.getPage(), search.getSize());
            }
        });
        DeliveryForm form = new DeliveryForm(); form.setOp("detail"); form.setShipmentId("5"); form.setAsOfRecordedAt("2020-01-03T12:34:56.123");
        new DeliveryAction().execute(mapping("/deliveryAttempts"), form, http.request, http.response);
        assertEquals(Boolean.TRUE, http.attributes.get("historical"));
        DeliverySummary summary = (DeliverySummary) http.attributes.get("deliverySummary");
        assertEquals(10, summary.getShippedQuantity()); assertEquals(3, summary.getCurrentReturnedQuantity());
        assertEquals("NONE", summary.getOutcome()); assertEquals("MANUAL_UNVERIFIED_REPORT", summary.getOutcomeBasis());
    }
    private DeliveryForm observation() {
        DeliveryForm form = new DeliveryForm(); form.setShipmentId("5"); form.setRequestKey("same-key");
        form.setAttemptAt("2020-01-02T12:00:01.123"); form.setOutcome("DELIVERED");
        form.setReportingCompany("会社名"); form.setEvidenceReference("証跡参照"); return form;
    }
    private static DeliveryAttempt persistedAttempt() {
        DeliveryAttempt event = new DeliveryAttempt(); event.setId(40L); event.setShipment(shipment());
        event.setEventType("ATTEMPT"); event.setOutcome("RESCHEDULED");
        event.setAttemptAt(PreciseDates.parse("2020-01-03T07:08:09.123", "日時", false));
        event.setReportingCompany("保存済み会社"); event.setEvidenceReference("保存済み参照");
        event.setReason("保存済み理由"); event.setNextAttemptDate(Dates.parse("2020-01-04")); return event;
    }
    private static Shipment shipment() {
        Warehouse warehouse = new Warehouse(); warehouse.setId(1L); warehouse.setCode("W1"); warehouse.setName("倉庫");
        SalesOrder order = new SalesOrder(); order.setId(2L); order.setWarehouse(warehouse); order.setNumber("SO2"); order.setCustomerName("得意先");
        Shipment shipment = new Shipment(); shipment.setId(5L); shipment.setVersion(4); shipment.setOrder(order); shipment.setNumber("SH5");
        shipment.setCarrier("OWN"); shipment.setShippedDate(Dates.parse("2020-01-01"));
        ShipmentLine line = new ShipmentLine(); line.setQuantity(10); line.setReturnedQuantity(3); shipment.getLines().add(line); return shipment;
    }
    private static DispatchManifest manifest() {
        DispatchManifest manifest = new DispatchManifest(); manifest.setId(20L); manifest.setVersion(3);
        manifest.setCarrier("OWN"); manifest.setStatus("RELEASED"); manifest.setNumber("DSP20"); manifest.setPlannedDispatchDate(Dates.today());
        DispatchStop stop = new DispatchStop(); stop.setShipment(shipment()); manifest.getStops().add(stop); return manifest;
    }
    private HttpFixture actor(String role) { HttpFixture http = new HttpFixture(); http.attributes.put("actor", new Actor(99L, "test", "検証", role)); return http; }
    private ActionMapping mapping(String path) { ActionMapping mapping = new ActionMapping(); mapping.setPath(path); return mapping; }
    private void context(HttpFixture http, String name, Object bean) {
        StaticWebApplicationContext context = new StaticWebApplicationContext(); context.setServletContext(http.context);
        context.getBeanFactory().registerSingleton(name, bean); context.refresh();
        http.contextAttributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
    }
}
