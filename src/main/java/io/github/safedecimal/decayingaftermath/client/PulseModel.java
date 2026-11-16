package io.github.safedecimal.decayingaftermath.client;

/**
 * Temporary, client-side pulse simulation, not a health or medical measurement.
 * Condition currently describes pulse strain only; vanilla hit points are not an input.
 */
public final class PulseModel {
    public static final double RESTING_BPM = 72.0;
    public static final double TICK_SECONDS = 1.0 / 20.0;

    private double bpm = RESTING_BPM;
    private double beatPhase;

    public void tick(double movementIntensity, boolean sprinting, boolean swimming, boolean alive) {
        if (!alive) {
            bpm = 0.0;
            beatPhase = 0.0;
            return;
        }

        double activity = Double.isFinite(movementIntensity)
                ? Math.clamp(movementIntensity, 0.0, 1.0) : 0.0;
        double target = RESTING_BPM + activity * 22.0;
        if (sprinting && activity > 0.05) {
            target += activity * 50.0;
        }
        if (swimming && activity > 0.05) {
            target += activity * 16.0;
        }

        // Rising pulse reacts faster than recovery. Both are independent of frame rate.
        double responseSeconds = target >= bpm ? 3.5 : 8.0;
        bpm += (target - bpm) * (1.0 - Math.exp(-TICK_SECONDS / responseSeconds));
        bpm = Math.clamp(bpm, 0.0, 190.0);
        beatPhase = (beatPhase + bpm / 60.0 * TICK_SECONDS) % 1.0;
    }

    public void reset() {
        bpm = RESTING_BPM;
        beatPhase = 0.0;
    }

    public int bpm() {
        return (int) Math.round(bpm);
    }

    public double bpmExact() {
        return bpm;
    }

    public double beatPhase() {
        return beatPhase;
    }

    public double condition() {
        return conditionForBpm(bpm);
    }

    public String conditionTranslationKey() {
        return conditionTranslationKey(condition());
    }

    /** Game-only placeholder scoring until the other condition systems are added. */
    public static double conditionForBpm(double rate) {
        if (!Double.isFinite(rate) || rate <= 0.0) {
            return 0.0;
        }
        double strain = rate > 100.0 ? (rate - 100.0) / 100.0
                : rate < 50.0 ? (50.0 - rate) / 50.0 : 0.0;
        return Math.clamp(1.0 - strain, 0.0, 1.0);
    }

    public static String conditionTranslationKey(double condition) {
        if (condition >= 0.9) {
            return "decaying_aftermath.condition.stable";
        }
        if (condition >= 0.55) {
            return "decaying_aftermath.condition.strained";
        }
        return "decaying_aftermath.condition.critical";
    }
}
