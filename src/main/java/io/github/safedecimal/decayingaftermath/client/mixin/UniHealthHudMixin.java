package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.UniHealthHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class UniHealthHudMixin {
    @Inject(method="extractHotbarAndDecorations",at=@At("HEAD"),cancellable=true)
    private void decayingAftermath$nativeDevice(GuiGraphicsExtractor graphics,DeltaTracker delta,CallbackInfo ci) {
        if(UniHealthHud.enabled(Minecraft.getInstance())) {UniHealthHud.extract(graphics,delta);ci.cancel();}
    }
    @Inject(method={"extractEffects","extractSelectedItemName"},at=@At("HEAD"),cancellable=true)
    private void decayingAftermath$noVanillaStatusDuplicates(CallbackInfo ci) {
        if(UniHealthHud.enabled(Minecraft.getInstance()))ci.cancel();
    }
}
