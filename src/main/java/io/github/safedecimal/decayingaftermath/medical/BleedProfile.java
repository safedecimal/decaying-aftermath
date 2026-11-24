package io.github.safedecimal.decayingaftermath.medical;

/** Independent external wound severities, sharing one normalized budget per body part. */
public record BleedProfile(float capillary, float venous, float arterial) {
    public static final BleedProfile NONE = new BleedProfile(0, 0, 0);
    private static final float BASE_CLOT = .0000125f;

    public BleedProfile {
        // Keep the most serious injury visible when a limb's severity budget is saturated.
        arterial = unit(arterial);
        venous = Math.min(unit(venous), 1 - arterial);
        capillary = Math.min(unit(capillary), 1 - arterial - venous);
    }

    /** Untyped wounds keep the old game's flow and clotting rate. */
    public static BleedProfile legacy(float severity) {
        return severity == 0 ? NONE : new BleedProfile(0, severity, 0);
    }

    static BleedProfile fromImpact(DamageKind kind, float damage) {
        float bleed = damage * kind.surfaceBleed;
        if (bleed <= 0) return NONE;
        // Gameplay thresholds in mitigated Minecraft units, not anatomical predictions.
        if (bleed < .045f) return new BleedProfile(bleed, 0, 0);
        if (bleed >= .12f && (kind == DamageKind.CUT || kind == DamageKind.PUNCTURE)) {
            return new BleedProfile(0, 0, bleed);
        }
        return legacy(bleed);
    }

    public float total() { return Math.min(1, capillary + venous + arterial); }
    /** Effective severity for blood-loss calculations; UI severity uses total(). */
    public float flow() { return capillary * .55f + venous + arterial * 1.6f; }

    BleedProfile add(BleedProfile other) {
        return other == NONE ? this : new BleedProfile(capillary + other.capillary, venous + other.venous, arterial + other.arterial);
    }

    public BleedProfile scale(float factor) {
        if (factor == 0 || total() == 0) return NONE;
        if (factor == 1) return this;
        return new BleedProfile(capillary * factor, venous * factor, arterial * factor);
    }

    BleedProfile reduce(float amount) {
        float total = total();
        return total > amount ? scale(1 - amount / total) : NONE;
    }

    BleedProfile clot(float factor) {
        if (total() == 0) return NONE;
        float base = BASE_CLOT * factor;
        return new BleedProfile(Math.max(0, capillary - base * 4), Math.max(0, venous - base),
                Math.max(0, arterial - base * .5f));
    }

    private static float unit(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite bleed severity");
        return Math.clamp(value, 0, 1);
    }
}
