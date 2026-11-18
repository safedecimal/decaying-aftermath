package io.github.safedecimal.decayingaftermath.medical;

/** Server-observed environment. Temperature is a game approximation in Celsius, not a biome reading. */
public record BodyEnvironment(float ambientTemperature, float insulation, boolean wet, boolean breathable,
        boolean sleeping, boolean sprinting, boolean moving, float carriedMass, float carryingCapacity,
        boolean metabolismEnabled, boolean temperatureEnabled) {
    public BodyEnvironment {
        if (!Float.isFinite(ambientTemperature) || !Float.isFinite(insulation)
                || !Float.isFinite(carriedMass) || !Float.isFinite(carryingCapacity))
            throw new IllegalArgumentException("Non-finite environment");
        ambientTemperature=Math.clamp(ambientTemperature,-60,100);
        insulation=Math.clamp(insulation,0,1);
        carriedMass=Math.clamp(carriedMass,0,10000);
        carryingCapacity=Math.clamp(carryingCapacity,1,1000);
    }
    public static BodyEnvironment temperate(boolean sprinting, boolean moving) {
        return new BodyEnvironment(20,.25f,false,true,false,sprinting,moving,0,32,true,true);
    }
    public float encumbrance() { return Math.clamp(carriedMass/carryingCapacity,0,10); }
}
