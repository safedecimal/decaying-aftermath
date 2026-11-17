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
    /** Vanilla potions keep their bottle/effect lifecycle and are never repacked. */
    public static boolean inventorySupply(TreatmentKind kind) {
        return kind != null && kind != TreatmentKind.POTION
                && kind != TreatmentKind.HEALING_POTION && kind != TreatmentKind.REGENERATION_POTION;
    }
    public static double material(int count, float perUnitFill) {
        return (double) Math.max(0, count) * fill(perUnitFill);
    }
    public record Packed(int fullUnits, float residual) { }
    /** Combine material, not item counts. Never round a residual upward into a new unit. */
    public static Packed repack(double material) {
        if (!Double.isFinite(material) || material <= 0 || material > Integer.MAX_VALUE)
            return new Packed(0, 0);
        int fullUnits = (int) Math.floor(material);
        double exactResidual = material - fullUnits;
        float residual = (float) exactResidual;
        if ((double) residual > exactResidual) residual = Math.nextDown(residual);
        return new Packed(fullUnits, Math.max(0, residual));
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
