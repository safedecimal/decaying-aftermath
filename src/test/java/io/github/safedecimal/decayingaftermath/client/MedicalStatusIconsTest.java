package io.github.safedecimal.decayingaftermath.client;

import com.google.gson.JsonParser;
import io.github.safedecimal.decayingaftermath.medical.BleedProfile;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static io.github.safedecimal.decayingaftermath.client.MedicalStatusIcons.BleedType.*;
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
        try(var png=getClass().getResourceAsStream("/assets/abjection/textures/gui/status/disinfected.png")) {
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
    @Test void mixedWoundsShowEachTypeAboveTheSameStrictDisplayThreshold() {
        float min=MedicalStatusIcons.MIN_VISIBLE_BLEED;
        var exact=state(0,false).withExternalBleeding(new BleedProfile(min,0,0));
        assertNull(MedicalStatusIcons.bleeding(exact));
        assertTrue(MedicalStatusIcons.bleedingTypes(exact).isEmpty());
        var mixed=exact.withExternalBleeding(new BleedProfile(Math.nextUp(min),min,.01f));
        assertEquals(List.of(CAPILLARY,ARTERIAL),MedicalStatusIcons.bleedingTypes(mixed));
        assertEquals("bleeding_light",MedicalStatusIcons.bleeding(mixed).name());
    }
    @Test void severityAndTintIgnoreVesselTypeAndEffectiveBloodLoss() {
        var capillary=state(0,true).withExternalBleeding(new BleedProfile(.21f,0,0));
        var arterial=state(0,true).withExternalBleeding(new BleedProfile(0,0,.21f));
        assertEquals(List.of(CAPILLARY),MedicalStatusIcons.bleedingTypes(capillary));
        assertEquals(List.of(ARTERIAL),MedicalStatusIcons.bleedingTypes(arterial));
        assertEquals("bleeding_covered_arterial",MedicalStatusIcons.bleeding(capillary).name());
        assertEquals(MedicalStatusIcons.bleeding(capillary),MedicalStatusIcons.bleeding(arterial));
        assertEquals(MedicalStatusIcons.bleedingColor(capillary),MedicalStatusIcons.bleedingColor(arterial));
    }
    @Test void typedAssetsAreDistinctTransparentMonochromeMasksWithLocalizedNames() throws Exception {
        var masks=new HashSet<BitSet>();
        try(var en=resource("lang/en_us.json");var ru=resource("lang/ru_ru.json")) {
            var english=JsonParser.parseReader(new InputStreamReader(en,StandardCharsets.UTF_8)).getAsJsonObject();
            var russian=JsonParser.parseReader(new InputStreamReader(ru,StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("Bleeding",english.get("abjection.status.bleed_type.heading").getAsString());
            assertEquals("Кровотечение",russian.get("abjection.status.bleed_type.heading").getAsString());
            for(var type:MedicalStatusIcons.BleedType.values()) {
                assertTrue(english.get(type.translationKey).getAsString().matches("[A-Za-z]+"));
                assertTrue(russian.get(type.translationKey).getAsString().matches("[А-Яа-яЁё]+"));
                assertFalse(type.icon.name().startsWith("bleeding_"));
                try(var png=resource("textures/gui/status/"+type.icon.name()+".png")) {
                    var image=ImageIO.read(png);
                    assertEquals(type.icon.width(),image.getWidth());assertEquals(type.icon.height(),image.getHeight());
                    var mask=new BitSet();
                    for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                        int pixel=image.getRGB(x,y);
                        if(pixel>>>24==0)continue;
                        assertEquals(0xFFFFFFFF,pixel);
                        mask.set(y*image.getWidth()+x);
                    }
                    assertTrue(mask.cardinality()>0&&mask.cardinality()<image.getWidth()*image.getHeight());
                    assertTrue(masks.add(mask),"Each type needs its own silhouette");
                }
            }
        }
    }
    private java.io.InputStream resource(String path) {
        var resource=getClass().getResourceAsStream("/assets/abjection/"+path);
        assertNotNull(resource,path);
        return resource;
    }
    @Test void fourStatusIconsFitAllRealWideAndSlimBodyParts() {
        var mixed=infected(.5f,true).withExternalBleeding(new BleedProfile(.02f,.03f,.12f));
        for(boolean slim:new boolean[]{false,true})for(var part:io.github.safedecimal.decayingaftermath.medical.BodyPart.values()) {
            var box=MinecraftBodyLayout.box(part,slim);var placements=MedicalStatusIcons.placements(mixed,box);
            assertEquals(List.of("bleeding_heavy","fracture","infection_decreasing","disinfected"),
                    placements.stream().map(p->p.icon().name()).toList());
            for(var p:placements) {
                float ratio=p.size()/(float)Math.max(p.icon().width(),p.icon().height());
                int w=Math.max(1,Math.round(p.icon().width()*ratio)),h=Math.max(1,Math.round(p.icon().height()*ratio));
                assertTrue(p.x()-w/2>=box.x());assertTrue(p.y()-h/2>=box.y());
                assertTrue(p.x()-w/2+w<=box.x()+box.width());assertTrue(p.y()-h/2+h<=box.y()+box.height());
            }
        }
    }
}
