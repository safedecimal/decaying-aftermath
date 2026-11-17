package io.github.safedecimal.decayingaftermath.compat;

import java.util.List;
import io.github.safedecimal.decayingaftermath.medical.BodyPart;

/** Weighted selection uses actual per-part bleeding, not injury names or cosmetic HP. */
public final class MedicalBleedOrigins {
    private MedicalBleedOrigins() {}
    public static List<Float> uniform(float total) {
        if (!Float.isFinite(total) || total < 0 || total > 6) throw new IllegalArgumentException("Invalid bleed total");
        return java.util.Collections.nCopies(6, total / 6);
    }
    public static List<Float> checked(List<Float> values) {
        var copy = List.copyOf(values);
        if (copy.size() != 6) throw new IllegalArgumentException("Six bleeding parts required");
        for (float value : copy) if (!Float.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Invalid part bleeding");
        return copy;
    }
    public static float total(List<Float> values) { float sum = 0; for (float value : values) sum += value; return sum; }
    public static BodyPart select(List<Float> bleeding, double fraction) {
        var values = checked(bleeding); float total = total(values);
        if (total <= .0001f) return null;
        if (!Double.isFinite(fraction)) throw new IllegalArgumentException("Invalid sample");
        double pick = Math.clamp(fraction, 0, Math.nextDown(1.0)) * total;
        BodyPart last = null;
        for (BodyPart part : BodyPart.values()) {
            float amount = values.get(part.ordinal());
            if (amount <= 0) continue;
            last = part; if (pick < amount) return part; pick -= amount;
        }
        return last;
    }
    /** Rest-pose part centres derived from shipped Ragdollified LimbAnchors.localOffset. */
    public static double[] restOffset(BodyPart part) {
        return switch (part) {
            case HEAD -> new double[]{0, 1.825, 0};
            case TORSO -> new double[]{0, 1.2, 0};
            case LEFT_ARM -> new double[]{-.375, 1.2, 0};
            case RIGHT_ARM -> new double[]{.375, 1.2, 0};
            case LEFT_LEG -> new double[]{-.11875, .45, 0};
            case RIGHT_LEG -> new double[]{.11875, .45, 0};
        };
    }
}
