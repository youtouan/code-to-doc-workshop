package jp.co.tsubame.wholesale.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class DispatchRulesTest {
    @Test public void stopOrderIsPreservedAndDuplicatesRejected() {
        final DispatchStopCommand first = stop(11L);
        DispatchStopCommand second = stop(9L);
        assertEquals(Arrays.asList(11L, 9L), DispatchRules.shipmentIds(Arrays.asList(first, second)));
        expect("dispatch.duplicateShipment", new Runnable() {
            @Override public void run() { DispatchRules.shipmentIds(Arrays.asList(first, first)); }
        });
    }
    @Test public void manifestsAreBoundedButEmptyDraftsPermitRemovingAllStops() {
        final List<DispatchStopCommand> stops = new ArrayList<DispatchStopCommand>();
        assertTrue(DispatchRules.shipmentIds(stops).isEmpty());
        for (long id = 1; id <= 100; id++) { stops.add(stop(id)); }
        assertEquals(100, DispatchRules.shipmentIds(stops).size());
        stops.add(stop(101L));
        expect("dispatch.stopLimit", new Runnable() {
            @Override public void run() { DispatchRules.shipmentIds(stops); }
        });
    }
    @Test public void trackingMustBeProvidedRatherThanInventedForAnyCarrier() {
        final DispatchTrackingCommand tracking = new DispatchTrackingCommand();
        tracking.setShipmentId(11L);
        tracking.setTrackingReference(" ");
        expect("validation.required", new Runnable() {
            @Override public void run() { DispatchRules.tracking(Arrays.asList(tracking)); }
        });
        tracking.setTrackingReference(" INTERNAL-TEST-11 ");
        assertEquals("INTERNAL-TEST-11", DispatchRules.tracking(Arrays.asList(tracking)).get(11L));
        expect("dispatch.tracking", new Runnable() {
            @Override public void run() { DispatchRules.tracking(Arrays.asList(tracking, tracking)); }
        });
    }
    @Test public void carrierAndPlanningDateHaveExplicitBounds() {
        assertEquals("OWN", DispatchRules.carrier("OWN"));
        assertEquals("PARCEL", DispatchRules.carrier("PARCEL"));
        assertEquals("FREIGHT", DispatchRules.carrier("FREIGHT"));
        expect("dispatch.carrier", new Runnable() {
            @Override public void run() { DispatchRules.carrier("AUTOMATED"); }
        });
        for (final int offset : new int[] {-31, 91}) {
            expect("dispatch.plannedDate", new Runnable() {
                @Override public void run() { DispatchRules.plannedDate(Dates.addDays(Dates.today(), offset)); }
            });
        }
        Date today = Dates.today();
        assertEquals(today, DispatchRules.plannedDate(today));
    }
    private DispatchStopCommand stop(Long id) {
        DispatchStopCommand command = new DispatchStopCommand();
        command.setShipmentId(id);
        return command;
    }
    private void expect(String code, Runnable action) {
        try { action.run(); fail(code); } catch (BusinessException failure) { assertEquals(code, failure.getCode()); }
    }
}
