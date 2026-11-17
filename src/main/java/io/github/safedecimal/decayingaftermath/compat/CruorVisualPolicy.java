package io.github.safedecimal.decayingaftermath.compat;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;

/** Cosmetic native flow, not a second health/bleeding simulation. */
public final class CruorVisualPolicy {
    private CruorVisualPolicy() {}
    public static float nativeBleed(float total) {
        return Float.isFinite(total) ? Math.clamp(total * 100, 0, 100) : 0;
    }
    public static float nativeBleed(BodySnapshot snapshot) {
        float total = 0;
        for (var part : snapshot.parts()) total += part.bleeding();
        return nativeBleed(total);
    }
    public static float fallbackHealth(float bleeding, boolean dead, float actual, float maximum) {
        return !dead && bleeding <= .0001f ? maximum : actual;
    }
    public static float fallbackHealth(boolean authoritativePatient, BodySnapshot snapshot, float actual, float maximum) {
        return authoritativePatient ? fallbackHealth(snapshot, actual, maximum) : actual;
    }
    public static float fallbackHealth(BodySnapshot snapshot, float actual, float maximum) {
        return fallbackHealth(nativeBleed(snapshot) / 100, snapshot.mode() == BodySnapshot.Mode.DEAD, actual, maximum);
    }
}
