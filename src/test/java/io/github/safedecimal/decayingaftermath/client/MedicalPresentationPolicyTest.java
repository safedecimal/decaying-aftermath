package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class MedicalPresentationPolicyTest {
    private static BodySnapshot body(BodySnapshot.Mode mode, float condition, float bpm) {
        var parts = new BodySnapshot.PartSnapshot[6];
        Arrays.fill(parts, new BodySnapshot.PartSnapshot(1, 0, 0, false, false, false, false));
        return new BodySnapshot(condition, 1, 1, 0, 0, bpm, mode, 1, parts);
    }

    @Test void healthyAndMinorInjuriesAreSilent() {
        assertFalse(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, 1, 72)));
        assertFalse(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, .8f, 140)));
    }
    @Test void criticalThresholdIsExplicit() {
        assertTrue(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, .35f, 72)));
        assertFalse(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, .351f, 72)));
    }
    @Test void unconsciousAndLastStandingAlarmEvenDuringRecovery() {
        assertTrue(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.UNCONSCIOUS, .6f, 72)));
        assertTrue(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.LAST_STANDING, .6f, 72)));
    }
    @Test void deadOrMissingPulseCannotStartBeatSound() {
        assertFalse(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.DEAD, 0, 72)));
        assertFalse(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.UNCONSCIOUS, .2f, 0)));
    }
    @Test void recoveryStopsAlarm() {
        assertTrue(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, .3f, 72)));
        assertFalse(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, .5f, 72)));
    }
    @Test void healthyInventoryHasPhysicalPulseWithoutEcgAlarm() {
        var healthy=body(BodySnapshot.Mode.CONSCIOUS,1,72);
        assertTrue(MedicalPresentationPolicy.inventoryPulse(true,healthy));
        assertFalse(MedicalPresentationPolicy.criticalAlarm(healthy));
    }
    @Test void physicalPulseIsNeverEnabledOutsideInventory() {
        assertFalse(MedicalPresentationPolicy.inventoryPulse(false,body(BodySnapshot.Mode.CONSCIOUS,1,72)));
        assertFalse(MedicalPresentationPolicy.inventoryPulse(false,body(BodySnapshot.Mode.UNCONSCIOUS,.2f,72)));
    }
    @Test void deadOrZeroBpmInventoryRemainsSilent() {
        assertFalse(MedicalPresentationPolicy.inventoryPulse(true,body(BodySnapshot.Mode.DEAD,0,72)));
        assertFalse(MedicalPresentationPolicy.inventoryPulse(true,body(BodySnapshot.Mode.CONSCIOUS,1,0)));
    }
}
