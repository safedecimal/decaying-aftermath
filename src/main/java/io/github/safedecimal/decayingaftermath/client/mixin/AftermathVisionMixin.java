package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.AftermathClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class AftermathVisionMixin {
    @Inject(method="update(Lnet/minecraft/client/DeltaTracker;)V",at=@At("TAIL"))
    private void decayingAftermath$monochrome(DeltaTracker delta,CallbackInfo ci) {
        if (AftermathClient.depression()) ((GameRenderer)(Object)this).getRequestedPostEffects()
                .add(Identifier.fromNamespaceAndPath("decaying_aftermath","afterlife_monochrome"));
    }
}
