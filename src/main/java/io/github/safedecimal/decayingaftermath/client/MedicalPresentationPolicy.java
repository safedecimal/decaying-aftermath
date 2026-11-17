package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;

/** CU Body.Update / isCriticallyDying branches, using the physiology this mod actually models. */
public final class MedicalPresentationPolicy {
    private MedicalPresentationPolicy() {}

    public static boolean inventoryPulse(boolean inventoryOpen, BodySnapshot body) {
        return inventoryOpen && body.mode() != BodySnapshot.Mode.DEAD && body.bpm() > 0;
    }

    public static boolean criticalAlarm(BodySnapshot body) {
        if (body.mode() == BodySnapshot.Mode.DEAD || body.bpm() <= 0) return false;
        // CU: oxygen < 50%, or active bleeding with blood volume < 30%.
        // Pain/sedation unconsciousness alone is NOT isCriticallyDying.
        // Blood pressure, fibrillation, temperature etc. are not invented from condition().
        boolean bleeding = java.util.Arrays.stream(body.parts()).anyMatch(part -> part.bleeding() > 0);
        return body.oxygen() < .5f || (body.blood() < .3f && bleeding)
                || body.mode() == BodySnapshot.Mode.LAST_STANDING;
    }

    /** One recording per beat, never physical pulse + monitor simultaneously. */
    public static String beatSound(boolean inventoryOpen, BodySnapshot body) {
        if (body.mode() == BodySnapshot.Mode.DEAD || body.bpm() <= 0) return null;
        if (criticalAlarm(body)) return inventoryOpen ? "heartbeat_monitor" : "heartbeat_heavy";
        return inventoryPulse(inventoryOpen, body) ? "heartbeat" : null;
    }

    /** MusicManager.Update: two crossfading looping sources, not an unconscious-entry one-shot. */
    public static boolean criticalAmbience(BodySnapshot body) {
        return criticalAlarm(body) && body.bpm() > 5;
    }
}
