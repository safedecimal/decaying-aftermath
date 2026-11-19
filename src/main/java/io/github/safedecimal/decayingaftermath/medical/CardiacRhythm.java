package io.github.safedecimal.decayingaftermath.medical;

/** Fictional game rhythm. Signed timer keeps saves and the wire format compact. */
public final class CardiacRhythm {
    public static final int WARNING = 40, COLLAPSE = 200, RESCUE = 900, RECOVERY = 440;
    private CardiacRhythm() {}

    public static boolean unstable(int ticks) { return ticks >= WARNING && ticks < COLLAPSE; }
    public static boolean shockable(int ticks) { return unstable(ticks) || ticks < 0; }
    public static int sanitize(int ticks) {
        int bounded = Math.clamp(ticks, -RESCUE, RECOVERY);
        return bounded == COLLAPSE ? 0 : bounded;
    }
    public static int advance(int ticks, boolean strain) {
        if (ticks < 0) return ticks + 1;
        if (ticks > COLLAPSE) return ticks == COLLAPSE + 1 ? 0 : ticks - 1;
        if (!strain) return Math.max(0, ticks - 3);
        return ticks + 1 >= COLLAPSE ? -RESCUE : ticks + 1;
    }
    public static float racingBpm(int ticks) {
        return Math.clamp(160 + (ticks - WARNING) * .3f + ((ticks / 7) % 3 - 1) * 9, 150, 220);
    }
}
