package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.MedicalPanelLayout;
import io.github.safedecimal.decayingaftermath.client.MedicalPanelRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryScreen.class)
public abstract class InventoryMedicalPanelMixin {
    @Inject(method = "getRecipeBookButtonPosition", at = @At("RETURN"), cancellable = true)
    private void decayingAftermath$placeRecipeButton(CallbackInfoReturnable<ScreenPosition> cir) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        ContainerScreenAccessor container = (ContainerScreenAccessor) this;
        int delta = MedicalPanelLayout.inventoryLeft(screen.width, container.decayingAftermath$getImageWidth(),
                container.decayingAftermath$getLeft(), decayingAftermath$recipesVisible()) - container.decayingAftermath$getLeft();
        ScreenPosition original = cir.getReturnValue();
        cir.setReturnValue(new ScreenPosition(original.x() + delta, original.y()));
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void decayingAftermath$placeInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        ContainerScreenAccessor container = (ContainerScreenAccessor) this;
        int left = MedicalPanelLayout.inventoryLeft(screen.width, container.decayingAftermath$getImageWidth(),
                container.decayingAftermath$getLeft(), decayingAftermath$recipesVisible());
        container.decayingAftermath$setLeft(left);
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void decayingAftermath$medicalBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        MedicalPanelLayout.Box box = decayingAftermath$box();
        if (!box.compact()) {
            MedicalPanelRenderer.extract(graphics, box);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void decayingAftermath$compactMedical(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        MedicalPanelLayout.Box box = decayingAftermath$box();
        if (box.compact()) {
            MedicalPanelRenderer.extract(graphics, box);
        }
    }

    @Unique
    private boolean decayingAftermath$recipesVisible() {
        return ((RecipeBookScreenAccessor) this).decayingAftermath$getRecipeBookComponent().isVisible();
    }

    @Unique
    private MedicalPanelLayout.Box decayingAftermath$box() {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        ContainerScreenAccessor container = (ContainerScreenAccessor) this;
        return MedicalPanelLayout.locate(screen.width, screen.height, container.decayingAftermath$getLeft(),
                container.decayingAftermath$getTop(), container.decayingAftermath$getImageWidth(),
                container.decayingAftermath$getImageHeight(), decayingAftermath$recipesVisible());
    }
}
