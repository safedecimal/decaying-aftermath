package io.github.safedecimal.decayingaftermath.medical;

/** A 0%-consciousness blackout is not the authoritative terminal DEAD state. */
public final class MedicalDeathPolicy {
    private MedicalDeathPolicy() { }
    public static boolean rejectBlackoutDeath(BodySnapshot body,boolean explicitLethalSource) {
        return body!=null && body.mode()!=BodySnapshot.Mode.DEAD && !explicitLethalSource
                && body.consciousness()<.005f;
    }
}
