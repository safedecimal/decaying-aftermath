package io.github.safedecimal.decayingaftermath.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.safedecimal.decayingaftermath.client.MedicalClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Final GUI extraction, independent of HUD registry layers, HUD hiding and screen order. */
@Mixin(Gui.class)
public abstract class GuiMedicalOverlayMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",at=@At("TAIL"))
    private void decayingAftermath$finalOverlay(DeltaTracker delta,boolean renderHud,boolean renderToasts,
            CallbackInfo ci,@Local GuiGraphicsExtractor graphics) {
        MedicalClient.extractFinalOverlay(graphics);
    }
}
