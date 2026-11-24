package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.client.CuMedicalMonitor;
import org.junit.jupiter.api.Test;

import static io.github.safedecimal.decayingaftermath.medical.BodyPart.LEFT_ARM;
import static io.github.safedecimal.decayingaftermath.medical.DamageKind.*;
import static io.github.safedecimal.decayingaftermath.medical.TreatmentKind.*;
import static org.junit.jupiter.api.Assertions.*;

class BleedProfileTest {
    private static BleedProfile flow(BodyModel model) { return model.snapshot().part(LEFT_ARM).externalBleeding(); }

    private static BodyModel mixed() {
        var model = new BodyModel();
        model.damage(LEFT_ARM, 2, CUT);
        model.damage(LEFT_ARM, 5, CUT);
        model.damage(LEFT_ARM, 15, PUNCTURE);
        return model;
    }

    @Test void smallAndLargeInjuriesOnTheSamePartDoNotReplaceThePreviousTypes() {
        var model = mixed();
        assertEquals(.024f, flow(model).capillary(), 1e-6);
        assertEquals(.060f, flow(model).venous(), 1e-6);
        assertEquals(.135f, flow(model).arterial(), 1e-6);
        assertEquals(.219f, model.snapshot().part(LEFT_ARM).bleeding(), 1e-6);
        var capped = new BleedProfile(1, 1, .4f);
        assertEquals(1, capped.total());
        assertEquals(.4f, capped.arterial());
        assertEquals(capped, new BleedProfile(capped.capillary(), capped.venous(), capped.arterial()));
    }

    @Test void sameSeverityLosesMoreBloodAndClotsMoreSlowlyForHigherPressureTypes() {
        float previousLoss = 0, previousRemaining = 0;
        for (var type : new BleedProfile[]{new BleedProfile(.02f,0,0),
                new BleedProfile(0,.02f,0), new BleedProfile(0,0,.02f)}) {
            var clean = new BodyModel().snapshot(); var parts = clean.parts();
            parts[LEFT_ARM.ordinal()] = parts[LEFT_ARM.ordinal()].withExternalBleeding(type);
            var model = BodyModel.restore(clean.withParts(parts));
            int displayed = CuMedicalMonitor.bleedFlowMlPerMinute(model.snapshot(), false);
            model.tick(false, false);
            float loss = model.snapshot().survival().bloodLostLiters();
            assertEquals(displayed, loss * 1200 * 1000, 1);
            assertTrue(loss > previousLoss);
            assertTrue(flow(model).total() > previousRemaining);
            previousLoss = loss;
            previousRemaining = flow(model).total();
        }
    }

    @Test void dressingsSpendOneSharedCareBudgetAndDoNotTouchInternalBleeding() {
        var model = mixed();
        model.damage(LEFT_ARM, 9, BLUNT);
        var start = model.snapshot().part(LEFT_ARM);
        model.treat(LEFT_ARM, BANDAGE, 1);
        assertEquals(start.externalBleeding(), flow(model));
        model.tick(false, false);
        assertEquals(.000625f, start.bleeding() - flow(model).total(), .00015f);
        float internal = model.snapshot().part(LEFT_ARM).internalBleeding();
        model.treat(LEFT_ARM, PRESSURE_DRESSING, 1);
        assertEquals(BleedProfile.NONE, flow(model));
        assertTrue(internal > 0);
        assertEquals(internal, model.snapshot().part(LEFT_ARM).internalBleeding());
    }

    @Test void tourniquetKeepsEveryResidualTypeIncludingNewTraumaAndTotemRevival() {
        var model = mixed();
        var initial = flow(model);
        model.treat(LEFT_ARM, TOURNIQUET, 1);
        model.treat(LEFT_ARM, TOURNIQUET, 1); // Reapplying does not overwrite the stopped flow with zero.
        model.damage(LEFT_ARM, 2, CUT);
        assertEquals(BleedProfile.NONE, flow(model));
        model.tick(false, false);
        assertEquals(0, model.snapshot().survival().bloodLostLiters());
        var revived = model.reviveWithTotem();
        assertEquals(BleedProfile.NONE, flow(revived));
        assertTrue(revived.removeTourniquet(LEFT_ARM));
        assertTrue(model.removeTourniquet(LEFT_ARM));
        assertEquals(flow(model).scale(.25f), flow(revived));
        assertTrue(flow(model).capillary() > initial.capillary());
        assertTrue(flow(model).venous() > 0);
        assertTrue(flow(model).arterial() > 0);
    }
}
