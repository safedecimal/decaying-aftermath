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
        // Match the recorded CU branches; unverified vitals must not select the heavy track.
        float bleeding = 0;
        for (var part:body.parts()) bleeding += part.bleeding() + part.internalBleeding();
        return body.racingHeart() || body.oxygen() < .5f || hemorrhageCritical(body.blood(), bleeding)
                || body.mode() == BodySnapshot.Mode.LAST_STANDING || body.mode() == BodySnapshot.Mode.DYING;
    }

    static boolean hemorrhageCritical(float blood,float totalBleeding) {
        return blood < .3f && totalBleeding > 0;
    }

    /** One recording per beat: the blackout monitor combines the ECG and heavy thump. */
    public static String beatSound(boolean woundViewOpen, BodySnapshot body) {
        if (body.mode() == BodySnapshot.Mode.DEAD || body.bpm() <= 0) return null;
        if (body.mode() == BodySnapshot.Mode.UNCONSCIOUS) return "heartbeat_monitor";
        if (criticalAlarm(body)) return woundViewOpen ? "heartbeat_monitor" : "heartbeat_heavy";
        return woundViewOpen ? "heartbeat" : null;
    }

    // Visual feedback shares the beat clock, not the inventory-dependent audio policy.
    static boolean heartbeatVision(boolean lastStandIntro, BodySnapshot body) {
        return !lastStandIntro && body.mode() != BodySnapshot.Mode.DEAD
                && enhancedPulse(body);
    }

    public static boolean enhancedPulse(BodySnapshot body) {
        return body!=null && body.mode()!=BodySnapshot.Mode.DEAD && Float.isFinite(body.bpm())
                && body.bpm()>0 && (body.bpm()>=100 || criticalAlarm(body));
    }
    static boolean crossedBeat(double phase,double advance) {
        // Compare the unwrapped phase so a tick straddling the cycle boundary cannot miss a beat.
        return Math.floor(phase+advance-.3)>Math.floor(phase-.3);
    }

    /** MusicManager.Update: two crossfading looping sources, not an unconscious-entry one-shot. */
    public static boolean criticalAmbience(BodySnapshot body) {
        return body.mode()!=BodySnapshot.Mode.DYING && body.mode()!=BodySnapshot.Mode.DEAD
                && criticalAlarm(body) && body.bpm() > 5;
    }
    public static double blackOverlay(BodySnapshot body,double black,boolean treatmentOpen) {
        double alpha=Double.isFinite(black)?Math.clamp(black,0,1):0;
        // Dying is a recoverable Minecraft treatment window, not CU's forced unconscious fast-forward.
        return treatmentOpen&&body.mode()==BodySnapshot.Mode.DYING?Math.min(.35,alpha):alpha;
    }
    /** Recovery HUD is state-owned: neither fade time nor progress may hide the sleep circle. */
    public static double sleepCircleAlpha(BodySnapshot body,boolean lastStandIntro) {
        if(body==null||lastStandIntro||body.mode()==BodySnapshot.Mode.DEAD
                ||body.mode()==BodySnapshot.Mode.LAST_STANDING)return 0;
        return body.mode()==BodySnapshot.Mode.UNCONSCIOUS||body.mode()==BodySnapshot.Mode.DYING
                ||body.cardiacArrest()?1:0;
    }
    /** The first blackout packet must not leave a visible world while waiting for client fades. */
    public static double sleepBlackout(BodySnapshot body,double fadedBlack,double relief) {
        double black=Double.isFinite(fadedBlack)?Math.clamp(fadedBlack,0,1):0;
        if(body!=null&&body.mode()==BodySnapshot.Mode.UNCONSCIOUS) {
            black=Math.max(.965,black);
            if(Double.isFinite(relief))black-=Math.clamp(relief,0,.13);
        }
        return Math.max(0,black);
    }
}
