package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.MedicalPanelLayout;
import io.github.safedecimal.decayingaftermath.client.MedicalPanelRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryMedicalPanelMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void decayingAftermath$renderCornerPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        var panel = MedicalPanelLayout.forScreen(graphics.guiWidth(), graphics.guiHeight());
        MedicalPanelRenderer.extract(graphics, panel);
        MedicalPanelRenderer.extractTooltip(graphics, panel, mouseX, mouseY);
    }
}
