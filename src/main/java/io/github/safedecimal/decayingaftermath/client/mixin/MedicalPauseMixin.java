package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.MedicalClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MedicalPauseMixin {
    @Inject(method="pauseGame(Z)V",at=@At("HEAD"),cancellable=true)
    private void decayingAftermath$denyCriticalPause(boolean showMenu,CallbackInfo ci) {
        if(MedicalClient.blocksPause((Minecraft)(Object)this)) {MedicalClient.pauseDenied();ci.cancel();}
    }
    @Inject(method="isPaused()Z",at=@At("HEAD"),cancellable=true)
    private void decayingAftermath$criticalTimeKeepsRunning(CallbackInfoReturnable<Boolean> cir) {
        if(MedicalClient.blocksPause((Minecraft)(Object)this))cir.setReturnValue(false);
    }
}
