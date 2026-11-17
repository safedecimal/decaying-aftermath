package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FastenerMotionTest {
    @Test void oneCursorJumpCannotFinishAStrap() {var pull=new FastenerMotion().pull(1,148,112,1.0/60);assertFalse(pull.complete());assertTrue(pull.fraction()<.01);}
    @Test void offRailMotionDoesNotFastenAnything() {var pull=new FastenerMotion().pull(1,148,190,1);assertTrue(pull.offRail());assertEquals(0,pull.fraction());}
    @Test void allFourThreadAndTensionStrokesUseOneMaterialUnit() {
        var straps=new FastenerMotion();float total=0;
        for(int phase=1;phase<=4;phase++)for(int i=0;i<100;i++)total+=straps.pull(phase,phase%2==1?148:96,phase<3?112:164,1.0/60).fraction();
        assertEquals(1,total,.00001);
    }
    @Test void repeatedThreadMotionCannotRefundOrDuplicateMaterial() {
        var straps=new FastenerMotion();for(int i=0;i<100;i++)straps.pull(1,148,112,1.0/60);assertEquals(0,straps.pull(1,96,112,1).fraction());assertEquals(0,straps.pull(1,148,112,1).fraction());
    }
}
