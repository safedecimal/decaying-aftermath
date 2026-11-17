package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalDeathMusicStateTest {
    @Test void dyingLoopStartsOnceForAnyNumberOfBodyUpdates() {
        var state=new MedicalDeathMusicState();assertTrue(state.advance(Mode.DYING).startDying());
        for(int i=0;i<500;i++) {var action=state.advance(Mode.DYING);assertFalse(action.startDying());assertFalse(action.playDead());}
    }
    @Test void actualDeathStopsDyingAndPlaysNonLoopingClipOnce() {
        var state=new MedicalDeathMusicState();state.advance(Mode.DYING);var action=state.advance(Mode.DEAD);
        assertTrue(action.stopDying());assertTrue(action.playDead());
        for(int i=0;i<500;i++)assertFalse(state.advance(Mode.DEAD).playDead());
    }
    @Test void successfulRescueStopsDyingWithoutPlayingDeathTrack() {
        var state=new MedicalDeathMusicState();state.advance(Mode.DYING);var action=state.advance(Mode.CONSCIOUS);
        assertTrue(action.stopDying());assertFalse(action.playDead());
    }
    @Test void respawnStopsDeathClipAndAllowsTheNextLifeToPlayIt() {
        var state=new MedicalDeathMusicState();state.advance(Mode.DEAD);assertTrue(state.advance(Mode.CONSCIOUS).stopDead());
        assertTrue(state.advance(Mode.DEAD).playDead());
    }
    @Test void disconnectResetDoesNotCarryPlayedDeadIntoANewSession() {
        var state=new MedicalDeathMusicState();state.advance(Mode.DEAD);state.reset();assertTrue(state.advance(Mode.DEAD).playDead());
    }
}
