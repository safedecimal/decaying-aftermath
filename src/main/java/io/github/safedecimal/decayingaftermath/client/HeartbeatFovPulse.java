package io.github.safedecimal.decayingaftermath.client;

/** A short contraction triggered by the existing audio beat, with no independent phase. */
final class HeartbeatFovPulse {
    private static final long DURATION_NANOS = 220_000_000L;
    private static final double PEAK_CONTRACTION = .03;

    private boolean triggered;
    private long lastBeatNanos;

    void onBeat(long now) {
        lastBeatNanos = now;
        triggered = true;
    }

    void reset() {
        triggered = false;
    }

    float apply(float vanillaFov, double effectScale, long now) {
        long elapsed = now - lastBeatNanos;
        if (!triggered || elapsed < 0 || elapsed >= DURATION_NANOS || effectScale <= 0) return vanillaFov;
        double remaining = 1 - (double) elapsed / DURATION_NANOS;
        return (float) (vanillaFov * (1 - PEAK_CONTRACTION * effectScale * remaining * remaining));
    }
}
