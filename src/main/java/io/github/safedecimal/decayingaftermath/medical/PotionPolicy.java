package io.github.safedecimal.decayingaftermath.medical;

/** Shared, testable policy: drinking is permitted, but medicinal effects are wasted. */
public final class PotionPolicy {
    public static final String WASTED_HEALING_THOUGHT = "It's seems didn't help..";
    private PotionPolicy() {}
    public static boolean isMedical(boolean instantHealing, boolean regeneration) {
        return instantHealing || regeneration;
    }
    public static boolean permitsHealing(boolean medical, boolean appliedToBody) {
        return !medical || appliedToBody;
    }
    public static boolean emitsThought(boolean medical, boolean finishedDrinking) {
        return medical && finishedDrinking;
    }
}
