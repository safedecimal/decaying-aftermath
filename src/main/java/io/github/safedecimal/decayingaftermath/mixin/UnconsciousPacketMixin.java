package io.github.safedecimal.decayingaftermath.mixin;

import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import io.github.safedecimal.decayingaftermath.server.ServerAftermathSystem;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enforce unconsciousness on the server after vanilla has transferred the packet to its server thread. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class UnconsciousPacketMixin {
    @Shadow public ServerPlayer player;

    @Inject(method="handleTeleportToEntityPacket",at=@At(value="INVOKE",
            target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift=At.Shift.AFTER),cancellable=true)
    private void decayingAftermath$noSpectatorTeleport(CallbackInfo ci) {
        if (ServerAftermathSystem.active(player)) ci.cancel();
    }
    @Inject(method="handleClientCommand",at=@At(value="INVOKE",
            target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift=At.Shift.AFTER),cancellable=true)
    private void decayingAftermath$noEarlyRespawn(ServerboundClientCommandPacket packet,CallbackInfo ci) {
        if (ServerAftermathSystem.active(player) && packet.getAction()==ServerboundClientCommandPacket.Action.PERFORM_RESPAWN) ci.cancel();
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void decayingAftermath$blockUnconsciousMovement(CallbackInfo ci) {
        if (!ServerMedicalSystem.canAct(player)) {
            // Correct the client as well as refusing its movement, so a modified client cannot walk while unconscious.
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            ci.cancel();
        }
    }

    @Inject(method = {"handleInteract", "handlePlayerAction", "handleUseItem", "handleUseItemOn", "handlePlayerInput"},
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void decayingAftermath$blockUnconsciousActions(CallbackInfo ci) {
        if (!ServerMedicalSystem.canAct(player)) ci.cancel();
    }
}
