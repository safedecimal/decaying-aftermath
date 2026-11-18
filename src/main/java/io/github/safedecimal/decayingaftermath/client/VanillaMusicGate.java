package io.github.safedecimal.decayingaftermath.client;

import net.minecraft.client.Minecraft;

/**
 * Minecraft's own soundtrack must not compete with a critical heartbeat or a death score.
 * The music is stopped once on entry; the following ticks are skipped so MusicManager
 * cannot immediately pick a new situational track, and it resumes on its own delay once
 * the body leaves the critical state.
 */
public final class VanillaMusicGate {
    private static boolean suppressing;

    private VanillaMusicGate() {}

    /** @return true when {@code MusicManager.tick} must be cancelled for this tick. */
    public static boolean suppress(Minecraft client) {
        if (client == null) return false;
        if (!UnconsciousPresentationPolicy.vanillaMusicMutedNow()) {
            suppressing = false;
            return false;
        }
        if (!suppressing) {
            suppressing = true;
            client.getMusicManager().stopPlaying();
        }
        return true;
    }

    public static void reset() { suppressing = false; }
}
