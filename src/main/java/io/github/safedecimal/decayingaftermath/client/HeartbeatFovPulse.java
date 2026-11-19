package io.github.safedecimal.decayingaftermath.client;

/** A gentle, frame-sampled contraction driven by the same beat clock as audio. */
public final class HeartbeatFovPulse {
    private static final long ATTACK_NANOS = 90_000_000L;
    private static final long RELEASE_NANOS = 180_000_000L;
    private static final double PEAK_ZOOM = .014;

    private boolean triggered;
    private long lastBeatNanos;
    private double attackStart;

    void onBeat(long now) {
        // Preserve continuity if another beat lands before the last release has finished.
        attackStart = envelope(now);
        lastBeatNanos = now;
        triggered = true;
    }

    void reset() {
        triggered = false;
    }

    double zoom(double effectScale, long now) {
        if (!Double.isFinite(effectScale) || effectScale <= 0) return 1;
        return 1 + PEAK_ZOOM * Math.min(effectScale, 1) * envelope(now);
    }

    private double envelope(long now) {
        long elapsed = now - lastBeatNanos;
        if (!triggered || elapsed < 0 || elapsed >= ATTACK_NANOS + RELEASE_NANOS) return 0;
        if (elapsed < ATTACK_NANOS) {
            return attackStart + (1 - attackStart) * smoothstep((double) elapsed / ATTACK_NANOS);
        }
        return 1 - smoothstep((double) (elapsed - ATTACK_NANOS) / RELEASE_NANOS);
    }

    private static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Match the change in apparent scale despite different vanilla world and hand angles. */
    public static float applyZoom(float vanillaFov, double zoom) {
        if (zoom == 1) return vanillaFov;
        return (float) Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(vanillaFov) * .5) / zoom));
    }
}
