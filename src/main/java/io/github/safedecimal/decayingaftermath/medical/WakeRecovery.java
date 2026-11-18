package io.github.safedecimal.decayingaftermath.medical;

/** Shared server/HUD recovery gates; the countdown counts stable server ticks, not wound healing. */
public final class WakeRecovery {
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
    public static int remainingSeconds(BodySnapshot body) {
        int ticks = Math.round(body.consciousnessProgress() * BodyModel.WAKE_UP_TICKS);
        return Math.max(0, (BodyModel.WAKE_UP_TICKS - ticks + 19) / 20);
    }
    public static String labelKey(BodySnapshot body) {
        return switch (blocker(body)) {
            case NONE -> "decaying_aftermath.recovery.wake_in";
            case BLOOD -> "decaying_aftermath.recovery.blood";
            case OXYGEN -> "decaying_aftermath.recovery.oxygen";
            case INJURY -> "decaying_aftermath.recovery.injury";
            case CONSCIOUSNESS -> "decaying_aftermath.recovery.consciousness";
        };
    }
}
