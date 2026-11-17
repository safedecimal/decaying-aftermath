package io.github.safedecimal.decayingaftermath.compat;
import static org.junit.jupiter.api.Assertions.*;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;
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
}
