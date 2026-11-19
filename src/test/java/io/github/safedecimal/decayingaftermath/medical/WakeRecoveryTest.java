package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode.*;

class WakeRecoveryTest {
    private BodyModel unconscious(float vital, float blood, float oxygen, float pain) {
        var parts = new BodySnapshot.PartSnapshot[6];
        for (int i = 0; i < 6; i++) parts[i] = new BodySnapshot.PartSnapshot(i < 2 ? vital : 1,
                0, pain, false, false, false, false);
        return BodyModel.restore(new BodySnapshot(.3f, blood, oxygen, pain, 0, 80,
                UNCONSCIOUS, 0, 1, .5f, parts));
    }
    private void ticks(BodyModel body, int count) { for (int i = 0; i < count; i++) body.tick(false, false); }

    @Test void safeFaintRecoversAfterExactlySixServerSeconds() {
        var body = unconscious(1, 1, .4f, 0);
        ticks(body, 119); assertEquals(UNCONSCIOUS, body.snapshot().mode());
        assertEquals(119f / BodyModel.WAKE_UP_TICKS, body.snapshot().consciousnessProgress());
        ticks(body, 1); assertEquals(CONSCIOUS, body.snapshot().mode());
    }
    @Test void earlyAttemptNeedsStableTicksAndDoesNotHealAnything() {
        var body = unconscious(1, 1, 1, 0);
        ticks(body, WakeRecovery.EARLY_STAND_TICKS - 1);
        assertFalse(body.tryStandUp());
        ticks(body, 1);
        var before = body.snapshot();
        assertTrue(body.tryStandUp());
        var after = body.snapshot();
        assertEquals(CONSCIOUS, after.mode());
        assertEquals(before.blood(), after.blood());
        assertEquals(before.oxygen(), after.oxygen());
        assertEquals(before.part(BodyPart.HEAD), after.part(BodyPart.HEAD));
        assertEquals(before.energy() - WakeRecovery.STAND_ENERGY_COST, after.energy(), 1e-6f);
        assertFalse(body.tryStandUp());
    }

    @Test void earlyAttemptCannotSkipPoorOxygenOrSevereInjury() {
        for (var body : new BodyModel[]{unconscious(.2f, 1, 1, 0), unconscious(1, 1, .36f, 0)}) {
            ticks(body, WakeRecovery.EARLY_STAND_TICKS);
            assertFalse(body.tryStandUp());
            assertEquals(UNCONSCIOUS, body.snapshot().mode());
        }
    }

    @Test void earlyAttemptNeedsAtLeastOneWeightBearingLeg() {
        var parts = unconscious(1, 1, 1, 0).snapshot().parts();
        var healthy = parts[BodyPart.LEFT_LEG.ordinal()];
        var fractured = new BodySnapshot.PartSnapshot(.8f, 0, 0, true, false, false, false);
        parts[BodyPart.LEFT_LEG.ordinal()] = fractured;
        parts[BodyPart.RIGHT_LEG.ordinal()] = fractured;
        assertFalse(WakeRecovery.canStandEarly(new BodySnapshot(.3f, 1, 1, 0, 0, 80, UNCONSCIOUS, .5f, 1, .8f, parts)));
        parts[BodyPart.RIGHT_LEG.ordinal()] = healthy;
        assertTrue(WakeRecovery.canStandEarly(new BodySnapshot(.3f, 1, 1, 0, 0, 80, UNCONSCIOUS, .5f, 1, .8f, parts)));
    }

