package io.github.safedecimal.decayingaftermath.compat;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class MedicalRagdollLifecycleTest {
    private static final class Port implements MedicalRagdollLifecycle.Port<Object> {
        int serial, starts, stops;
        public int liveSerial(int id){return serial;}
        public int start(Object player){starts++;return serial=starts;}
        public void stop(int id){stops++;serial=0;}
    }
    @Test void manyUnconsciousTicksStartOnlyOnceThenRecoveryStops() throws Exception {
        var port=new Port();var lifecycle=new MedicalRagdollLifecycle<>(port);var p=new Object();var level=new Object();
        for(int i=0;i<100;i++)lifecycle.update(p,7,level,true);
        assertEquals(1,port.starts);lifecycle.update(p,7,level,false);assertEquals(1,port.stops);
        lifecycle.update(p,7,level,false);assertEquals(1,port.stops);
    }
    @Test void sleepTransitionStopsOwnBodyAndCannotRestart() throws Exception {
        var port=new Port();var lifecycle=new MedicalRagdollLifecycle<>(port);var p=new Object();var level=new Object();
        lifecycle.update(p,7,level,true);lifecycle.update(p,7,level,false);assertEquals(1,port.stops);
        for(int i=0;i<100;i++)lifecycle.update(p,7,level,false);assertEquals(1,port.starts);
    }
    @Test void replacementDeathOrOtherLiveSerialIsNeverRemoved() throws Exception {
        var port=new Port();var lifecycle=new MedicalRagdollLifecycle<>(port);var p=new Object();var level=new Object();
        lifecycle.update(p,7,level,true);port.serial=999;lifecycle.update(p,7,level,false);assertEquals(0,port.stops);
        lifecycle.update(p,7,level,true);assertEquals(1,port.starts);
    }
    @Test void leavingOrChangingDimensionReleasesOwnedBody() throws Exception {
        var port=new Port();var lifecycle=new MedicalRagdollLifecycle<>(port);var p=new Object();
        lifecycle.update(p,7,new Object(),true);lifecycle.update(p,7,new Object(),true);
        assertEquals(2,port.starts);assertEquals(1,port.stops);lifecycle.release(p);assertEquals(2,port.stops);
    }
}
