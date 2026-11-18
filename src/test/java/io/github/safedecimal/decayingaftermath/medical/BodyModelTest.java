package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.safedecimal.decayingaftermath.medical.BodyPart.*;
import static io.github.safedecimal.decayingaftermath.medical.TreatmentKind.*;
import static io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode.*;

class BodyModelTest {
    private static void ticks(BodyModel model, int count) {
        for (int i = 0; i < count; i++) model.tick(false, false);
    }

    @Test void healthyBodyStartsConsciousAndIndependentOfAnyVanillaHp() {
        BodyModel model = new BodyModel();
        ticks(model, 1200);
        assertEquals(1, model.snapshot().condition());
        assertEquals(1, model.snapshot().blood());
        assertEquals(CONSCIOUS, model.snapshot().mode());
        assertEquals(72, model.snapshot().bpm(), .01);
    }

    @Test void localizedDamageDoesNotHitOtherParts() {
        BodyModel model = new BodyModel();
        model.damage(LEFT_ARM, 4);
        assertEquals(.88, model.snapshot().part(LEFT_ARM).integrity(), .0001);
        assertTrue(model.snapshot().part(LEFT_ARM).bleeding() > 0);
        for (BodyPart part : BodyPart.values()) if (part != LEFT_ARM)
            assertEquals(1, model.snapshot().part(part).integrity());
    }

    @Test void heavyFallPrioritizesLegsButAlsoHitsTheRestOfTheBody() {
        BodyModel model=new BodyModel();model.fall(20);var b=model.snapshot();
        assertEquals(.745f,b.part(LEFT_LEG).integrity(),.0001f);
        assertEquals(.745f,b.part(RIGHT_LEG).integrity(),.0001f);
        assertTrue(b.part(LEFT_LEG).fractured());assertTrue(b.part(RIGHT_LEG).fractured());
        assertTrue(b.part(LEFT_LEG).bleeding()>0);assertTrue(b.part(RIGHT_LEG).bleeding()>0);
        assertTrue(b.part(HEAD).integrity()<1);assertTrue(b.part(TORSO).integrity()<1);
        assertTrue(b.part(LEFT_ARM).integrity()<1);assertTrue(b.part(RIGHT_ARM).integrity()<1);
        assertTrue(b.part(LEFT_ARM).integrity()>b.part(TORSO).integrity());
    }

    @Test void untreatedTissueDoesNotNaturallyRegenerate() {
        BodyModel model = new BodyModel();
        model.damage(LEFT_ARM, 4);
        float before = model.snapshot().part(LEFT_ARM).integrity();
        ticks(model, 1200);
        assertEquals(before, model.snapshot().part(LEFT_ARM).integrity());
        assertTrue(model.snapshot().blood() < 1);
    }

    @Test void bandageStopsSmallBleedAndEnablesSlowWoundCareNotInstantHp() {
        BodyModel model = new BodyModel();
        model.damage(LEFT_ARM, 4);
        float before = model.snapshot().part(LEFT_ARM).integrity();
        assertTrue(model.treat(LEFT_ARM, BANDAGE, 1));
        assertTrue(model.snapshot().part(LEFT_ARM).bleeding() > 0); // Care is queued, not instant.
        assertTrue(model.treat(LEFT_ARM, BANDAGE, 1));
        assertEquals(before, model.snapshot().part(LEFT_ARM).integrity());
        ticks(model, 1200);
        assertTrue(model.snapshot().part(LEFT_ARM).integrity() > before);
        assertTrue(model.treat(HEAD, BANDAGE, 1));
    }

    @Test void splintReducesPainWithoutRepairingFractureOrTissue() {
        BodyModel model = new BodyModel();
        model.damage(LEFT_LEG, 10);
        var before = model.snapshot().part(LEFT_LEG);
        assertTrue(model.treat(LEFT_LEG, SPLINT, 1));
        var after = model.snapshot().part(LEFT_LEG);
        assertTrue(after.fractured()); assertTrue(after.splinted());
        assertEquals(before.integrity(), after.integrity());
        assertTrue(after.pain() < before.pain());
        assertTrue(model.treat(HEAD, SPLINT, 1));
        assertTrue(model.treat(RIGHT_LEG, SPLINT, 1));
    }

    @Test void healingAndRegenerationPotionsTreatOnlySelectedTissue() {
        BodyModel model = new BodyModel();
        model.damage(LEFT_ARM, 8); model.damage(RIGHT_ARM, 8);
        float blood = model.snapshot().blood();
        assertTrue(model.treat(LEFT_ARM, HEALING_POTION, 1));
        assertEquals(.76, model.snapshot().part(LEFT_ARM).integrity(),.0001);
        assertEquals(.76, model.snapshot().part(RIGHT_ARM).integrity(), .0001);
        assertEquals(blood, model.snapshot().blood());
        assertTrue(model.snapshot().part(LEFT_ARM).fractured());
        ticks(model,400);
        assertTrue(model.snapshot().part(LEFT_ARM).integrity()>.76f);
        float before = model.snapshot().part(RIGHT_ARM).integrity();
        assertTrue(model.treat(RIGHT_ARM, REGENERATION_POTION, 1));
        assertEquals(before, model.snapshot().part(RIGHT_ARM).integrity());
        ticks(model, 100);
        assertTrue(model.snapshot().part(RIGHT_ARM).integrity() > before);
    }

