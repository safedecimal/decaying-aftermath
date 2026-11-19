package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;

/** Unscaled visual state from PlayerCamera.HandleUnconsciousScreen / LastStandSequence. */
public final class MedicalPresentationTimeline {
    public static final double LAST_STAND_BLACK_SECONDS = 2;
    public static final double LAST_STAND_INTRO_SECONDS = 6.7;
    public static final double LAST_STAND_FADE_SECONDS = 4;
    private double black, circleOpacity, vignette;
    private static final double FIRST_GLIMPSE_SECONDS = 2.5;
    private static final double GLIMPSE_PERIOD_SECONDS = 4.6;
    private static final double GLIMPSE_DURATION_SECONDS = 1.4;
    private static final double GLIMPSE_PEAK_RELIEF = .13;
    private double lastStandSeconds = -1;
    private double unconsciousSeconds = -1;

    public void beginLastStand() { lastStandSeconds = 0; }

    public void advance(Mode mode, double seconds) {
        advance(mode,mode==Mode.UNCONSCIOUS?.25:mode==Mode.DEAD?0:1,1,seconds);
    }
    public void advance(BodySnapshot body,double seconds) {
        advance(body.mode(),body.consciousness(),body.consciousnessProgress(),seconds);
    }
    private void advance(Mode mode,double consciousness,double remainingGrace,double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return;
        unconsciousSeconds = mode != Mode.UNCONSCIOUS ? -1
                : unconsciousSeconds < 0 ? 0 : unconsciousSeconds + seconds;
        if (mode == Mode.DYING || mode == Mode.DEAD) lastStandSeconds=-1;
        if (lastStandSeconds >= 0) lastStandSeconds += seconds;
        if(mode==Mode.DEAD) {
            black=towards(black,1,seconds*2);vignette=towards(vignette,.75,seconds*.4);circleOpacity=0;
            return;
        }
        if(mode==Mode.DYING) {
            double loss=1-Math.clamp(remainingGrace,0,1);
            double focus=Math.clamp((.7-consciousness)*2.1,0,.55);
            black=towards(black,Math.max(focus,.12+.43*loss),seconds*.3);
            vignette=towards(vignette,.45+.55*loss,seconds*.4);circleOpacity=0;
            return;
        }
        boolean unconscious = mode == Mode.UNCONSCIOUS;
        double focus=unconscious?.965:Math.clamp((.7-consciousness)*2.1,0,.965);
        black = towards(black, focus, seconds * (unconscious ? 2 : .8));
        vignette = towards(vignette, unconscious ? 1 : 0, seconds * .4);
        circleOpacity = towards(circleOpacity, unconscious ? 1 : 0, seconds * 4);
    }

    public double black() { return black; }
    // Sample between client ticks so the opening and closing stay smooth at render rate.
    public double blackoutRelief(double secondsSinceTick) {
        if (unconsciousSeconds < 0 || lastStandActive()) return 0;
        double afterFirst = unconsciousSeconds + secondsSinceTick - FIRST_GLIMPSE_SECONDS;
        if (afterFirst <= 0) return 0;
        double phase = afterFirst % GLIMPSE_PERIOD_SECONDS;
        if (phase >= GLIMPSE_DURATION_SECONDS) return 0;
        double opening = Math.sin(Math.PI * phase / GLIMPSE_DURATION_SECONDS);
        return GLIMPSE_PEAK_RELIEF * opening * opening;
    }
    public double vignette() { return vignette; }
    public double circleAlpha() { return circleOpacity; }
    public boolean lastStandActive() { return lastStandSeconds >= 0 && lastStandSeconds < 10.7; }
    public boolean droneDue() { return lastStandSeconds > LAST_STAND_BLACK_SECONDS + 1e-9; }
    public int lastStandFrame() {
        if (!lastStandActive() || lastStandSeconds <= LAST_STAND_BLACK_SECONDS) return 0;
        return (int) Math.floor(lastStandSeconds * 8) % 3 + 1;
    }
    public double lastStandBrightness() {
        return lastStandSeconds < LAST_STAND_INTRO_SECONDS
                ? Math.clamp((lastStandSeconds - LAST_STAND_BLACK_SECONDS) * .25, 0, 1) : 1;
    }
    public double lastStandAlpha() {
        if (!lastStandActive()) return 0;
        return lastStandSeconds <= LAST_STAND_INTRO_SECONDS ? 1
                : Math.clamp(1 - (lastStandSeconds - LAST_STAND_INTRO_SECONDS) * .25, 0, 1);
    }
    public boolean lastStandFlicker() { return lastStandSeconds > 2 && lastStandSeconds < 6.7; }
    public void reset() {
        black = circleOpacity = vignette = 0;
        lastStandSeconds = unconsciousSeconds = -1;
    }
    private static double towards(double value, double target, double maximum) {
        return value + Math.copySign(Math.min(Math.abs(target - value), maximum), target - value);
    }
}
