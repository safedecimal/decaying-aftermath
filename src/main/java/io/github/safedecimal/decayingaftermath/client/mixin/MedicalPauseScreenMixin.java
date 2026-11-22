package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.MedicalClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class MedicalPauseScreenMixin {
    @Shadow @Final private Minecraft minecraft;
    @Inject(method="setPauseScreen(ZZ)V",at=@At("HEAD"),cancellable=true)
    private void decayingAftermath$denyPauseScreen(boolean showMenu,boolean canPause,CallbackInfo ci) {
        if(MedicalClient.blocksPause(minecraft)) {MedicalClient.pauseDenied();ci.cancel();}
    }
    @Inject(method="setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",at=@At("HEAD"),cancellable=true)
    private void decayingAftermath$denyOtherPauseScreens(Screen incoming,CallbackInfo ci) {
        if(incoming!=null&&incoming.isPauseScreen()&&MedicalClient.blocksPause(minecraft)) {
            MedicalClient.pauseDenied();ci.cancel();
        }
    }
    @Inject(method="tick()V",at=@At("TAIL"))
    private void decayingAftermath$releaseExistingPause(CallbackInfo ci) {
        Gui gui=(Gui)(Object)this;var screen=gui.screen();
        if(screen!=null&&screen.isPauseScreen()&&MedicalClient.blocksPause(minecraft)) {
            gui.setScreen(null);MedicalClient.pauseDenied();
        }
    }
}
