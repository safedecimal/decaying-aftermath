package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.VanillaMusicGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps vanilla background music out of critical and unconscious presentation. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void decayingAftermath$suppressCriticalMusic(CallbackInfo ci) {
        if (VanillaMusicGate.suppress(minecraft)) ci.cancel();
    }
}
