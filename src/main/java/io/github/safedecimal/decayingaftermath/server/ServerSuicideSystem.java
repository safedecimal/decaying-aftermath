package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import io.github.safedecimal.decayingaftermath.network.DefibNetwork;
import io.github.safedecimal.decayingaftermath.network.HeadBleedPayload;
import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import io.github.safedecimal.decayingaftermath.network.SelfHarmNetwork;
import io.github.safedecimal.decayingaftermath.network.SuicidePayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative 4.15s /suicide countdown matching CU's selfdestruct1 -> head detonation -> 6m aftermath. */
public final class ServerSuicideSystem {
    public static final int COUNTDOWN_TICKS = 83;
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();
    private static boolean registered;

    private ServerSuicideSystem() { }

    public static void register() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.clientboundPlay().register(SuicidePayload.TYPE, SuicidePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HeadBleedPayload.TYPE, HeadBleedPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(ServerSuicideSystem::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.getPlayer().getUUID()));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> ACTIVE.remove(oldPlayer.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static boolean start(ServerPlayer player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator()
                || ServerAftermathSystem.active(player) || ACTIVE.containsKey(player.getUUID())) {
            return false;
        }
        if (ServerMedicalSystem.get(player).voluntarySleeping()) {
            ServerMedicalSystem.wakeFromSleep(player);
        }
        MedicalNetwork.cancelProcedures(player);
        DefibNetwork.cancelFor(player);
        SelfHarmNetwork.cancelFor(player);
        player.closeContainer();
        ACTIVE.put(player.getUUID(), COUNTDOWN_TICKS);
        send(player, SuicidePayload.START);
        return true;
    }

    public static void cancel(ServerPlayer player) {
        if (ACTIVE.remove(player.getUUID()) != null) {
            send(player, SuicidePayload.ABORT);
        }
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            Integer ticks = ACTIVE.get(player.getUUID());
            if (ticks == null) continue;
            if (!player.isAlive() || player.isCreative() || player.isSpectator() || ServerAftermathSystem.active(player)) {
                ACTIVE.remove(player.getUUID());
                send(player, SuicidePayload.ABORT);
                continue;
            }
            int next = ticks - 1;
            if (next <= 0) {
                ACTIVE.remove(player.getUUID());
                detonate(player);
            } else {
                ACTIVE.put(player.getUUID(), next);
            }
        }
    }

    private static void detonate(ServerPlayer player) {
        send(player, SuicidePayload.DETONATE);
        MedicalNetwork.cancelProcedures(player);
        DefibNetwork.cancelFor(player);
        SelfHarmNetwork.cancelFor(player);
        var body = ServerMedicalSystem.get(player);
        body.setHeadBlownOff(true);
        body.adminSetPart(BodyPart.HEAD, "integrity", 0f);
        body.adminSetVitals(Float.NaN, Float.NaN, Float.NaN, 0f);

        ServerLevel level = (ServerLevel) player.level();
        double hx = player.getX(), hy = player.getEyeY(), hz = player.getZ();
        ExplosionDamageCalculator noBlockOrEntityDamage = new ExplosionDamageCalculator() {
            @Override public boolean shouldBlockExplode(Explosion e, BlockGetter r, BlockPos p, BlockState s, float pow) { return false; }
            @Override public boolean shouldDamageEntity(Explosion e, Entity entity) { return false; }
            @Override public float getKnockbackMultiplier(Entity entity) { return 0f; }
        };
        Holder<SoundEvent> headgibSound = Holder.direct(SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath("abjection", "headgib")));
        level.explode(player, player.damageSources().explosion(null, null), noBlockOrEntityDamage,
                hx, hy, hz, 1.6f, false, Level.ExplosionInteraction.NONE,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
                WeightedList.of(), headgibSound);

        HeadBleedPayload bleed = new HeadBleedPayload(player.getId(), player.getUUID(), hx, hy, hz, 400);
        if (ServerPlayNetworking.canSend(player, HeadBleedPayload.TYPE)) {
            ServerPlayNetworking.send(player, bleed);
        }
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player && ServerPlayNetworking.canSend(watcher, HeadBleedPayload.TYPE)) {
                ServerPlayNetworking.send(watcher, bleed);
            }
        }

        ServerMedicalSystem.killMedically(player, player.damageSources().genericKill());
    }

    private static void send(ServerPlayer player, int stage) {
        if (!player.isRemoved() && ServerPlayNetworking.canSend(player, SuicidePayload.TYPE)) {
            ServerPlayNetworking.send(player, new SuicidePayload(stage));
        }
    }
}
