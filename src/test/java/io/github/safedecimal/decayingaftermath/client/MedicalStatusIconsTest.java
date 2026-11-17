package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalStatusIconsTest {
    private BodySnapshot.PartSnapshot state(float bleed,boolean dressed) {return new BodySnapshot.PartSnapshot(1,bleed,0,false,dressed,false,false);}
    @Test void noBleedingMeansNoItemOverlay() {assertNull(MedicalStatusIcons.bleeding(state(0,true)));}
    @Test void sourceBleedingThresholdsChooseNativeSprites() {
        assertEquals("bleeding_light",MedicalStatusIcons.bleeding(state(.01f,false)).name());assertEquals("bleeding_medium",MedicalStatusIcons.bleeding(state(.08f,false)).name());assertEquals("bleeding_heavy",MedicalStatusIcons.bleeding(state(.0801f,false)).name());assertEquals("bleeding_arterial",MedicalStatusIcons.bleeding(state(.21f,false)).name());
    }
    @Test void dressedBleedingIsAStatusNotAnInventoryItem() {
        assertEquals("bleeding_covered_heavy",MedicalStatusIcons.bleeding(state(.12f,true)).name());
    }
    @Test void exactSpriteDimensionsRemainAvailable() {
        var icon=MedicalStatusIcons.bleeding(state(.21f,false));assertEquals(25,icon.width());assertEquals(40,icon.height());
    }
}
