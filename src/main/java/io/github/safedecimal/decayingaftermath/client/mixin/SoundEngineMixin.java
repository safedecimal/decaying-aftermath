package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.UnconsciousPresentationPolicy;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A fainted player must not hear chickens, mining, records or any other world sound.
 * Returning NOT_STARTED keeps the instance out of the channel map entirely, so no
 * half-muted playback survives the blackout.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void decayingAftermath$refuseWorldWhileFainted(SoundInstance instance,
            CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (!UnconsciousPresentationPolicy.keepsPlaybackNow(instance))
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }
}
