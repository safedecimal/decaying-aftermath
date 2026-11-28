package io.github.safedecimal.decayingaftermath.medical;

/** Stable ordinals are used in saved anatomy and network snapshots. */
public enum BodyPart {
    HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG;

    public boolean isArm() { return this == LEFT_ARM || this == RIGHT_ARM; }
    public boolean isLeg() { return this == LEFT_LEG || this == RIGHT_LEG; }
    public boolean isLimb() { return isArm() || isLeg(); }
    public boolean isVital() { return this == HEAD || this == TORSO; }
    public String translationKey() { return "abjection.body." + name().toLowerCase(java.util.Locale.ROOT); }
}
