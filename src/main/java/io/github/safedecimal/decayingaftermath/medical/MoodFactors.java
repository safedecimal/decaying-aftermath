package io.github.safedecimal.decayingaftermath.medical;

/** Bitmask of active mood-shaping factors synced from server to client tooltips. */
public final class MoodFactors {
    private MoodFactors() { }

    public static final int DARKNESS = 1 << 0;
    public static final int CAVE = 1 << 1;
    public static final int HOSTILES = 1 << 2;
    public static final int EERIE_MUSIC = 1 << 3;
    public static final int BAD_FOOD = 1 << 4;
    public static final int SLEEP_DEPRIVED = 1 << 5;
    public static final int COLD_OR_RAIN = 1 << 6;
    public static final int PAIN_OR_ILLNESS = 1 << 7;
    public static final int WITHDRAWAL = 1 << 8;

    public static final int DAYLIGHT = 1 << 9;
    public static final int GOOD_MUSIC = 1 << 10;
    public static final int GOOD_FOOD = 1 << 11;
    public static final int SLEEPING = 1 << 12;
    public static final int COZY_WARMTH = 1 << 13;
    public static final int NATURE = 1 << 14;
    public static final int THERAPY = 1 << 15;
    public static final int SEDATIVE = 1 << 16;

    public static final int NEGATIVE_MASK = DARKNESS | CAVE | HOSTILES | EERIE_MUSIC | BAD_FOOD
            | SLEEP_DEPRIVED | COLD_OR_RAIN | PAIN_OR_ILLNESS | WITHDRAWAL;
    public static final int POSITIVE_MASK = DAYLIGHT | GOOD_MUSIC | GOOD_FOOD | SLEEPING
            | COZY_WARMTH | NATURE | THERAPY | SEDATIVE;
}
