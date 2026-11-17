package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;

/** Unscaled visual state from PlayerCamera.HandleUnconsciousScreen / LastStandSequence. */
public final class MedicalPresentationTimeline {
    public static final double LAST_STAND_BLACK_SECONDS = 2;
    public static final double LAST_STAND_INTRO_SECONDS = 6.7;
    public static final double LAST_STAND_FADE_SECONDS = 4;
    private double black, blackSeconds, vignette;
    private double lastStandSeconds = -1;

    public void beginLastStand() { lastStandSeconds = 0; }

    public void advance(Mode mode, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return;
        if (mode == Mode.DEAD) { reset(); return; }
        if (lastStandSeconds >= 0) lastStandSeconds += seconds;
        boolean unconscious = mode == Mode.UNCONSCIOUS;
        black = towards(black, unconscious ? .965 : 0, seconds * (unconscious ? 2 : .8));
        vignette = towards(vignette, unconscious ? 1 : 0, seconds * .4);
        blackSeconds = towards(blackSeconds, unconscious && black >= .965 ? 4 : 0,
                seconds * (unconscious ? 1 : 1.5));
    }

    public double black() { return black; }
    public double vignette() { return vignette; }
    public double circleAlpha() { return Math.clamp((blackSeconds - 1) * .3334, 0, 1); }
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
    public void reset() { black = blackSeconds = vignette = 0; lastStandSeconds = -1; }
    private static double towards(double value, double target, double maximum) {
        return value + Math.copySign(Math.min(Math.abs(target - value), maximum), target - value);
    }
}
