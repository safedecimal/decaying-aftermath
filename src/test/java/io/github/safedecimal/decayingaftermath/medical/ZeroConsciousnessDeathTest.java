package io.github.safedecimal.decayingaftermath.medical;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ZeroConsciousnessDeathTest {
    private BodySnapshot blackout(float consciousness) {
        var b=new BodyModel().snapshot();
        return new BodySnapshot(.32f,1,1,0,0,72,BodySnapshot.Mode.UNCONSCIOUS,0,1,consciousness,b.parts(),b.survival());
    }
    @Test void zeroOrDisplayedZeroConsciousnessIsBlackoutNotDeath() {
        assertTrue(MedicalDeathPolicy.rejectBlackoutDeath(blackout(0),false));
        assertTrue(MedicalDeathPolicy.rejectBlackoutDeath(blackout(.004f),false));
        assertFalse(MedicalDeathPolicy.rejectBlackoutDeath(blackout(.1f),false));
    }
    @Test void actualDeadStateAndExplicitLethalSourcesAreNeverBlocked() {
        assertFalse(MedicalDeathPolicy.rejectBlackoutDeath(blackout(0),true));
        var b=new BodyModel();b.kill();assertFalse(MedicalDeathPolicy.rejectBlackoutDeath(b.snapshot(),false));
    }
    @Test void lossOfNeuralFunctionAloneDoesNotKillTheBody() {
        var base=new BodyModel().snapshot();var body=BodyModel.restore(blackout(0).withBrainHealth(0));
        for(int tick=0;tick<100;tick++)body.tick(BodyEnvironment.temperate(false,false));
        assertEquals(BodySnapshot.Mode.UNCONSCIOUS,body.mode());
        assertEquals(0,body.snapshot().consciousness());assertTrue(body.snapshot().condition()>0);
    }
}
