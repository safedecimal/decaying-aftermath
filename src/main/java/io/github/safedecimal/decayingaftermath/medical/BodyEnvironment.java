package io.github.safedecimal.decayingaftermath.medical;

/** Server-observed environment. Temperature is a game approximation in Celsius, not a biome reading. */
public record BodyEnvironment(float ambientTemperature, float insulation, boolean wet, boolean breathable,
        boolean sleeping, boolean sprinting, boolean moving, float carriedMass, float carryingCapacity,
        boolean metabolismEnabled, boolean temperatureEnabled,
        int lightLevel, boolean daylightSurface, boolean nearCampfire, boolean undergroundCave,
        boolean naturalGreenery, int hostileMobsNearby, float jukeboxMood, int sleepDeprivedTicks,
        boolean mentalHealthEnabled) {
    public BodyEnvironment(float ambientTemperature, float insulation, boolean wet, boolean breathable,
            boolean sleeping, boolean sprinting, boolean moving, float carriedMass, float carryingCapacity,
            boolean metabolismEnabled, boolean temperatureEnabled) {
        this(ambientTemperature, insulation, wet, breathable, sleeping, sprinting, moving,
                carriedMass, carryingCapacity, metabolismEnabled, temperatureEnabled,
                15, true, false, false, false, 0, 0, 0, false);
    }

    public BodyEnvironment {
        if (!Float.isFinite(ambientTemperature) || !Float.isFinite(insulation)
                || !Float.isFinite(carriedMass) || !Float.isFinite(carryingCapacity)
                || !Float.isFinite(jukeboxMood))
            throw new IllegalArgumentException("Non-finite environment");
        ambientTemperature=Math.clamp(ambientTemperature,-60,100);
        insulation=Math.clamp(insulation,0,1);
        carriedMass=Math.clamp(carriedMass,0,10000);
        carryingCapacity=Math.clamp(carryingCapacity,1,1000);
        lightLevel=Math.clamp(lightLevel,0,15);
        hostileMobsNearby=Math.max(0,hostileMobsNearby);
        jukeboxMood=Math.clamp(jukeboxMood,-1,1);
        sleepDeprivedTicks=Math.max(0,sleepDeprivedTicks);
    }
    public static BodyEnvironment temperate(boolean sprinting, boolean moving) {
        return new BodyEnvironment(20,.25f,false,true,false,sprinting,moving,0,32,true,true);
    }
    public float encumbrance() { return Math.clamp(carriedMass/carryingCapacity,0,10); }
}
