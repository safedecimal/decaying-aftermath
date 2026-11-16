package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftBodyLayoutTest {
    @Test void everyDrawnPartCenterSelectsExactlyThatPart() {
        for (boolean slim : new boolean[]{false,true}) for (BodyPart part : BodyPart.values()) {
            var b=MinecraftBodyLayout.box(part,slim);
            assertEquals(part,MinecraftBodyLayout.partAt(b.x()+b.width()/2.,b.y()+b.height()/2.,slim));
        }
    }
    @Test void halfOpenEdgesDoNotOverlapOrSelectBackground() {
        assertEquals(BodyPart.TORSO,MinecraftBodyLayout.partAt(110,52,false));
        assertEquals(BodyPart.LEFT_LEG,MinecraftBodyLayout.partAt(110,100,false));
        assertNull(MinecraftBodyLayout.partAt(20,40,false));
        assertNull(MinecraftBodyLayout.partAt(110,148,false));
    }
    @Test void widePlayerMatchesMinecraftEightEightTwelveProportions() {
        assertEquals(32,MinecraftBodyLayout.box(BodyPart.HEAD,false).width());
        assertEquals(32,MinecraftBodyLayout.box(BodyPart.HEAD,false).height());
        assertEquals(48,MinecraftBodyLayout.box(BodyPart.TORSO,false).height());
        assertEquals(48,MinecraftBodyLayout.box(BodyPart.LEFT_ARM,false).width());
        assertEquals(16,MinecraftBodyLayout.box(BodyPart.LEFT_ARM,false).height());
        assertEquals(48,MinecraftBodyLayout.box(BodyPart.LEFT_LEG,false).height());
    }
    @Test void slimArmHitBoxesMatchTheirThreePixelSkinFaces() {
        assertEquals(12,MinecraftBodyLayout.box(BodyPart.LEFT_ARM,true).height());
        assertEquals(3,MinecraftBodyLayout.face(BodyPart.LEFT_ARM,true).width());
        assertNull(MinecraftBodyLayout.partAt(79,65,true));
        assertEquals(BodyPart.RIGHT_ARM,MinecraftBodyLayout.partAt(83,58,true));
    }
    @Test void allBaseAndOuterSkinUvsRemainInsideNative64PixelAtlas() {
        for (boolean slim : new boolean[]{false,true}) for (BodyPart part : BodyPart.values()) {
            var f=MinecraftBodyLayout.face(part,slim);
            assertTrue(f.u()>=0&&f.u()+f.width()<=64&&f.v()>=0&&f.v()+f.height()<=64);
            assertTrue(f.outerU()>=0&&f.outerU()+f.width()<=64&&f.outerV()>=0&&f.outerV()+f.height()<=64);
        }
    }
    @Test void inverseScreenTransformPreservesPartAtSeveralGuiScales() {
        for (double scale : new double[]{.45,.75,1}) for (BodyPart part : BodyPart.values()) {
            var b=MinecraftBodyLayout.box(part,false);
            double x=210+(b.x()+b.width()/2.)*scale,y=35+(b.y()+b.height()/2.)*scale;
            assertEquals(part,MinecraftBodyLayout.partAt((x-210)/scale,(y-35)/scale,false));
        }
    }
}
