package io.github.safedecimal.decayingaftermath.medical;

/** Five clinically grounded depression profiles modeled alongside baseline mood reserve. */
public enum DepressionType {
    NONE("none", 1.0f, 1.0f),
    SITUATIONAL("situational", 0.90f, 1.25f),
    SEASONAL("seasonal", 0.70f, 1.0f),
    MELANCHOLIC("melancholic", 0.25f, 0.45f),
    PSYCHOTIC("psychotic", 0.35f, 0.55f),
    PERSISTENT("persistent", 0.55f, 0.70f);

    private final String id;
    private final float positiveGainFactor;
    private final float naturalRecoveryFactor;

    DepressionType(String id, float positiveGainFactor, float naturalRecoveryFactor) {
        this.id = id;
        this.positiveGainFactor = positiveGainFactor;
        this.naturalRecoveryFactor = naturalRecoveryFactor;
    }

    public String id() { return id; }
    public String translationKey() { return "abjection.depression." + id; }
    /** Melancholic anhedonia blunts ordinary positive stimuli; therapy bypasses it. */
    public float positiveGainFactor() { return positiveGainFactor; }
    public float naturalRecoveryFactor() { return naturalRecoveryFactor; }
    public boolean active() { return this != NONE; }
    public boolean severe() { return this == MELANCHOLIC || this == PSYCHOTIC; }
}
