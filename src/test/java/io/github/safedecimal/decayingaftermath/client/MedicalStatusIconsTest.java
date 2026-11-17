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
    private BodySnapshot.PartSnapshot infected(float infection,boolean disinfected) {
        return new BodySnapshot.PartSnapshot(1,.1f,.5f,true,false,false,false,infection,disinfected,false);
    }
    @Test void nativeInfectionThresholdDoesNotInventAnIconForCleanSkin() {
        assertNull(MedicalStatusIcons.infection(infected(.249f,false)));
        assertEquals("infection_increasing",MedicalStatusIcons.infection(infected(.25f,false)).name());
    }
    @Test void disinfectedInfectionUsesOriginalDownIconWithItsWholeSpriteRegion() {
        var icon=MedicalStatusIcons.infection(infected(.5f,true));
        assertEquals("infection_decreasing",icon.name());assertEquals(24,icon.width());assertEquals(22,icon.height());
    }
    @Test void exhaustedBandageCareReturnsToUncoveredBleedingEvenIfPreviouslyBandaged() {
        var used=new BodySnapshot.PartSnapshot(1,.1f,0,false,true,false,false,0,false,false,0);
        assertEquals("bleeding_heavy",MedicalStatusIcons.bleeding(used).name());
        var covered=new BodySnapshot.PartSnapshot(1,.1f,0,false,true,false,false,0,false,false,.006f);
        assertEquals("bleeding_covered_heavy",MedicalStatusIcons.bleeding(covered).name());
    }
    @Test void threeStatusIconsFitAllRealWideAndSlimBodyParts() {
        for(boolean slim:new boolean[]{false,true})for(var part:io.github.safedecimal.decayingaftermath.medical.BodyPart.values()) {
            var box=MinecraftBodyLayout.box(part,slim);var placements=MedicalStatusIcons.placements(infected(.5f,false),box);
            assertEquals(3,placements.size());
            for(var p:placements) {
                float ratio=p.size()/(float)Math.max(p.icon().width(),p.icon().height());
                int w=Math.max(1,Math.round(p.icon().width()*ratio)),h=Math.max(1,Math.round(p.icon().height()*ratio));
                assertTrue(p.x()-w/2>=box.x());assertTrue(p.y()-h/2>=box.y());
                assertTrue(p.x()-w/2+w<=box.x()+box.width());assertTrue(p.y()-h/2+h<=box.y()+box.height());
            }
        }
    }
}
