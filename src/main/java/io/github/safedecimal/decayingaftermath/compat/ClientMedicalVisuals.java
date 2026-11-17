package io.github.safedecimal.decayingaftermath.compat;

import io.github.safedecimal.decayingaftermath.client.ClientMedicalState;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Local full snapshot wins; remote players require their own fresh, identity-matched S2C summary. */
public final class ClientMedicalVisuals {
    private static final MedicalVisualCache<ClientLevel> CACHE = new MedicalVisualCache<>();
    private static boolean registered;
    private ClientMedicalVisuals() {}
    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(MedicalVisualPayload.TYPE, (payload, context) -> {
            ClientLevel arrived = context.client().level;
            context.client().execute(() -> {
                var client = context.client();
                CACHE.accept(arrived, client.level, payload.entityId(), payload.playerId(), payload.bleeding(),
                        payload.dead(), payload.partBleeding(), payload.ragdollSerial(), System.nanoTime());
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientMedicalVisuals::reset));
    }
    public static void reset() { CACHE.reset(); }
    public static MedicalVisualCache.Visual forLiving(LivingEntity entity) {
        var client = Minecraft.getInstance();
        CACHE.enterLevel(client.level);
        if (client.level == null || entity.level() != client.level || !(entity instanceof Player) || !entity.isAlive())
            return null;
        if (entity == client.player && ClientMedicalState.received()) {
            BodySnapshot snapshot = ClientMedicalState.snapshot();
            if (snapshot.mode() == BodySnapshot.Mode.DEAD) return null;
            var parts = java.util.Arrays.stream(snapshot.parts()).map(BodySnapshot.PartSnapshot::bleeding).toList();
            var summary = CACHE.live(client.level, entity.getId(), entity.getUUID(), true, true, System.nanoTime());
            return new MedicalVisualCache.Visual(MedicalBleedOrigins.total(parts), false, parts,
                    MedicalCollapsePolicy.collapsed(snapshot.mode(), entity.isAlive(), entity.isSleeping(),
                            client.player.isCreative() || client.player.isSpectator()) && summary != null ? summary.ragdollSerial() : 0);
        }
        return CACHE.live(client.level, entity.getId(), entity.getUUID(), true, true, System.nanoTime());
    }
}
