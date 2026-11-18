package io.github.safedecimal.decayingaftermath.medical;

/** Trauma mechanisms, not a second damage/armour calculation. Inputs are already mitigated. */
public enum DamageKind {
    CUT(.030f, .012f, .050f, true, true),
    BLUNT(.030f, 0, .055f, false, true),
    PUNCTURE(.030f, .009f, .050f, true, true),
    BURN(.030f, 0, .060f, false, false),
    EXPLOSION(.030f, .008f, .055f, true, true),
    FROSTBITE(.020f, 0, .035f, false, false),
    CHEMICAL(.025f, 0, .045f, false, false),
    FALL_IMPACT(.030f, .008f, .055f, true, true);

    public final float tissue, surfaceBleed, pain;
    public final boolean opensSkin, fractures;
    DamageKind(float tissue, float surfaceBleed, float pain, boolean opensSkin, boolean fractures) {
        this.tissue=tissue; this.surfaceBleed=surfaceBleed; this.pain=pain;
        this.opensSkin=opensSkin; this.fractures=fractures;
    }
}
