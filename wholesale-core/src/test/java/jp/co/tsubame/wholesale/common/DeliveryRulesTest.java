package jp.co.tsubame.wholesale.common;

import java.util.Date;
import jp.co.tsubame.wholesale.entity.DeliveryAttempt;
import jp.co.tsubame.wholesale.entity.Shipment;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeliveryRulesTest {
    @Test public void observationSeparatesActualTimestampFromTokyoBusinessDate() {
        DeliveryAttemptCommand command = command("FAILED");
        DeliveryAttempt event = DeliveryRules.observation(shipment(), command);
        assertEquals(command.getAttemptAt(), event.getAttemptAt());
        assertEquals(Dates.day(command.getAttemptAt()), event.getBusinessDate());
        assertEquals("Asia/Tokyo", event.getBusinessTimeZone());
        assertEquals("MANUAL_UNVERIFIED_REPORT", event.getEvidenceBasis());
    }
    @Test public void failureNeedsReasonAndRescheduleNeedsLaterBoundedBusinessDate() {
        final DeliveryAttemptCommand missing = command("FAILED");
        missing.setReason("");
        expect("validation.required", new Runnable() {
            @Override public void run() { DeliveryRules.observation(shipment(), missing); }
        });
        final DeliveryAttemptCommand reschedule = command("RESCHEDULED");
        reschedule.setNextAttemptDate(Dates.day(reschedule.getAttemptAt()));
        expect("delivery.nextDate", new Runnable() {
            @Override public void run() { DeliveryRules.observation(shipment(), reschedule); }
        });
        reschedule.setNextAttemptDate(Dates.addDays(reschedule.getAttemptAt(), 91));
        expect("delivery.nextDate", new Runnable() {
            @Override public void run() { DeliveryRules.observation(shipment(), reschedule); }
        });
        reschedule.setNextAttemptDate(Dates.addDays(reschedule.getAttemptAt(), 1));
        assertNotNull(DeliveryRules.observation(shipment(), reschedule).getNextAttemptDate());
    }
    @Test public void attemptsCannotPrecedeShipmentOrDescribeFutureEvents() {
        final DeliveryAttemptCommand command = command("DELIVERED");
        command.setAttemptAt(Dates.addDays(Dates.today(), -3));
        expect("delivery.attemptAt", new Runnable() {
            @Override public void run() { DeliveryRules.observation(shipment(), command); }
        });
        command.setAttemptAt(new Date(System.currentTimeMillis() + 60000));
        expect("delivery.attemptAt", new Runnable() {
            @Override public void run() { DeliveryRules.observation(shipment(), command); }
        });
    }
    @Test public void optimisticCursorDistinguishesNoHistoryFromStaleHistory() {
        DeliveryRules.cursor(null, null);
        DeliveryRules.cursor(10L, 10L);
        expect("delivery.concurrent", new Runnable() {
            @Override public void run() { DeliveryRules.cursor(10L, null); }
        });
        expect("delivery.concurrent", new Runnable() {
            @Override public void run() { DeliveryRules.cursor(10L, 9L); }
        });
    }
    @Test public void fingerprintIncludesOutcomeCorrectionTargetAndEvidence() {
        DeliveryAttempt event = DeliveryRules.observation(shipment(), command("FAILED"));
        event.setEventType("ATTEMPT");
        String original = DeliveryRules.fingerprint(event);
        event.setEventType("CORRECTION");
        event.setSupersedesId(8L);
        event.setCorrectionReason("Corrected manual evidence");
        assertFalse(original.equals(DeliveryRules.fingerprint(event)));
        String corrected = DeliveryRules.fingerprint(event);
        event.setEvidenceReference("DIFFERENT-REFERENCE");
        assertFalse(corrected.equals(DeliveryRules.fingerprint(event)));
    }
    private Shipment shipment() {
        Shipment shipment = new Shipment();
        shipment.setId(1L);
        shipment.setShippedDate(Dates.addDays(Dates.today(), -2));
        return shipment;
    }
    private DeliveryAttemptCommand command(String outcome) {
        DeliveryAttemptCommand command = new DeliveryAttemptCommand();
        command.setAttemptAt(new Date(Dates.addDays(Dates.today(), -1).getTime() + 3600000L));
        command.setOutcome(outcome);
        command.setReportingCompany("SYNTHETIC-CARRIER");
        command.setEvidenceReference("TEST-MANUAL-REPORT");
        command.setReason("Synthetic failure reason");
        return command;
    }
    private void expect(String code, Runnable action) {
        try { action.run(); fail(code); } catch (BusinessException failure) { assertEquals(code, failure.getCode()); }
    }
}
