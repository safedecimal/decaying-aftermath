package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class MedicalPresentationPolicyTest {
    private static BodySnapshot body(BodySnapshot.Mode mode, float condition, float blood,
            float oxygen, float pain, float bpm, float bleeding) {
        var parts = new BodySnapshot.PartSnapshot[6];
        Arrays.fill(parts, new BodySnapshot.PartSnapshot(1, bleeding, 0, false, false, false, false));
        return new BodySnapshot(condition, blood, oxygen, pain, 0, bpm, mode, 1, parts);
    }
    private static BodySnapshot healthy() { return body(BodySnapshot.Mode.CONSCIOUS, 1, 1, 1, 0, 72, 0); }
    @Test void healthyAndGenericLowConditionAreNotInventedCriticalPhysiology() {
        assertFalse(MedicalPresentationPolicy.criticalAlarm(healthy()));
        assertFalse(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, .2f, 1, 1, .8f, 140, 0)));
    }
    @Test void nativeOxygenThresholdIsStrictlyBelowFiftyPercent() {
        assertTrue(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, .6f, 1, .499f, 0, 72, 0)));
        assertFalse(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, .6f, 1, .5f, 0, 72, 0)));
    }
    @Test void lowBloodNeedsActiveBleedingForTheSourceCriticalBranch() {
        assertTrue(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, .3f, .29f, 1, 0, 72, .1f)));
        assertFalse(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, .3f, .29f, 1, 0, 72, 0)));
        assertFalse(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS, .3f, .3f, 1, 0, 72, .1f)));
    }
    @Test void painOrSedationFaintIsNotAutomaticallyAnEcgAlarmOrCriticalLoop() {
        var faint=body(BodySnapshot.Mode.UNCONSCIOUS,.6f,.9f,.9f,.95f,72,0);
        assertFalse(MedicalPresentationPolicy.criticalAlarm(faint));assertFalse(MedicalPresentationPolicy.criticalAmbience(faint));
        assertEquals("heartbeat",MedicalPresentationPolicy.beatSound(true,faint));assertNull(MedicalPresentationPolicy.beatSound(false,faint));
    }
    @Test void sourceSelectsOneRecordingPerBeatForInventoryAndWorld() {
        var critical=body(BodySnapshot.Mode.UNCONSCIOUS,.2f,.7f,.3f,.3f,72,0);
        assertEquals("heartbeat",MedicalPresentationPolicy.beatSound(true,healthy()));assertNull(MedicalPresentationPolicy.beatSound(false,healthy()));
        assertEquals("heartbeat_monitor",MedicalPresentationPolicy.beatSound(true,critical));assertEquals("heartbeat_heavy",MedicalPresentationPolicy.beatSound(false,critical));
    }
    @Test void lastStandingIsAnExplicitModCriticalStateNotAClaimOfNativeRevivalBalance() {
        assertTrue(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.LAST_STANDING,.15f,1,1,0,110,0)));
    }
    @Test void deadOrMissingPulseIsSilentAndCriticalAmbienceNeedsMoreThanFiveBpm() {
        assertNull(MedicalPresentationPolicy.beatSound(true,body(BodySnapshot.Mode.DEAD,0,0,0,0,72,0)));
        assertNull(MedicalPresentationPolicy.beatSound(true,body(BodySnapshot.Mode.UNCONSCIOUS,.2f,1,.3f,0,0,0)));
        assertFalse(MedicalPresentationPolicy.criticalAmbience(body(BodySnapshot.Mode.UNCONSCIOUS,.2f,1,.3f,0,5,0)));
        assertTrue(MedicalPresentationPolicy.criticalAmbience(body(BodySnapshot.Mode.UNCONSCIOUS,.2f,1,.3f,0,6,0)));
    }
    @Test void recoveryClearsCriticalBranch() {
        assertTrue(MedicalPresentationPolicy.criticalAlarm(body(BodySnapshot.Mode.CONSCIOUS,.3f,1,.4f,0,72,0)));
        assertFalse(MedicalPresentationPolicy.criticalAlarm(healthy()));
    }
    @Test void userInventoryOnlyPhysicalPulseContractIsPreserved() {
        assertTrue(MedicalPresentationPolicy.inventoryPulse(true,healthy()));assertFalse(MedicalPresentationPolicy.inventoryPulse(false,healthy()));
        assertFalse(MedicalPresentationPolicy.inventoryPulse(true,body(BodySnapshot.Mode.DEAD,0,0,0,0,0,0)));
    }
    @Test void brainDyingOwnsItsBoundSoundtrackInsteadOfTheWrongCriticalAmbience() {
        var dying=body(BodySnapshot.Mode.DYING,.1f,.2f,.3f,.8f,65,.1f);
        assertTrue(MedicalPresentationPolicy.criticalAlarm(dying));assertFalse(MedicalPresentationPolicy.criticalAmbience(dying));
        assertEquals("heartbeat_monitor",MedicalPresentationPolicy.beatSound(true,dying));
    }
    @Test void situationalDronesFollowAuthoritativePainAndEnergyOnly() {
        var parts = new BodySnapshot.PartSnapshot[6];
        Arrays.fill(parts, new BodySnapshot.PartSnapshot(1, 0, 0, false, false, false, false));
        var body = new BodySnapshot(.5f, 1, 1, .8f, 0, 72, BodySnapshot.Mode.CONSCIOUS,
                1, .2f, 1, parts);
        var volumes = MedicalSituationSoundState.volumes(body);
        assertTrue(volumes.pain() > 0); assertTrue(volumes.exhausted() > 0); assertTrue(volumes.tired() > 0);
    }
    @Test void situationalDronesStopForDyingAndDead() {
        var dying = body(BodySnapshot.Mode.DYING,.1f,.2f,.3f,.8f,65,.1f);
        var dead = body(BodySnapshot.Mode.DEAD,0,0,0,0,0,0);
        assertTrue(MedicalSituationSoundState.volumes(dying).dying() > 0);
        assertTrue(MedicalSituationSoundState.volumes(dead).silent());
    }
}
