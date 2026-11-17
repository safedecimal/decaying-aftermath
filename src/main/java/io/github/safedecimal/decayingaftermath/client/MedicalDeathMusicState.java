package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;

/** MusicManager.Update's alive/brainDying and playedDead gates, independent of beat/intro clocks. */
public final class MedicalDeathMusicState {
    public record Actions(boolean startDying,boolean stopDying,boolean playDead,boolean stopDead) { }
    private boolean dyingActive,playedDead;
    public Actions advance(Mode mode) {
        boolean dying=mode==Mode.DYING,dead=mode==Mode.DEAD;
        var result=new Actions(dying&&!dyingActive,!dying&&dyingActive,dead&&!playedDead,!dead&&playedDead);
        dyingActive=dying;playedDead=dead;
        return result;
    }
    public void reset() {dyingActive=playedDead=false;}
}
