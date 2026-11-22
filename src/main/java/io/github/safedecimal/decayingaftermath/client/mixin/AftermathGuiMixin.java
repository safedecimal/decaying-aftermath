package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.AftermathClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class AftermathGuiMixin {
    @ModifyVariable(method="setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Screen decayingAftermath$filterExplicitDeathMenu(Screen incoming) {
        return incoming instanceof DeathScreen && AftermathClient.suppressDeathScreen() ? null : incoming;
    }
    // setScreen(null) itself creates a DeathScreen after resolving its initial argument.
    // Skip that internal branch, without triggering shouldShowDeathScreen=false -> respawn().
    @ModifyExpressionValue(method="setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;isDeadOrDying()Z"))
    private boolean decayingAftermath$skipInternalDeathMenu(boolean deadOrDying) {
        return deadOrDying && !AftermathClient.suppressDeathScreen();
    }
    @ModifyVariable(method="extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private boolean decayingAftermath$cinematicHud(boolean renderHud) { return renderHud && !AftermathClient.active(); }
}
