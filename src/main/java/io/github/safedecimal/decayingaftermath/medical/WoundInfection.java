package io.github.safedecimal.decayingaftermath.medical;

/** CU game units adapted to Minecraft's normalized infection value and 20 Hz ticks. */
final class WoundInfection {
    static final int SPRAY_TICKS = 240 * 20;
    static final int INCUBATION_TICKS = 40 * 20;

    private WoundInfection() {}

    static float localDelta(boolean disinfected, boolean foreignBody) {
        // Limb.GetInfectionSpeed: 7.2 percentage points/min times (immunity - 1.7 when treated).
        return (7.2f / (100 * 60 * 20)) * (disinfected ? 1f - 1.7f : 1f) * (foreignBody ? 1.6f : 1f);
    }
}
