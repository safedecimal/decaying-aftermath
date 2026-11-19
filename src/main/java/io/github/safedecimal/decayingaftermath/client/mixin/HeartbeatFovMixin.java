package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.MedicalClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class HeartbeatFovMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract Entity entity();
    @Shadow public abstract boolean isPanoramicMode();

    // Affect the world projection and culling together, not the separate hand/HUD FOV.
    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
    private void decayingAftermath$heartbeatFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (isPanoramicMode() || entity() != minecraft.player) return;
        float vanillaFov = cir.getReturnValueF();
        float pulsedFov = MedicalClient.heartbeatFov(minecraft, vanillaFov);
        if (pulsedFov != vanillaFov) cir.setReturnValue(pulsedFov);
    }
}
