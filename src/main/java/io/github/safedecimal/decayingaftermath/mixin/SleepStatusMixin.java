package io.github.safedecimal.decayingaftermath.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Never allow vanilla night/thunderstorm skipping. */
@Mixin(SleepStatus.class)
public abstract class SleepStatusMixin {
    @Inject(method = "areEnoughSleeping", at = @At("HEAD"), cancellable = true)
    private void abjection$neverSkipNight(int percentage, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "areEnoughDeepSleeping", at = @At("HEAD"), cancellable = true)
    private void abjection$neverSkipNightDeep(int percentage, List<ServerPlayer> players, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
