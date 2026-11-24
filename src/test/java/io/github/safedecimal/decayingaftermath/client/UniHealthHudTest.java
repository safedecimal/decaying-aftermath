package io.github.safedecimal.decayingaftermath.client;

import static org.junit.jupiter.api.Assertions.*;
import io.github.safedecimal.decayingaftermath.medical.*;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class UniHealthHudTest {
    @Test void nineIconSlotsAreInsideNativeDeviceAndScaleToNarrowGui() {
        for(int slot=0;slot<9;slot++)assertTrue(UniHealthHudLayout.slotX(slot)+UniHealthHudLayout.SLOT_SIZE<=288);
        assertNotEquals(UniHealthHudLayout.slotX(0),UniHealthHudLayout.slotX(8));
        var box=UniHealthHudLayout.at(320,180);
        assertTrue(box.x()>=0);assertTrue(box.x()+UniHealthHudLayout.WIDTH*box.scale()<=320);assertTrue(box.y()>=0);
    }
    @Test void footprintNeverConsumesHalfTheScreenAtAnyGuiScaleOrAspectRatio() {
        for(int[] size:new int[][]{{426,240},{320,180},{480,270},{1920,1080},{3440,1440},{180,320},{64,32},{1,1}}) {
            var box=UniHealthHudLayout.at(size[0],size[1]);
            float width=UniHealthHudLayout.WIDTH*box.scale(),height=UniHealthHudLayout.HEIGHT*box.scale();
            assertTrue(width<=size[0]*.60f+.001f);assertTrue(height<=size[1]*.18f+.001f);
            assertEquals((size[0]-width)/2,box.x(),.001f);
            assertTrue(box.y()>=0);assertTrue(box.y()+height<=size[1]);
        }
        assertThrows(IllegalArgumentException.class,()->UniHealthHudLayout.at(0,240));
        assertThrows(IllegalArgumentException.class,()->UniHealthHudLayout.at(426,-1));
        assertThrows(IllegalArgumentException.class,()->UniHealthHudLayout.slotX(9));
    }
    @Test void compactBackdropHasReviewedDarkLcdAndTransparentEffectColumns() throws Exception {
        var image=javax.imageio.ImageIO.read(Path.of("src/main/resources/assets/decaying_aftermath/textures/gui/uni_health/compact_backdrop.png").toFile());
        assertEquals(UniHealthHudLayout.WIDTH,image.getWidth());assertEquals(UniHealthHudLayout.HEIGHT,image.getHeight());
        assertEquals(0,image.getRGB(0,0)>>>24);assertEquals(255,image.getRGB(24,30)>>>24);
    }
    @Test void medicalConditionsAreIconMedallionsAndHealthyBodyHasNone() {
        var body=new BodyModel().snapshot();assertTrue(UniHealthHud.medicalBadges(body).isEmpty());
        var parts=body.parts();parts[0]=new BodySnapshot.PartSnapshot(.5f,.1f,.8f,true,false,false,false,.8f,false,false);
        var injured=new BodySnapshot(.5f,.5f,.4f,.8f,0,110,BodySnapshot.Mode.CONSCIOUS,1,.1f,1,parts,body.survival());
        assertTrue(UniHealthHud.medicalBadges(injured).stream().anyMatch(b->b.label().equals("BLEED")));
        assertTrue(UniHealthHud.medicalBadges(injured).stream().anyMatch(b->b.label().equals("FRACTURE")));
        assertTrue(UniHealthHud.medicalBadges(injured).stream().anyMatch(b->b.label().equals("HYPOXIA")));
    }
    @Test void mixedExternalAndInternalBleedingOccupyOnlyOneMedallion() {
        var body=new BodyModel().snapshot();
        var parts=body.parts();
        parts[0]=parts[0].withExternalBleeding(new BleedProfile(.02f,.03f,.12f));
        parts[1]=new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false,0,false,false,
                0,0,.2f,false,false,1);
        var withBoth=new BodySnapshot(1,1,1,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,parts);
        var badges=UniHealthHud.medicalBadges(withBoth);
        assertEquals(1,badges.size());
        assertEquals("blood",badges.getFirst().icon());

        parts[0]=body.part(BodyPart.HEAD).withExternalBleeding(new BleedProfile(.0001f,0,0));
        var internalOnly=new BodySnapshot(1,1,1,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,parts);
        badges=UniHealthHud.medicalBadges(internalOnly);
        assertEquals(1,badges.size());
        assertEquals("warning",badges.getFirst().icon());
    }
    @Test void originalUniHealthFrameIsSourceAndSketchIsNeverAnAsset() throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/decaying_aftermath/uni_health_hud_provenance.json"))).getAsJsonObject();
        assertTrue(json.get("background_source").getAsString().contains("computerback"));
        assertEquals("user example sketch",json.get("not_source").getAsString());
    }
    @Test void vcrOdsFontsUseNativeLowercaseResourceNames() throws Exception {
        Path root=Path.of("src/main/resources/assets/decaying_aftermath");
        var json=JsonParser.parseString(Files.readString(root.resolve("font/aftermath_thin.json"))).getAsJsonObject();
        assertEquals("decaying_aftermath:vcr_osd_mono.ttf",json.getAsJsonArray("providers").get(0).getAsJsonObject().get("file").getAsString());
        assertEquals(75864,Files.size(root.resolve("font/vcr_osd_mono.ttf")));
    }
    @Test void restingHeartbeatHasNoFovPulseButEnhancedPulseDoes() {
        var b=new BodyModel().snapshot();assertFalse(MedicalPresentationPolicy.heartbeatVision(false,b));
        var fast=new BodySnapshot(1,1,1,0,0,130,BodySnapshot.Mode.CONSCIOUS,1,1,1,b.parts(),b.survival());
        assertTrue(MedicalPresentationPolicy.heartbeatVision(false,fast));
        assertFalse(MedicalPresentationPolicy.heartbeatVision(true,fast));
    }
}
