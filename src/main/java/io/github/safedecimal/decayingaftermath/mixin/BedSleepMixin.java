package io.github.safedecimal.decayingaftermath.mixin;

import com.mojang.datafixers.util.Either;
import io.github.safedecimal.decayingaftermath.server.ServerAftermathSystem;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes bed sleep through Abjection's real-time sleep circle at any time of day without skipping night. */
@Mixin(ServerPlayer.class)
public abstract class BedSleepMixin {
    @Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
    private void abjection$sleepOnBedAnytimeWithoutSkippingNight(AbstractBedBlock block, BlockState state,
            BedRule rule, BlockPos pos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || ServerAftermathSystem.active(player)) {
            return;
        }
        if (!pos.closerToCenterThan(player.position(), 4.5)) {
            cir.setReturnValue(Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY));
            return;
        }
        if (rule.canSetSpawn(player.level())) {
            player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                    LevelData.RespawnData.of(player.level().dimension(), pos, player.getYRot(), player.getXRot()),
                    false), true);
        }
        if (ServerMedicalSystem.startSleep(player)) {
            player.teleportTo(pos.getX() + 0.5, pos.getY() + 0.5625, pos.getZ() + 0.5);
        }
        cir.setReturnValue(Either.right(Unit.INSTANCE));
    }
}
