package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.MedicalClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class UnconsciousVisionMixin {
    @Unique private static final Identifier decayingAftermath$blur =
            Identifier.fromNamespaceAndPath("abjection", "unconscious_vision");
    @Shadow @Final private Minecraft minecraft;

    // Vanilla collects world post effects in update and applies them before any GUI is drawn.
    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void decayingAftermath$updateVision(DeltaTracker delta, CallbackInfo ci) {
        if (MedicalClient.updateUnconsciousVision(minecraft)) {
            ((GameRenderer) (Object) this).getRequestedPostEffects().add(decayingAftermath$blur);
        }
    }
}
