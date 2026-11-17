package io.github.safedecimal.decayingaftermath.medical;

/** Resource units, not completion percentages. Twelve full CU bandage turns use one roll. */
public final class MedicalSupplyPolicy {
    public static final float EMPTY = .0001f;
    private MedicalSupplyPolicy() {}
    public static boolean wrap(TreatmentKind kind) {
        return kind == TreatmentKind.BANDAGE || kind == TreatmentKind.PRESSURE_DRESSING;
    }
    public static boolean installation(TreatmentKind kind) {
        return kind == TreatmentKind.SPLINT || kind == TreatmentKind.TOURNIQUET;
    }
    public static int ticksPerFill(TreatmentKind kind) {
        return wrap(kind) ? 240 : kind == TreatmentKind.SPLINT ? 80 : 120;
    }
    public static float treatmentAmount(TreatmentKind kind, float supply) {
        return fill(supply) * (wrap(kind) ? 12 : 1);
    }
    public static float wrapCost(double normalTurns) {
        return Double.isFinite(normalTurns) && normalTurns > 0 ? (float)(normalTurns / 12) : 0;
    }
    public static float fill(float value) {
        return Float.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
    }
    public record Spend(float used, float remaining, boolean exhausted) {}
    public static Spend spend(float remaining, float requested) {
        float available = fill(remaining);
        if (!Float.isFinite(requested) || requested <= 0) return new Spend(0, available, available <= EMPTY);
        float used = Math.min(available, requested), left = Math.max(0, available - used);
        // The final floating-point remainder belongs to this item, never to a fresh stack unit.
        if (left <= EMPTY) return new Spend(available, 0, true);
        return new Spend(used, left, false);
    }
}
