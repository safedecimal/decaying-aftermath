package io.github.safedecimal.decayingaftermath.compat;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;

/** Optional cosmetic receivers are discovered with canSend; no native mod dependency is required. */
public final class MedicalVisualNetwork {
    private static boolean registered;
    private MedicalVisualNetwork() {}
    public static void register() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.clientboundPlay().register(MedicalVisualPayload.TYPE, MedicalVisualPayload.CODEC);
    }
    public static void sync(ServerPlayer victim, BodySnapshot snapshot) {
        if (victim.getId() < 0) return;
        var parts = java.util.Arrays.stream(snapshot.parts()).map(BodySnapshot.PartSnapshot::bleeding).toList();
        float bleeding = MedicalBleedOrigins.total(parts);
        var posture = MedicalCollapsePolicy.posture(snapshot, victim.isAlive(), victim.isSleeping(), victim.isCreative() || victim.isSpectator());
        var payload = new MedicalVisualPayload(victim.getId(), victim.getUUID(), Math.clamp(bleeding, 0, 6),
                snapshot.mode() == BodySnapshot.Mode.DEAD, parts,
                posture == MedicalCollapsePolicy.Posture.COLLAPSED ? MedicalRagdollCompatibility.bodySerial(victim) : 0,
                posture.ordinal());
        var watchers = MedicalVisualAudience.select(PlayerLookup.tracking(victim), victim,
                watcher -> ServerPlayNetworking.canSend(watcher, MedicalVisualPayload.TYPE));
        for (ServerPlayer watcher : watchers) ServerPlayNetworking.send(watcher, payload);
    }
}
