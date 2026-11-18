package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.UnconsciousPresentationPolicy;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Advancement, tutorial and "now playing" toasts are hints too; a fainted player sees none. */
@Mixin(ToastManager.class)
public abstract class UnconsciousToastMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void decayingAftermath$hideToasts(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (UnconsciousPresentationPolicy.hintsHiddenNow()) ci.cancel();
    }
}