    @Test void persistentPainNoLongerResetsProgressOrImmediatelyRefaints() {
        var body = unconscious(1, 1, 1, .95f);
        ticks(body, 60); assertEquals(.5f, body.snapshot().consciousnessProgress());
        ticks(body, 60); assertTrue(body.snapshot().pain() > .85f);
        for (int i = 0; i < 600; i++) { body.tick(false, false); assertEquals(CONSCIOUS, body.snapshot().mode()); }
    }
    @Test void persistentNoncriticalVitalInjuryDoesNotRequireTissueHealingToWake() {
        var body = unconscious(.2f, 1, 1, 0); ticks(body, 120);
        assertEquals(.2f, body.snapshot().part(BodyPart.HEAD).integrity());
        for (int i = 0; i < 600; i++) { body.tick(false, false); assertEquals(CONSCIOUS, body.snapshot().mode()); }
    }
    @Test void severeBloodLossBlocksRecovery() {
        var body = unconscious(1, .25f, 1, 0); ticks(body, 120);
        assertEquals(UNCONSCIOUS, body.snapshot().mode()); assertEquals(0, body.snapshot().consciousnessProgress());
        assertEquals(WakeRecovery.Blocker.BLOOD, WakeRecovery.blocker(body.snapshot()));
        assertFalse(body.tryStandUp());
    }
    @Test void unsafeOxygenPausesInsteadOfErasingEarnedProgress() {
        var body = unconscious(1, 1, .7f, 0); ticks(body, 60);
        body.reduceOxygen(.45f);
        var sealed = new BodyEnvironment(20, .25f, false, false, false, false, false, 0, 32, false, false);
        for (int i = 0; i < 10; i++) body.tick(sealed);
        assertEquals(WakeRecovery.Blocker.OXYGEN, WakeRecovery.blocker(body.snapshot()));
        assertEquals(.5f, body.snapshot().consciousnessProgress());
        ticks(body, 120); assertEquals(CONSCIOUS, body.snapshot().mode());
    }
    @Test void repeatedReadoutsAndNonTickEvaluationCannotAdvanceOrResetTimer() {
        var body = unconscious(1, 1, 1, .95f); ticks(body, 60);
        for (int i = 0; i < 100; i++) { body.snapshot(); body.treat(BodyPart.HEAD, TreatmentKind.ANTISEPTIC, .01f); }
        assertEquals(.5f, body.snapshot().consciousnessProgress());
    }
    @Test void saveLoadPreservesHalfFinishedCountdown() {
        var body = unconscious(.2f, 1, 1, 0); ticks(body, 60);
        body = BodyModel.restoreState(body.saveState()); assertEquals(.5f, body.snapshot().consciousnessProgress());
        ticks(body, 59); assertEquals(UNCONSCIOUS, body.snapshot().mode());
        ticks(body, 1); assertEquals(CONSCIOUS, body.snapshot().mode());
    }
    @Test void saveLoadPreservesProtectionAgainstTheSameUnchangedInjury() {
        var body = unconscious(.2f, 1, 1, 0); ticks(body, 120); assertTrue(body.saveState().faintRecovered());
        body = BodyModel.restoreState(body.saveState()); ticks(body, 600);
        assertEquals(CONSCIOUS, body.snapshot().mode());
    }
    @Test void newInjuryCanCauseANewFaint() {
        var body = unconscious(.2f, 1, 1, 0); ticks(body, 120);
        body.damage(BodyPart.LEFT_ARM, 1); assertEquals(UNCONSCIOUS, body.snapshot().mode());
        assertEquals(0, body.snapshot().consciousnessProgress());
    }
    @Test void protectionNeverOverridesSevereHypoxia() {
        var body = unconscious(.2f, 1, 1, 0); ticks(body, 120);
        body.reduceOxygen(.75f); assertEquals(UNCONSCIOUS, body.snapshot().mode());
        assertEquals(WakeRecovery.Blocker.OXYGEN, WakeRecovery.blocker(body.snapshot()));
    }
    @Test void clearingTheTriggerRearmsFainting() {
        var body = unconscious(1, 1, .4f, 0); ticks(body, 121);
        assertFalse(body.saveState().faintRecovered());
        body.reduceOxygen(.1f); assertEquals(UNCONSCIOUS, body.snapshot().mode());
    }
    @Test void allBlockersAndStableTickProgressAreDeterministic() {
        assertEquals(WakeRecovery.Blocker.NONE, WakeRecovery.blocker(.13f, .33f, .31f, .21f));
        assertEquals(WakeRecovery.Blocker.INJURY, WakeRecovery.blocker(.12f, 1, 1, 1));
        assertEquals(WakeRecovery.Blocker.CONSCIOUSNESS, WakeRecovery.blocker(1, 1, 1, .2f));
        var body = unconscious(1, 1, 1, 0);
        for (int tick = 0; tick < 120; tick++) {
            assertEquals((float) tick / BodyModel.WAKE_UP_TICKS, body.snapshot().consciousnessProgress());
            body.tick(false, false);
        }
    }
}
