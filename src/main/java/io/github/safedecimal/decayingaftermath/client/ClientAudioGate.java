package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.client.mixin.SoundEngineAccessor;
import io.github.safedecimal.decayingaftermath.client.mixin.SoundManagerAccessor;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;

/**
 * Stops the world for a fainted player. New world sounds are refused in SoundEngine;
 * this class removes the ones that were already playing when consciousness was lost,
 * because a looping chicken or a record would otherwise keep sounding for its full length.
 */
public final class ClientAudioGate {
    private ClientAudioGate() {}

    public static void silenceWorldSounds(Minecraft client, BodySnapshot body) {
        if (client == null || body == null) return;
        SoundManager manager = client.getSoundManager();
        List<SoundInstance> doomed = worldSounds(manager, body);
        for (SoundInstance instance : doomed) manager.stop(instance);
    }

    static List<SoundInstance> worldSounds(SoundManager manager, BodySnapshot body) {
        List<SoundInstance> doomed = new ArrayList<>();
        if (!UnconsciousPresentationPolicy.worldSoundsMuted(body) || manager == null) return doomed;
        SoundEngine engine = ((SoundManagerAccessor) manager).decayingAftermath$engine();
        if (engine == null) return doomed;
        // Snapshot the keys: stopping schedules channel work and must not mutate while iterating.
        for (SoundInstance instance :
                new ArrayList<>(((SoundEngineAccessor) engine).decayingAftermath$channels().keySet())) {
            if (!UnconsciousPresentationPolicy.keepsPlayback(body, instance)) doomed.add(instance);
        }
        return doomed;
    }
}
