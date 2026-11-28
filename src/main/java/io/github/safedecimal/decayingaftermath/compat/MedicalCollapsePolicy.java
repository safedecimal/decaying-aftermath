package io.github.safedecimal.decayingaftermath.compat;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.PartSnapshot;

/** Sleeping is not a medical collapse; incapable-of-walking trauma collapses into a ragdoll. */
public final class MedicalCollapsePolicy {
    public enum Posture { NORMAL, INJURED, COLLAPSED, SLEEPING }

    private MedicalCollapsePolicy() {}

    public static boolean collapsed(Mode mode, boolean alive, boolean sleeping, boolean exempt) {
        return alive && !sleeping && !exempt && mode == Mode.UNCONSCIOUS;
    }

    public static boolean collapsed(BodySnapshot snapshot, boolean alive, boolean sleeping, boolean exempt) {
        return posture(snapshot, alive, sleeping, exempt) == Posture.COLLAPSED;
    }

    public static Posture posture(BodySnapshot snapshot, boolean alive, boolean sleeping, boolean exempt) {
        if (!alive || sleeping || exempt || snapshot == null || snapshot.mode() == Mode.DEAD) {
            return Posture.NORMAL;
        }
        if (cannotWalk(snapshot)) return Posture.COLLAPSED;
        if (snapshot.voluntarySleeping()) return Posture.SLEEPING;
        if (severelyInjuredWalkable(snapshot)) return Posture.INJURED;
        return Posture.NORMAL;
    }

    /** True when real-world physiology prevents standing or walking at all. */
    public static boolean cannotWalk(BodySnapshot snapshot) {
        if (snapshot == null) return false;
        Mode mode = snapshot.mode();
        if (mode == Mode.DEAD) return false;
        if (mode == Mode.UNCONSCIOUS || mode == Mode.DYING) return true;
        PartSnapshot leftLeg = snapshot.part(BodyPart.LEFT_LEG);
        PartSnapshot rightLeg = snapshot.part(BodyPart.RIGHT_LEG);
        if (legIncapacitated(leftLeg) && legIncapacitated(rightLeg)) return true;
        if ((leftLeg.integrity() <= 0.15f || rightLeg.integrity() <= 0.15f) && snapshot.energy() <= 0.12f) return true;
        if (mode != Mode.LAST_STANDING) {
            if (snapshot.survival().encumbrance() >= 2.5f) return true;
            if (snapshot.pain() >= 0.85f) return true;
            if (snapshot.blood() <= 0.22f || snapshot.oxygen() <= 0.25f) return true;
            if (snapshot.energy() <= 0.02f && (snapshot.pain() >= 0.65f || snapshot.blood() <= 0.35f)) return true;
        }
        return false;
    }

    /** True when badly injured/suffering, yet still capable of walking/limping. */
    public static boolean severelyInjuredWalkable(BodySnapshot snapshot) {
        if (snapshot == null || snapshot.mode() == Mode.DEAD || cannotWalk(snapshot)) return false;
        if (snapshot.mode() == Mode.LAST_STANDING) return true;
        if (snapshot.condition() < 0.65f || snapshot.pain() >= 0.35f
                || snapshot.blood() < 0.65f || snapshot.oxygen() < 0.70f || snapshot.energy() < 0.25f) {
            return true;
        }
        for (PartSnapshot part : snapshot.parts()) {
            if (part.integrity() < 0.65f || part.fractured() || part.bleeding() >= 0.02f
                    || part.burn() >= 0.35f || part.infection() >= 0.45f) {
                return true;
            }
        }
        return false;
    }

    private static boolean legIncapacitated(PartSnapshot leg) {
        return leg.integrity() <= 0.25f || (leg.fractured() && !leg.splinted()) || leg.anesthetized();
    }
}
