package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.UnconsciousPresentationPolicy;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A fainted player cannot read or hear the world, so its hints are not drawn either:
 * the action-bar message, titles, subtitles and the boss overlay that narrates events.
 */
@Mixin(Hud.class)
public abstract class UnconsciousHintsMixin {
    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void decayingAftermath$hideOverlayMessage(GuiGraphicsExtractor graphics, DeltaTracker delta,
            CallbackInfo ci) {
        if (UnconsciousPresentationPolicy.hintsHiddenNow()) ci.cancel();
    }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
    private void decayingAftermath$hideTitle(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (UnconsciousPresentationPolicy.hintsHiddenNow()) ci.cancel();
    }

    @Inject(method = "extractSubtitleOverlay", at = @At("HEAD"), cancellable = true)
    private void decayingAftermath$hideSubtitles(GuiGraphicsExtractor graphics, boolean defer, CallbackInfo ci) {
        if (UnconsciousPresentationPolicy.hintsHiddenNow()) ci.cancel();
    }
}
