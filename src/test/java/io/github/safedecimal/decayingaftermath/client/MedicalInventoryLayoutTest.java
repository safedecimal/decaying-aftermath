package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalInventoryLayoutTest {
    @Test void allFortySixNativeSlotsFitAndDoNotOverlapEachOtherOrBody() {
        for(int i=0;i<46;i++) {
            var a=MedicalInventoryLayout.slot(i);
            assertTrue(a.x()>=1&&a.y()>=0&&a.x()+17<=MedicalInventoryLayout.WIDTH&&a.y()+17<=MedicalInventoryLayout.HEIGHT);
            for(int j=i+1;j<46;j++) {
                var b=MedicalInventoryLayout.slot(j);
                assertFalse(overlap(a.x()-1,a.y()-1,18,18,b.x()-1,b.y()-1,18,18),i+" overlaps "+j);
            }
            for(boolean slim:new boolean[]{false,true}) for(BodyPart part:BodyPart.values()) {
                var b=MinecraftBodyLayout.box(part,slim);
                assertFalse(overlap(a.x()-1,a.y()-1,18,18,b.x(),b.y(),b.width(),b.height()),i+" covers "+part);
            }
        }
    }
    @Test void armorAndOffhandAreNextToTheirAnatomicalParts() {
        assertEquals(new MedicalInventoryLayout.Position(101,0),MedicalInventoryLayout.slot(5));
        assertEquals(new MedicalInventoryLayout.Position(130,76),MedicalInventoryLayout.slot(6));
        assertEquals(new MedicalInventoryLayout.Position(72,106),MedicalInventoryLayout.slot(7));
        assertEquals(new MedicalInventoryLayout.Position(132,129),MedicalInventoryLayout.slot(8));
        assertEquals(new MedicalInventoryLayout.Position(178,52),MedicalInventoryLayout.slot(45));
    }
    @Test void gridsAndNativeHotbarSpacingAreAligned() {
        for(int i=9;i<36;i++) assertEquals(new MedicalInventoryLayout.Position(30+(i-9)%9*18,164+(i-9)/9*18),MedicalInventoryLayout.slot(i));
        for(int i=36;i<45;i++) assertEquals(new MedicalInventoryLayout.Position(MedicalInventoryLayout.HOTBAR_X+3+(i-36)*20,MedicalInventoryLayout.HOTBAR_Y+3),MedicalInventoryLayout.slot(i));
    }
    @Test void fullBleedingDetailsStayClearOfBodyAndInventorySlots() {
        var details=MedicalInventoryLayout.BLEED_DETAILS;
        assertTrue(details.x()>=0&&details.y()>=0);
        assertTrue(details.x()+details.width()<=MedicalInventoryLayout.WIDTH);
        assertTrue(details.y()+details.height()<=MedicalInventoryLayout.HEIGHT);
        for(boolean slim:new boolean[]{false,true}) for(BodyPart part:BodyPart.values()) {
            var b=MinecraftBodyLayout.box(part,slim);
            assertFalse(overlap(details.x(),details.y(),details.width(),details.height(),b.x(),b.y(),b.width(),b.height()),part.name());
        }
        for(int i=0;i<46;i++) {
            var slot=MedicalInventoryLayout.slot(i);
            assertFalse(overlap(details.x(),details.y(),details.width(),details.height(),slot.x()-1,slot.y()-1,18,18),"slot "+i);
        }
    }
    private static boolean overlap(int x,int y,int w,int h,int bx,int by,int bw,int bh) {
        return x<bx+bw&&x+w>bx&&y<by+bh&&y+h>by;
    }
}
