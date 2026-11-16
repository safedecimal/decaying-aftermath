package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;

/** Presentation-only alarm threshold; never changes server physiology. */
public final class MedicalPresentationPolicy {
    public static final float CRITICAL_CONDITION = 0.35f;

    private MedicalPresentationPolicy() {}

    public static boolean inventoryPulse(boolean inventoryOpen, BodySnapshot body) {
        return inventoryOpen && body.mode() != BodySnapshot.Mode.DEAD && body.bpm() > 0;
    }

    public static boolean criticalAlarm(BodySnapshot body) {
        return body.mode() != BodySnapshot.Mode.DEAD && body.bpm() > 0
                && (body.condition() <= CRITICAL_CONDITION
                    || body.mode() == BodySnapshot.Mode.UNCONSCIOUS
                    || body.mode() == BodySnapshot.Mode.LAST_STANDING);
    }
}
