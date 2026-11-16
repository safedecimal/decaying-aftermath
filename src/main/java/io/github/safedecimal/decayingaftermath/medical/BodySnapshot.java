package io.github.safedecimal.decayingaftermath.medical;

import java.util.Objects;

/** Game-only quantities are normalized except BPM and the cumulative 0..3 drug burden. */
public record BodySnapshot(float condition, float blood, float oxygen, float pain, float drug,
        float bpm, Mode mode, float consciousnessProgress, PartSnapshot[] parts) {
    public enum Mode { CONSCIOUS, UNCONSCIOUS, LAST_STANDING, DEAD }

    public BodySnapshot {
        condition = unit(condition); blood = unit(blood); oxygen = unit(oxygen); pain = unit(pain);
        drug = finiteClamp(drug, 0, 3); bpm = finiteClamp(bpm, 0, 220);
        consciousnessProgress = unit(consciousnessProgress);
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(parts, "parts");
        if (parts.length != BodyPart.values().length) throw new IllegalArgumentException("Six body parts required");
        parts = parts.clone();
        for (PartSnapshot part : parts) Objects.requireNonNull(part, "part");
    }

    @Override public PartSnapshot[] parts() { return parts.clone(); }
    public PartSnapshot part(BodyPart part) { return parts[Objects.requireNonNull(part).ordinal()]; }

    public record PartSnapshot(float integrity, float bleeding, float pain, boolean fractured,
            boolean bandaged, boolean splinted, boolean tourniquet) {
        public PartSnapshot { integrity = unit(integrity); bleeding = unit(bleeding); pain = unit(pain); }
    }

    private static float unit(float value) { return finiteClamp(value, 0, 1); }
    private static float finiteClamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite body state");
        return Math.clamp(value, minimum, maximum);
    }
}
