package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodyModel;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;

/** CU's brainDying/dead split, adapted to Minecraft's server-owned rescue countdown. */
public final class MedicalDeathMusicState {
    // DrawYourLastBreath1 lasts 19.93 seconds. Progress is remaining ticks / total ticks.
    static final float PRE_DEATH_PROGRESS = (20 * 20f) / BodyModel.DYING_TICKS;
    public record Actions(boolean startDying, boolean stopDying, boolean playDead, boolean stopDead) { }
    private boolean dyingActive, playedDying, playedDead;

    static boolean preDeathWindow(Mode mode, float progress) {
        return mode == Mode.DYING && progress >= 0 && progress <= PRE_DEATH_PROGRESS;
    }

    /** State-only compatibility entry point for callers without a countdown snapshot. */
    public Actions advance(Mode mode) { return advance(mode, mode == Mode.DYING ? 0 : 1); }
    public Actions advance(BodySnapshot body) {
        // CU brainDying is absent perfusion and unconsciousness, not only a mode countdown.
        if(body.cardiacArrest())return advance(Mode.DYING,0);
        if(body.mode()!=Mode.DYING&&body.mode()!=Mode.DEAD)playedDying=false;
        return advance(body.mode(),body.consciousnessProgress());
    }

    public Actions advance(Mode mode, float progress) {
        boolean terminal = preDeathWindow(mode, progress), dead = mode == Mode.DEAD;
        // Brief stabilization pauses the same countdown. Only a fresh countdown rearms this cue.
        if (mode == Mode.DYING && progress > PRE_DEATH_PROGRESS) playedDying = false;
        boolean start = terminal && !playedDying;
        var actions = new Actions(start, !terminal && dyingActive, dead && !playedDead, !dead && playedDead);
        dyingActive = terminal && (dyingActive || start);
        playedDying |= start;
        playedDead = dead;
        return actions;
    }

    public boolean preDeathStarted() { return playedDying; }
    public void reset() { dyingActive = playedDying = playedDead = false; }
}
