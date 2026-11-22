package io.github.safedecimal.decayingaftermath.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The same connection-load reset used by vanilla's PERFORM_RESPAWN handler. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface AftermathConnectionAccessor {
    @Invoker("restartClientLoadTimerAfterRespawn")
    void decayingAftermath$restartAfterRespawn();
}
