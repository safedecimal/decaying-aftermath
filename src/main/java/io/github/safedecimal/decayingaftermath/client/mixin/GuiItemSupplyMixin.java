package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.MedicalSupplyOverlay;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only the count layer: vanilla cooldown/durability and non-medical items stay native. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiItemSupplyMixin {
    @Inject(method="itemCount(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at=@At("HEAD"),cancellable=true)
    private void decayingAftermath$supplyCount(Font font,ItemStack stack,int x,int y,String override,CallbackInfo ci) {
        if(MedicalSupplyOverlay.replaceItemCount((GuiGraphicsExtractor)(Object)this,stack,x,y))ci.cancel();
    }
}
