package jp.co.tsubame.wholesale.web;

import java.util.Date;
import jp.co.tsubame.wholesale.common.BusinessException;
import jp.co.tsubame.wholesale.common.Dates;
import jp.co.tsubame.wholesale.common.DeliveryAttemptCommand;
import jp.co.tsubame.wholesale.common.DispatchManifestCommand;
import jp.co.tsubame.wholesale.web.form.DeliveryForm;
import jp.co.tsubame.wholesale.web.form.DispatchForm;
import org.junit.Test;
import static org.junit.Assert.*;

public class DispatchDeliveryInputsTest {
    @Test public void shipmentChoicesPreserveOrderAndExplicitOptimisticVersions() {
        DispatchForm form = plan();
        form.setShipmentChoice(new String[] {"12:7", "11:3", ""}); form.setStopNote(new String[] {"先に配送", "後に配送", ""});
        DispatchManifestCommand command = DispatchInputs.plan(form);
        assertEquals(2, command.getStops().size());
        assertEquals(Long.valueOf(12), command.getStops().get(0).getShipmentId());
        assertEquals(7, command.getStops().get(0).getExpectedShipmentVersion());
        assertEquals(Long.valueOf(11), command.getStops().get(1).getShipmentId());
    }
    @Test public void emptyStopDraftIsAllowedButDuplicatesAndBrokenColumnsAreRejected() {
        DispatchForm empty = plan(); empty.rows(5); assertTrue(DispatchInputs.plan(empty).getStops().isEmpty());
        final DispatchForm duplicate = plan(); duplicate.setShipmentChoice(new String[] {"1:0", "1:0"});
        duplicate.setStopNote(new String[] {"", ""});
        invalid(new Runnable() { public void run() { DispatchInputs.plan(duplicate); } });
        final DispatchForm mismatch = plan(); mismatch.setShipmentChoice(new String[] {"1:0"}); mismatch.setStopNote(new String[0]);
        invalid(new Runnable() { public void run() { DispatchInputs.plan(mismatch); } });
    }
    @Test public void shipmentChoiceIdsAndVersionsAreNotCoerced() {
        for (final String value : new String[] {"1", "1:0:2", "0:0", "1:-1", "01:0", "1:2147483648", "9223372036854775808:0", "1:0 "}) {
            invalid(new Runnable() { public void run() { DispatchInputs.choice(value); } });
        }
    }
    @Test public void ownCarrierRequiresManualReferenceForEveryShipment() {
        final DispatchForm form = plan(); form.setCarrier("OWN"); form.setDispatchDate("2020-01-01");
        form.setShipmentId(new String[] {"1", "2"}); form.setTrackingReference(new String[] {"入力された自社番号", ""});
        invalid(new Runnable() { public void run() { DispatchInputs.confirmation(form); } });
        form.setTrackingReference(new String[] {"自社-1", "自社-2"});
        assertEquals("自社-2", DispatchInputs.confirmation(form).getTracking().get(1).getTrackingReference());
        assertEquals(2, DispatchInputs.confirmation(form).getTracking().size());
    }
    @Test public void preciseTimesPreserveTokyoHoursAndMilliseconds() {
        assertEquals("2020-02-29T12:34:00.000", PreciseDates.format(PreciseDates.parse("2020-02-29T12:34", "日時", false)));
        assertEquals("2020-02-29T12:34:56.100", PreciseDates.format(PreciseDates.parse("2020-02-29T12:34:56.1", "日時", false)));
        Date date = PreciseDates.parse("2020-02-29T12:34:56.123", "日時", false);
        assertEquals(Dates.parse("2020-02-29").getTime() + 45296123L, date.getTime());
        assertNull(PreciseDates.parse("", "日時", true));
    }
    @Test public void malformedOrNonexistentPreciseTimesAreRejected() {
        for (final String value : new String[] {"2020-02-30T12:00", "2020-01-01T24:00", "2020-01-01T12:60",
                "2020-01-01T12:00:60", "2020-01-01T12:00:00.1234", "2020-01-01", "2020-01-01T12:00Z"}) {
            invalid(new Runnable() { public void run() { PreciseDates.parse(value, "日時", false); } });
        }
    }
    @Test public void deliveryPreservesCursorRetryKeyAndManualEvidence() {
        DeliveryForm form = observation();
        DeliveryAttemptCommand command = DeliveryInputs.observation(form);
        assertNull(command.getExpectedLatestEventId()); assertEquals("stable-key", command.getRequestKey());
        assertEquals("報告会社", command.getReportingCompany()); assertEquals("手入力参照", command.getEvidenceReference());
        assertEquals("2020-01-02T12:00:01.123", PreciseDates.format(command.getAttemptAt()));
        form.setExpectedLatestEventId("42");
        assertEquals(Long.valueOf(42), DeliveryInputs.observation(form).getExpectedLatestEventId());
    }
    @Test public void outcomeReasonAndRescheduleFieldsAreExplicit() {
        final DeliveryForm form = observation(); form.setOutcome("FAILED"); form.setReason("");
        invalid(new Runnable() { public void run() { DeliveryInputs.observation(form); } });
        form.setOutcome("DELIVERED"); form.setNextAttemptDate("2020-01-03");
        invalid(new Runnable() { public void run() { DeliveryInputs.observation(form); } });
        form.setOutcome("RESCHEDULED"); form.setReason("再配送"); form.setNextAttemptDate("");
        invalid(new Runnable() { public void run() { DeliveryInputs.observation(form); } });
        form.setNextAttemptDate("2020-01-03"); assertNotNull(DeliveryInputs.observation(form).getNextAttemptDate());
    }
    @Test public void cutoffIsPreciseAndCannotClaimFutureKnowledge() {
        DeliveryForm form = new DeliveryForm(); form.setAsOfRecordedAt("2020-01-02T12:00:01.123");
        assertEquals("2020-01-02T12:00:01.123", PreciseDates.format(DeliveryInputs.search(form).getAsOfRecordedAt()));
        final DeliveryForm future = new DeliveryForm();
        future.setAsOfRecordedAt(PreciseDates.format(new Date(System.currentTimeMillis() + 86400000L)));
        invalid(new Runnable() { public void run() { DeliveryInputs.cutoff(future); } });
    }
    private DispatchForm plan() {
        DispatchForm form = new DispatchForm(); form.setWarehouseId("1"); form.setCarrier("OWN");
        form.setPlannedDispatchDate("2020-01-01"); return form;
    }
    private DeliveryForm observation() {
        DeliveryForm form = new DeliveryForm(); form.setShipmentId("5"); form.setRequestKey("stable-key");
        form.setAttemptAt("2020-01-02T12:00:01.123"); form.setOutcome("DELIVERED");
        form.setReportingCompany("報告会社"); form.setEvidenceReference("手入力参照"); return form;
    }
    private void invalid(Runnable action) {
        try { action.run(); fail("Expected strict input validation"); }
        catch (BusinessException expected) { assertNotNull(expected.getField()); }
    }
}
