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
    @Test void bleedingTintUsesSerializedGradientColorKeysRatherThanConstantPink() {
        assertEquals(0xFF000000,MedicalStatusIcons.bleedingColor(state(0,false)));
        assertEquals(0xFF6E6E6E,MedicalStatusIcons.bleedingColor(state(66/65535f,false)));
        assertEquals(0xFFFFFFFF,MedicalStatusIcons.bleedingColor(state(3277/65535f,false)));
        assertEquals(0xFFFFFF00,MedicalStatusIcons.bleedingColor(state(6554/65535f,false)));
        assertEquals(0xFFBB8500,MedicalStatusIcons.bleedingColor(state(13107/65535f,false)));
        assertEquals(0xFF891900,MedicalStatusIcons.bleedingColor(state(29491/65535f,false)));
        assertEquals(0xFF340600,MedicalStatusIcons.bleedingColor(state(1,false)));
    }
    @Test void coveredAndUncoveredBleedingHaveIdenticalSeverityTint() {
        for(float value:new float[]{.01f,.04f,.08f,.1f,.2f,.5f,1})
            assertEquals(MedicalStatusIcons.bleedingColor(state(value,false)),MedicalStatusIcons.bleedingColor(state(value,true)));
    }
    @Test void infectionTintUsesCuPurpleProgressionForBothDirections() {
        assertEquals(0xFFFFFFFF,MedicalStatusIcons.infectionColor(infected(0,false)));
        assertEquals(0xFFE8BFEC,MedicalStatusIcons.infectionColor(infected(.25f,false)));
        assertEquals(0xFFD17FDA,MedicalStatusIcons.infectionColor(infected(.5f,true)));
        assertEquals(0xFFA300B6,MedicalStatusIcons.infectionColor(infected(1,false)));
    }
    @Test void splintDoesNotWhitenPendingFractureInPlaceOfMissingHealingTimer() {
        var before=new BodySnapshot.PartSnapshot(.5f,0,0,true,false,false,false);
        var splinted=new BodySnapshot.PartSnapshot(.5f,0,0,true,false,true,false);
        assertEquals(0xFFFF0000,MedicalStatusIcons.fractureColor(before));
        assertEquals(MedicalStatusIcons.fractureColor(before),MedicalStatusIcons.fractureColor(splinted));
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
    @Test void nativeSprayIconIsIndependentOfVisibleInfectionAndFades() throws Exception {
        var clean = new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false,0,true,false);
        var expiring = new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false,0,true,false,
                0,0,0,false,false,1,false,1,0);
        assertNull(MedicalStatusIcons.infection(clean));
        assertNull(MedicalStatusIcons.disinfection(infected(0,false)));
        var icon=MedicalStatusIcons.disinfection(clean);
        assertEquals("disinfected",icon.name());
        assertEquals(0xFFFFFFFF,MedicalStatusIcons.disinfectionColor(clean));
        assertEquals(0xFF191919,MedicalStatusIcons.disinfectionColor(expiring));
        try(var png=getClass().getResourceAsStream("/assets/decaying_aftermath/textures/gui/status/disinfected.png")) {
            assertNotNull(png);
            var sprite=javax.imageio.ImageIO.read(png);
            assertEquals(17,sprite.getWidth());assertEquals(23,sprite.getHeight());
        }
    }
    @Test void exhaustedBandageCareReturnsToUncoveredBleedingEvenIfPreviouslyBandaged() {
        var used=new BodySnapshot.PartSnapshot(1,.1f,0,false,true,false,false,0,false,false,0);
        assertEquals("bleeding_heavy",MedicalStatusIcons.bleeding(used).name());
        var covered=new BodySnapshot.PartSnapshot(1,.1f,0,false,true,false,false,0,false,false,.006f);
        assertEquals("bleeding_covered_heavy",MedicalStatusIcons.bleeding(covered).name());
    }
    @Test void fourStatusIconsFitAllRealWideAndSlimBodyParts() {
        for(boolean slim:new boolean[]{false,true})for(var part:io.github.safedecimal.decayingaftermath.medical.BodyPart.values()) {
            var box=MinecraftBodyLayout.box(part,slim);var placements=MedicalStatusIcons.placements(infected(.5f,true),box);
            assertEquals(4,placements.size());
            for(var p:placements) {
                float ratio=p.size()/(float)Math.max(p.icon().width(),p.icon().height());
                int w=Math.max(1,Math.round(p.icon().width()*ratio)),h=Math.max(1,Math.round(p.icon().height()*ratio));
                assertTrue(p.x()-w/2>=box.x());assertTrue(p.y()-h/2>=box.y());
                assertTrue(p.x()-w/2+w<=box.x()+box.width());assertTrue(p.y()-h/2+h<=box.y()+box.height());
            }
        }
    }
}