    @Test void fentanylIsCumulativeAnalgesiaAndDoesNotHealDamage() {
        BodyModel model = new BodyModel();
        model.damage(TORSO, 8);
        float pain = model.snapshot().pain(); float tissue = model.snapshot().part(TORSO).integrity();
        assertTrue(model.treat(LEFT_ARM, FENTANYL, .5f));
        assertTrue(model.snapshot().pain() < pain);
        assertEquals(.325, model.snapshot().drug(), .0001);
        assertTrue(model.treat(LEFT_ARM, FENTANYL, .5f));
        assertEquals(.65, model.snapshot().drug(), .0001);
        assertEquals(tissue, model.snapshot().part(TORSO).integrity());
    }

    @Test void suppressedBreathingCanCauseUnconsciousnessThenRecovery() {
        BodyModel model = new BodyModel();
        model.reduceOxygen(.6f);
        assertEquals(UNCONSCIOUS, model.snapshot().mode());
        assertEquals(0, model.snapshot().consciousnessProgress());
        ticks(model, 1200);
        assertEquals(CONSCIOUS, model.snapshot().mode());
        assertEquals(1, model.snapshot().oxygen());
    }

    @Test void VeryCriticalTraumaEntersLastStandingBeforeDeath() {
        BodyModel model = new BodyModel();
        model.damage(HEAD, 34);
        assertEquals(LAST_STANDING, model.snapshot().mode());
        assertEquals(1, model.snapshot().consciousnessProgress());
        ticks(model, BodyModel.LAST_STANDING_TICKS - 1);
        assertEquals(LAST_STANDING, model.snapshot().mode());
        assertTrue(model.snapshot().consciousnessProgress() > 0);
        model.tick(false, false);
        assertNotEquals(DEAD, model.snapshot().mode());
        assertTrue(model.snapshot().bpm() > 0);
        assertTrue(model.treat(HEAD, HEALING_POTION, 1));
    }

    @Test void urgentTreatmentCanExitLastStandingButCannotResetItByStateHopping() {
        BodyModel model = new BodyModel();
        model.damage(HEAD, 31);
        assertEquals(LAST_STANDING, model.snapshot().mode());
        assertTrue(model.treat(HEAD, HEALING_POTION, 1));
        ticks(model, BodyModel.LAST_STANDING_TICKS + BodyModel.WAKE_UP_TICKS);
        assertEquals(CONSCIOUS, model.snapshot().mode());
        model.damage(HEAD, 40);
        assertEquals(DYING, model.snapshot().mode());
    }

    @Test void repeatedExcessiveInjectionCanProduceFatalGameOverdose() {
        BodyModel model = new BodyModel();
        for (int i = 0; i < 3; i++) assertTrue(model.treat(LEFT_ARM, FENTANYL, 1));
        boolean fainted = false, lastStanding = false;
        for (int i = 0; i < 6000; i++) {
            model.tick(false, false);
            fainted |= model.snapshot().mode() == UNCONSCIOUS;
            lastStanding |= model.snapshot().mode() == LAST_STANDING;
        }
        assertTrue(fainted); assertTrue(lastStanding);
        assertEquals(DEAD, model.snapshot().mode());
        assertEquals(1, model.snapshot().part(LEFT_ARM).integrity());
    }

    @Test void savedStatePreservesLastStandingAndCareTimersAcrossReload() {
        BodyModel model = new BodyModel();
        model.damage(HEAD, 34); ticks(model, 100);
        BodyModel restored = BodyModel.restoreState(model.saveState());
        assertEquals(model.snapshot().consciousnessProgress(), restored.snapshot().consciousnessProgress());
        ticks(restored, 500);
        assertNotEquals(DEAD, restored.snapshot().mode());
        BodyModel care = new BodyModel(); care.damage(LEFT_ARM, 4); care.treat(LEFT_ARM, BANDAGE, 1);
        ticks(care, 100);
        var state = care.saveState();
        int expected = state.careTicks()[LEFT_ARM.ordinal()];
        int[] external = state.careTicks(); external[LEFT_ARM.ordinal()] = 0;
        assertEquals(expected, state.careTicks()[LEFT_ARM.ordinal()]);
        assertEquals(expected, BodyModel.restoreState(state).saveState().careTicks()[LEFT_ARM.ordinal()]);
    }

    @Test void tourniquetIsTemporaryAndHasConsequencesIfLeftOn() {
        BodyModel model = new BodyModel(); model.damage(LEFT_LEG, 12);
        assertTrue(model.treat(HEAD, TOURNIQUET, 1));
        assertTrue(model.treat(LEFT_LEG, TOURNIQUET, 1));
        float before = model.snapshot().part(LEFT_LEG).integrity();
        ticks(model, 1201);
        assertEquals(0, model.snapshot().part(LEFT_LEG).bleeding());
        assertTrue(model.snapshot().part(LEFT_LEG).integrity() < before);
        assertTrue(model.removeTourniquet(LEFT_LEG));
        assertTrue(model.snapshot().part(LEFT_LEG).bleeding() > 0);
    }

    @Test void snapshotsDefensivelyCopyPartsAndRejectNonFiniteState() {
        BodyModel model = new BodyModel(); var snapshot = model.snapshot();
        var parts = snapshot.parts(); parts[0] = null;
        assertNotNull(snapshot.part(HEAD));
        assertThrows(IllegalArgumentException.class, () -> new BodySnapshot(Float.NaN, 1, 1, 0, 0,
                72, CONSCIOUS, 1, snapshot.parts()));
        assertThrows(IllegalArgumentException.class, () -> new BodySnapshot.PartSnapshot(1, Float.POSITIVE_INFINITY,
                0, false, false, false, false));
        model.damage(HEAD, Float.NaN);
        assertFalse(model.treat(HEAD, FENTANYL, Float.POSITIVE_INFINITY));
        assertEquals(1, model.snapshot().condition());
    }
}
