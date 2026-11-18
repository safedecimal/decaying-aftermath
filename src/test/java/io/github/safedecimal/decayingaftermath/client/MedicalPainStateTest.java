package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalPainStateTest {
    @Test void noPainLeavesScreenUntouched() {
        var s=new MedicalPainState();for(int i=0;i<600;i++)s.advance(0,.05);
        assertEquals(0,s.intensity());assertEquals(0,s.maskIndex());
    }
    @Test void usesCuSixPerSecondSmoothingNotHealthOrInstantDamageFlash() {
        var s=new MedicalPainState();s.advance(1,1.0/60);
        assertEquals(.1,s.smoothedPain(),1e-9);assertTrue(s.intensity()>0&&s.intensity()<.12);
    }
    @Test void persistentPainPulsesEvenWhenTheSnapshotDoesNotChange() {
        var s=new MedicalPainState();for(int i=0;i<120;i++)s.advance(1,1.0/60);
        double min=2,max=0;for(int i=0;i<120;i++){s.advance(1,1.0/60);min=Math.min(min,s.intensity());max=Math.max(max,s.intensity());}
        assertTrue(min<.87);assertTrue(max>1.13);assertTrue(max<=1.15);
    }
    @Test void analgesiaFadesAndDisconnectOrRespawnClearsImmediately() {
        var s=new MedicalPainState();s.advance(1,1);double before=s.intensity();
        s.advance(0,.05);assertTrue(s.intensity()>0);assertTrue(s.intensity()<before);
        for(int i=0;i<100;i++)s.advance(0,.05);assertEquals(0,s.intensity());
        s.advance(1,1);s.reset();assertEquals(0,s.intensity());assertEquals(0,s.maskBlend());
    }
    @Test void framePartitionIsStableAndInvalidValuesCannotPoisonRenderer() {
        var tick=new MedicalPainState();var frame=new MedicalPainState();
        for(int i=0;i<20;i++)tick.advance(.8f,.05);for(int i=0;i<60;i++)frame.advance(.8f,1.0/60);
        assertEquals(frame.intensity(),tick.intensity(),1e-8);
        tick.advance(Float.NaN,Double.NaN);assertTrue(Double.isFinite(tick.intensity()));
        tick.reset();tick.advance(Float.NaN,.05);assertEquals(0,tick.intensity());
    }
}
