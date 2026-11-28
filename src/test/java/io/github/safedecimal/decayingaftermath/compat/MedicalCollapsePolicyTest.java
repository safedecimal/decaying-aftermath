package io.github.safedecimal.decayingaftermath.compat;
import static org.junit.jupiter.api.Assertions.*;
import io.github.safedecimal.decayingaftermath.medical.BodyModel;
import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;
import io.github.safedecimal.decayingaftermath.medical.DamageKind;
import io.github.safedecimal.decayingaftermath.medical.SurvivalSnapshot;
import io.github.safedecimal.decayingaftermath.medical.TreatmentKind;
import org.junit.jupiter.api.Test;
class MedicalCollapsePolicyTest {
    @Test void onlyMedicalUnconsciousnessStartsLiveRagdoll() {
        for (Mode mode:Mode.values()) assertEquals(mode==Mode.UNCONSCIOUS,MedicalCollapsePolicy.collapsed(mode,true,false,false));
    }
    @Test void sleepingIsNeverACollapseEvenWhenMedicalModeIsUnconscious() {
        for (Mode mode:Mode.values()) assertFalse(MedicalCollapsePolicy.collapsed(mode,true,true,false));
    }
    @Test void deathAndExemptPlayersNeverStartLiveRagdolls() {
        assertFalse(MedicalCollapsePolicy.collapsed(Mode.UNCONSCIOUS,false,false,false));
        assertFalse(MedicalCollapsePolicy.collapsed(Mode.UNCONSCIOUS,true,false,true));
    }

    @Test void incapacitatedLegsCollapseIntoRagdollWhileWalkableSevereInjuryUsesInjuredPosture() {
        var healthy = new BodyModel();
        assertEquals(MedicalCollapsePolicy.Posture.NORMAL,
                MedicalCollapsePolicy.posture(healthy.snapshot(), true, false, false));

        // One fractured leg -> severely injured, can still limp (injured.json animations)
        var oneLeg = new BodyModel();
        oneLeg.damage(BodyPart.LEFT_LEG, 9f, DamageKind.BLUNT);
        assertEquals(MedicalCollapsePolicy.Posture.INJURED,
                MedicalCollapsePolicy.posture(oneLeg.snapshot(), true, false, false));
        assertFalse(MedicalCollapsePolicy.cannotWalk(oneLeg.snapshot()));

        // Both legs fractured without splints -> cannot walk -> COLLAPSED (ragdoll)
        var bothLegs = new BodyModel();
        bothLegs.damage(BodyPart.LEFT_LEG, 9f, DamageKind.BLUNT);
        bothLegs.damage(BodyPart.RIGHT_LEG, 9f, DamageKind.BLUNT);
        assertTrue(MedicalCollapsePolicy.cannotWalk(bothLegs.snapshot()));
        assertEquals(MedicalCollapsePolicy.Posture.COLLAPSED,
                MedicalCollapsePolicy.posture(bothLegs.snapshot(), true, false, false));

        // Splinting one leg allows limping again (INJURED posture instead of ragdoll)
        bothLegs.treat(BodyPart.LEFT_LEG, TreatmentKind.SPLINT, 1f);
        assertFalse(MedicalCollapsePolicy.cannotWalk(bothLegs.snapshot()));
        assertEquals(MedicalCollapsePolicy.Posture.INJURED,
                MedicalCollapsePolicy.posture(bothLegs.snapshot(), true, false, false));

        // Crushing encumbrance collapses into ragdoll
        var base = new BodyModel().snapshot();
        var crushedSurvival = new SurvivalSnapshot(1, 1, 37, 0, 0, 1, 1, 0, 0, 0, 0, 0, 1, 2.6f, 0);
        var crushed = new BodySnapshot(1, 1, 1, 0, 0, 72, Mode.CONSCIOUS, 1, 1, 1, base.parts(), crushedSurvival);
        assertTrue(MedicalCollapsePolicy.cannotWalk(crushed));
        assertEquals(MedicalCollapsePolicy.Posture.COLLAPSED,
                MedicalCollapsePolicy.posture(crushed, true, false, false));
    }
}
