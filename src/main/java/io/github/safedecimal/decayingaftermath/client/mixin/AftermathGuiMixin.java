package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.AftermathClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class AftermathGuiMixin {
    @Inject(method="setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",at=@At("HEAD"),cancellable=true)
    private void decayingAftermath$noDeathMenu(Screen incoming,CallbackInfo ci) {
        if (incoming instanceof DeathScreen && AftermathClient.suppressDeathScreen()) ci.cancel();
    }
    @ModifyVariable(method="extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private boolean decayingAftermath$cinematicHud(boolean renderHud) { return renderHud && !AftermathClient.active(); }
}
