package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode.*;

class RunningConsciousnessTest {
    @Test void healthyWalkingAndSprintingConsumeEnergyButNotConsciousness() {
        for(boolean sprint:new boolean[]{false,true}) {
            var body=new BodyModel();
            for(int i=0;i<6000;i++) {
                body.tick(sprint,true);var s=body.snapshot();
                assertEquals(CONSCIOUS,s.mode(),"sprint="+sprint+",tick="+i);
                assertEquals(1,s.consciousness(),.00001f,"sprint="+sprint+",tick="+i);
            }
            assertTrue(body.snapshot().energy()<.05f);
            assertFalse(body.saveState().lastStandingSpent());assertEquals(0,body.saveState().lastStandingTicks());
        }
    }
    @Test void zeroEnergyIsPhysicalExhaustionNotAutomaticFainting() {
        var base=new BodyModel().snapshot();
        var exhausted=new BodySnapshot(1,1,1,0,0,72,CONSCIOUS,1,0,1,base.parts(),base.survival());
        var b=BodyModel.restore(exhausted);b.tick(false,false);
        assertEquals(CONSCIOUS,b.snapshot().mode());assertEquals(1,b.snapshot().consciousness());
        assertTrue(b.snapshot().energy()>0);
    }
    @Test void restRecoversEnergyWithoutAFalseWakeupCycle() {
        var b=new BodyModel();for(int i=0;i<1000;i++)b.tick(true,true);float spent=b.snapshot().energy();
        for(int i=0;i<600;i++)b.tick(false,false);
        assertTrue(b.snapshot().energy()>spent);assertEquals(CONSCIOUS,b.snapshot().mode());assertEquals(1,b.snapshot().consciousness());
    }
    @Test void lowOxygenAndOpiateRespiratoryDepressionStillImpairConsciousness() {
        var hypoxic=new BodyModel();hypoxic.reduceOxygen(.6f);assertEquals(UNCONSCIOUS,hypoxic.snapshot().mode());
        var sedated=new BodyModel();sedated.treat(BodyPart.LEFT_ARM,TreatmentKind.FENTANYL,1);
        assertTrue(sedated.snapshot().consciousness()<1);
    }
    @Test void exhaustedBodiesRemainExhaustedAndConsciousAfterSaveRestore() {
        var b=new BodyModel();for(int i=0;i<1200;i++)b.tick(true,true);
        var restored=BodyModel.restoreState(b.saveState());assertEquals(b.snapshot().energy(),restored.snapshot().energy());
        assertEquals(CONSCIOUS,restored.snapshot().mode());assertEquals(1,restored.snapshot().consciousness());
    }
}
