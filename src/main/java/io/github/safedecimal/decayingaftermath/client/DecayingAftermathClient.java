package io.github.safedecimal.decayingaftermath.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import io.github.safedecimal.decayingaftermath.compat.ClientMedicalVisuals;

public final class DecayingAftermathClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientMedicalState.register();
        DefibScreen.register();
        SelfHarmScreen.register();
        SuicideClient.register();
        ClientMedicalVisuals.register();
        MedicalClient.register();
        AftermathClient.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientMedicalState.reset(); ClientMedicalVisuals.reset(); PulseState.reset();
            SelfHarmScreen.reset();
            SuicideClient.reset();
            io.github.safedecimal.decayingaftermath.compat.ClientMedicalRagdolls.reset();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            io.github.safedecimal.decayingaftermath.compat.ClientMedicalRagdolls.tick();
            SelfHarmScreen.ensureOpen(client);
            SuicideClient.tick(client);
            if (client.player == null || client.level == null) {
                PulseState.reset();
                ClientMedicalVisuals.reset();
            } else if (!client.isPaused()) {
                PulseState.tick(client.player);
            }
        });
    }
}
