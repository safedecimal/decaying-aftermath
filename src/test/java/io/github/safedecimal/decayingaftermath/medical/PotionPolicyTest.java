package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PotionPolicyTest {
    @Test void medicinalEffectsRequireBodyApplication() {
        assertTrue(PotionPolicy.isMedical(true, false));
        assertTrue(PotionPolicy.isMedical(false, true));
        assertFalse(PotionPolicy.isMedical(false, false));
        assertFalse(PotionPolicy.permitsHealing(true, false));
        assertTrue(PotionPolicy.permitsHealing(true, true));
    }
    @Test void thoughtOccursOnlyAfterCompletedMedicinalDrink() {
        assertFalse(PotionPolicy.emitsThought(true, false));
        assertFalse(PotionPolicy.emitsThought(false, true));
        assertTrue(PotionPolicy.emitsThought(true, true));
        assertEquals("It's seems didn't help..", PotionPolicy.WASTED_HEALING_THOUGHT);
    }
}
