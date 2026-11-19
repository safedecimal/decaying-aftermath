package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CuProcedureGeometryTest {
    @Test void rotatedToolsAndAlignmentCuesShareTheActualGeometry() {
        assertTrue(CuProcedureGeometry.toolGrip(136,116,102,90,32,32,45,4));
        assertFalse(CuProcedureGeometry.toolGrip(102,90,102,90,32,32,45,0));
        assertTrue(CuProcedureGeometry.deviceAligned(116,105,0,true));
        assertFalse(CuProcedureGeometry.deviceAligned(116,105,18,true));
        assertFalse(CuProcedureGeometry.deviceAligned(47,90,0,false));
    }
    @Test void visibleWindlassEndsCanBeGrabbedAtEveryRotation() {
        for(int degrees=0;degrees<360;degrees+=15) {
            double r=Math.toRadians(degrees);
            assertTrue(CuProcedureGeometry.windlassGrip(128+22*Math.cos(r),128+22*Math.sin(r),degrees));
        }
        assertFalse(CuProcedureGeometry.windlassGrip(128,128,0));
        assertFalse(CuProcedureGeometry.windlassGrip(188,128,0));
    }
    @Test void bottleHitboxMatchesTheRendered32PixelItem() {
        assertTrue(CuProcedureGeometry.bottleGrip(63,106,47,90));
        assertFalse(CuProcedureGeometry.bottleGrip(63,134,47,90));
    }
    @Test void rotatedMouthNotHandPositionChoosesTheWound() {
        var mouth=CuProcedureGeometry.bottleMouth(102,90,60);
        assertTrue(CuProcedureGeometry.overWound(BodyPart.LEFT_LEG,mouth));
        assertFalse(CuProcedureGeometry.overWound(BodyPart.LEFT_LEG,CuProcedureGeometry.bottleMouth(45,90,60)));
        assertEquals(102+16+11*Math.sin(Math.toRadians(60)),mouth.x(),.000001);
    }
    @Test void pouringRequiresSteadyIntermediateTilt() {
        assertEquals(0,CuProcedureGeometry.pourStrength(0));assertEquals(0,CuProcedureGeometry.pourStrength(95));
        assertEquals(1,CuProcedureGeometry.pourStrength(60));assertTrue(CuProcedureGeometry.pourStrength(40)<.5);
        assertEquals(0,CuProcedureGeometry.pourStrength(Double.NaN));
    }
    @Test void antisepticWindowAcceptsTheVisibleNozzleAndRejectsAnOutsidePoint() {
        for(var part:BodyPart.values()) {
            var zone=CuProcedureGeometry.antisepticZone(part);
            var mouthOffset=CuProcedureGeometry.bottleMouth(0,0,60);
            var centeredMouth=CuProcedureGeometry.bottleMouth(zone.x()+zone.width()/2.-mouthOffset.x(),
                    zone.y()+10-mouthOffset.y(),60);
            assertTrue(CuProcedureGeometry.contains(zone,centeredMouth));
            assertFalse(CuProcedureGeometry.contains(zone,new CuProcedureGeometry.Point(zone.x()-1,centeredMouth.y())));
        }
        assertEquals(0,CuProcedureGeometry.antisepticFlow(30));
        assertEquals(.5f,CuProcedureGeometry.antisepticFlow(40));
        assertEquals(1,CuProcedureGeometry.antisepticFlow(90));
        assertEquals(0,CuProcedureGeometry.antisepticFlow(Double.NaN));
    }
    @Test void everyPartUsesTheSameBoundsForDrawingAndAim() {
        for(BodyPart part:BodyPart.values()) {
            var b=CuProcedureGeometry.part(part,111,80,34,116);
            assertTrue(b.width()<=34&&b.height()<=116);
            assertTrue(CuProcedureGeometry.overWound(part,new CuProcedureGeometry.Point(b.x()+b.width()/2.,b.y()-8)));
        }
    }
}
