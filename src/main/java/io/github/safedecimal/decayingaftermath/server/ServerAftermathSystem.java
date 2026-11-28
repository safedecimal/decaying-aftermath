package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.AftermathPolicy;
import io.github.safedecimal.decayingaftermath.medical.SurvivalConfig;
import io.github.safedecimal.decayingaftermath.medical.MedicalPhysiology;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import io.github.safedecimal.decayingaftermath.network.AftermathPayload;
import io.github.safedecimal.decayingaftermath.network.DefibNetwork;
import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Actual spectator on the server, not a free camera with a client-only countdown. */
public final class ServerAftermathSystem {
    private static final String SAVE_KEY = "abjection_afterlife";
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Float> LIVE_MOOD = new HashMap<>();
    private static boolean registered;
    private static final class Session {
        final long token;
        final ResourceKey<Level> dimension;
        final Vec3 anchor;
        final float yaw;
        final int corpseId;
        final GameType returnMode;
        final boolean depression, hardcore;
        int remainingTicks;
        boolean pendingRespawn, finishing;
        Session(long token, ResourceKey<Level> dimension, Vec3 anchor, float yaw, int corpseId,
                GameType returnMode, boolean depression, boolean hardcore, int remainingTicks, boolean pendingRespawn) {
            this.token=token; this.dimension=dimension; this.anchor=anchor; this.yaw=yaw; this.corpseId=corpseId;
            this.returnMode=returnMode; this.depression=depression; this.hardcore=hardcore;
            this.remainingTicks=remainingTicks; this.pendingRespawn=pendingRespawn;
        }
    }
    private ServerAftermathSystem() { }
    public static void register() {
        if (registered) return;
        registered=true;
        PayloadTypeRegistry.clientboundPlay().register(AftermathPayload.TYPE, AftermathPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(ServerAftermathSystem::tick);
        ServerPlayerEvents.JOIN.register(player -> {
            Session session=SESSIONS.get(player.getUUID());
            if (session!=null) { session.pendingRespawn=!player.isAlive(); sync(player,session,true); }
            else if (ServerPlayNetworking.canSend(player,AftermathPayload.TYPE))
                ServerPlayNetworking.send(player,new AftermathPayload(false,false,0,0,
                        player.level().dimension().identifier(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getId()));
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            Session session=SESSIONS.get(player.getUUID());
            if (session!=null && !session.finishing) {
                session.pendingRespawn=false;
                rebindConnection(player);
                io.github.safedecimal.decayingaftermath.compat.AftermathRagdollCompatibility.retainDeath(oldPlayer,session.remainingTicks);
                ServerMedicalSystem.restoreAfterDeath(player);
                hold(player,session); sync(player,session,true);
                sendHealth(player);
            }
        });
        // Save/load carries the remaining time across logout/restart. Never leak to a new server world.
        ServerPlayerEvents.LEAVE.register(player -> LIVE_MOOD.remove(player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {SESSIONS.clear(); LIVE_MOOD.clear();});
    }
    public static boolean active(ServerPlayer player) {
        Session session=SESSIONS.get(player.getUUID());
        return session!=null && !session.finishing;
    }
    public static int remainingTicks(ServerPlayer player) {
        Session session=SESSIONS.get(player.getUUID()); return session==null ? 0 : session.remainingTicks;
    }
    /** Explicit admin revive: ends the afterlife immediately at the death location and restores live mode. */
    public static ServerPlayer reviveNow(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.finishing) return null;
        if (session.pendingRespawn || !player.isAlive()) {
            player = player.level().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            rebindConnection(player);
            session.pendingRespawn = false;
        }
        ServerLevel level = player.level().getServer().getLevel(session.dimension);
        if (level != null) {
            player.teleportTo(level, session.anchor.x, session.anchor.y, session.anchor.z, Set.of(), session.yaw, 0, false);
        }
        player.setGameMode(GameType.byId(AftermathPolicy.returnGameModeId(session.returnMode.getId(), false)));
        finish(player, session, false);
        return player;
    }
    public static void rememberMood(ServerPlayer player, BodySnapshot body) {
        if (body.mode()!=BodySnapshot.Mode.DEAD && !active(player))
            LIVE_MOOD.put(player.getUUID(), MedicalPhysiology.mood(body)*100);
    }
    public static void onDeath(ServerPlayer player, DamageSource source) {
        if (player.isAlive() || active(player) || !ServerPlayNetworking.canSend(player,AftermathPayload.TYPE)) return;
        float mood=LIVE_MOOD.getOrDefault(player.getUUID(),MedicalPhysiology.mood(ServerMedicalSystem.get(player).snapshot())*100);
        boolean depression=AftermathPolicy.fromMood(mood)==AftermathPolicy.Cause.SEVERE_DEPRESSION;
        LIVE_MOOD.remove(player.getUUID());
        Session session=new Session(UUID.randomUUID().getMostSignificantBits(), player.level().dimension(),
                player.position(),player.getYRot(),player.getId(),player.gameMode(),depression,
                player.level().getServer().getWorldData().isHardcore(),
                AftermathPolicy.checkedSeconds(SurvivalConfig.get().deathAftermathSeconds)*20,true);
        SESSIONS.put(player.getUUID(),session);
        MedicalNetwork.cancelProcedures(player); DefibNetwork.cancelFor(player);
        io.github.safedecimal.decayingaftermath.network.SelfHarmNetwork.cancelFor(player);
        sync(player,session,true);
    }
    private static void tick(MinecraftServer server) {
        for (ServerPlayer initial : new ArrayList<>(server.getPlayerList().getPlayers())) {
            Session session=SESSIONS.get(initial.getUUID());
            if (session==null || session.finishing) continue;
            ServerPlayer player=initial;
            if (session.pendingRespawn || !player.isAlive()) {
                // Normal death/drop logic already ran. The temporary new body is always a spectator.
                // Do not tag the DEAD old body as spectator before COPY_FROM.
                player=server.getPlayerList().respawn(player,false,Entity.RemovalReason.KILLED);
                rebindConnection(player);
                session.pendingRespawn=false;
                hold(player,session); sync(player,session,true);
            }
            // An explicit server/admin gamemode change ends the camera lock without another teleport.
            if(player.gameMode()!=GameType.SPECTATOR) {finish(player,session,false);continue;}
            hold(player,session);
            if (--session.remainingTicks<=0) finish(player,session,true);
            else if (session.remainingTicks%20==0) {sync(player,session,true);sendHealth(player);}
        }
    }
    private static void hold(ServerPlayer player, Session session) {
        if (player.gameMode()!=GameType.SPECTATOR) player.setGameMode(GameType.SPECTATOR);
        player.setCamera(player); player.setDeltaMovement(Vec3.ZERO); player.setSprinting(false);
        player.setLastClientInput(Input.EMPTY); player.stopUsingItem();
        ServerLevel level=player.level().getServer().getLevel(session.dimension);
        if (level!=null && (player.level()!=level || player.position().distanceToSqr(session.anchor)>.0025))
            player.teleportTo(level,session.anchor.x,session.anchor.y,session.anchor.z,Set.of(),session.yaw,0,false);
    }
    private static void finish(ServerPlayer player, Session session,boolean respawnAtSpawn) {
        session.finishing=true;
        SESSIONS.remove(player.getUUID());LIVE_MOOD.remove(player.getUUID());
        ServerMedicalSystem.restoreAfterDeath(player);
        player.setCamera(player);player.setLastClientInput(Input.EMPTY);
        if (!session.hardcore && respawnAtSpawn) {
            TeleportTransition spawn=player.findRespawnPositionAndUseSpawnBlock(false,TeleportTransition.DO_NOTHING);
            Vec3 position=spawn.position();
            player.teleportTo(spawn.newLevel(),position.x,position.y,position.z,Set.of(),spawn.yRot(),spawn.xRot(),false);
            player.setGameMode(GameType.byId(AftermathPolicy.returnGameModeId(session.returnMode.getId(),false)));
        } // Hardcore remains spectator: this presentation must never grant a second life.
        // Send the actual final mode even when a mode setter would otherwise be a no-op.
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE,player.gameMode().getId()));
        sync(player,session,false);sendHealth(player);
        player.connection.send(new ClientboundSetCameraPacket(player));
    }
    static void rebindConnection(ServerPlayer player) {
        // PlayerList.respawn returns a new ServerPlayer but does not assign listener.player.
        // Vanilla's handleClientCommand normally performs these three steps afterwards.
        player.connection.player=player;
        player.connection.resetPosition();
        ((io.github.safedecimal.decayingaftermath.mixin.AftermathConnectionAccessor)(Object)player.connection)
                .decayingAftermath$restartAfterRespawn();
    }
    private static void sendHealth(ServerPlayer player) {
        player.connection.send(new ClientboundSetHealthPacket(player.getHealth(),
                player.getFoodData().getFoodLevel(),player.getFoodData().getSaturationLevel()));
    }
    private static void sync(ServerPlayer player, Session session, boolean active) {
        if (ServerPlayNetworking.canSend(player,AftermathPayload.TYPE))
            ServerPlayNetworking.send(player,new AftermathPayload(active,session.depression,
                    Math.max(0,session.remainingTicks),session.token,session.dimension.identifier(),
                    session.anchor.x,session.anchor.y,session.anchor.z,session.yaw,session.corpseId));
    }
    public static void save(ServerPlayer player, ValueOutput output) {
        Session session=SESSIONS.get(player.getUUID());
        if (session==null || session.finishing) return;
        ValueOutput node=output.child(SAVE_KEY);
        node.putLong("token",session.token); node.putString("dimension",session.dimension.identifier().toString());
        node.putDouble("x",session.anchor.x); node.putDouble("y",session.anchor.y); node.putDouble("z",session.anchor.z);
        node.putFloat("yaw",session.yaw); node.putInt("corpse_id",session.corpseId);
        node.putInt("return_mode",session.returnMode.getId()); node.putBoolean("depression",session.depression);
        node.putBoolean("hardcore",session.hardcore); node.putInt("remaining_ticks",session.remainingTicks);
    }
    public static void load(ServerPlayer player, ValueInput input) {
        var child = input.child(SAVE_KEY);
        if (child.isEmpty()) child = input.child("decaying_aftermath_afterlife");
        child.ifPresent(node -> {
            int remaining=node.getIntOr("remaining_ticks",0);
            Identifier dimension=Identifier.tryParse(node.getStringOr("dimension","minecraft:overworld"));
            double x=node.getDoubleOr("x",0),y=node.getDoubleOr("y",64),z=node.getDoubleOr("z",0);
            float yaw=node.getFloatOr("yaw",0);
            if (remaining<=0 || remaining>72000 || dimension==null || !Double.isFinite(x)
                    || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) return;
            SESSIONS.put(player.getUUID(),new Session(node.getLongOr("token",0),
                    ResourceKey.create(Registries.DIMENSION,dimension),new Vec3(x,y,z),yaw,
                    node.getIntOr("corpse_id",-1),GameType.byId(node.getIntOr("return_mode",0)),
                    node.getBooleanOr("depression",false),node.getBooleanOr("hardcore",false),remaining,!player.isAlive()));
        });
    }
}
