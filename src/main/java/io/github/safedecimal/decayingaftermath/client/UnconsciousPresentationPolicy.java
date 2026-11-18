package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;

/**
 * What a body without consciousness still perceives. A fainted player keeps the CU medical
 * cues but loses the world: mobs, blocks, records, vanilla music, action-bar hints, titles,
 * subtitles and toasts. Critical physiology also silences Minecraft's own soundtrack, because
 * a heartbeat alarm or a death score must not compete with it.
 */
public final class UnconsciousPresentationPolicy {
    private UnconsciousPresentationPolicy() {}

    /** Every sound from outside this mod is silenced while fainted; CU cues stay audible. */
    public static boolean worldSoundsMuted(BodySnapshot body) {
        return body != null && body.mode() == BodySnapshot.Mode.UNCONSCIOUS;
    }

    /** Blackout, critical physiology and death own the audio, so vanilla music stays off. */
    public static boolean vanillaMusicMuted(BodySnapshot body) {
        if (body == null) return false;
        return worldSoundsMuted(body) || body.mode() == BodySnapshot.Mode.DEAD
                || MedicalPresentationPolicy.criticalAlarm(body);
    }

    /** Action bar, titles and toasts are "подсказки": a fainted player sees none of them. */
    public static boolean hintsHidden(BodySnapshot body) {
        return worldSoundsMuted(body);
    }

    /** True when this instance may keep sounding; only this mod survives a blackout. */
    public static boolean keepsPlayback(BodySnapshot body, SoundInstance instance) {
        return !worldSoundsMuted(body) || isModSound(instance);
    }

    public static boolean isModSound(SoundInstance instance) {
        if (instance == null) return false;
        Identifier location = instance.getIdentifier();
        return location != null && DecayingAftermath.MOD_ID.equals(location.getNamespace());
    }

    private static BodySnapshot liveBody() {
        return ClientMedicalState.received() ? ClientMedicalState.snapshot() : null;
    }

    public static boolean worldSoundsMutedNow() { return worldSoundsMuted(liveBody()); }
    public static boolean vanillaMusicMutedNow() { return vanillaMusicMuted(liveBody()); }
    public static boolean hintsHiddenNow() { return hintsHidden(liveBody()); }
    public static boolean keepsPlaybackNow(SoundInstance instance) {
        return keepsPlayback(liveBody(), instance);
    }
}
