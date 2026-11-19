package io.github.safedecimal.decayingaftermath.medical;

/** Gameplay thresholds for unconscious recovery and an earlier, deliberate attempt to stand. */
public final class WakeRecovery {
    static final int EARLY_STAND_TICKS = 40;
    static final float STAND_ENERGY_COST = .04f;
    public enum Blocker { NONE, BLOOD, OXYGEN, INJURY, CONSCIOUSNESS }
    private WakeRecovery() {}
    public static Blocker blocker(float vital, float blood, float oxygen, float consciousness) {
        if (blood <= .32f) return Blocker.BLOOD;
        if (oxygen <= .30f) return Blocker.OXYGEN;
        if (vital <= .12f) return Blocker.INJURY;
        if (consciousness <= .20f) return Blocker.CONSCIOUSNESS;
        return Blocker.NONE;
    }
    public static Blocker blocker(BodySnapshot body) {
        return blocker(Math.min(body.part(BodyPart.HEAD).integrity(), body.part(BodyPart.TORSO).integrity()),
                body.blood(), body.oxygen(), body.consciousness());
    }
    static boolean canStandEarly(BodySnapshot body) {
        return body.mode() == BodySnapshot.Mode.UNCONSCIOUS && body.blood() > .45f && body.oxygen() > .50f
                && body.consciousness() > .30f && body.energy() >= .20f
                && body.part(BodyPart.HEAD).integrity() > .22f && body.part(BodyPart.TORSO).integrity() > .22f
                && (loadBearing(body.part(BodyPart.LEFT_LEG)) || loadBearing(body.part(BodyPart.RIGHT_LEG)));
    }
    private static boolean loadBearing(BodySnapshot.PartSnapshot leg) {
        return leg.integrity() >= .30f && !leg.anesthetized() && (!leg.fractured() || leg.splinted());
    }
}
