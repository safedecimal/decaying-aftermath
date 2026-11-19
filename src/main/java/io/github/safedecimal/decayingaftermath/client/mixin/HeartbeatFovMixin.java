package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.HeartbeatFovPulse;
import io.github.safedecimal.decayingaftermath.client.MedicalClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class HeartbeatFovMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private float fov;
    @Shadow private float hudFov;
    @Shadow public abstract Entity entity();
    @Shadow public abstract boolean isPanoramicMode();

    // Sample once after vanilla calculates both FOVs and before it builds the frustum.
    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Camera;hudFov:F", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void decayingAftermath$heartbeatFov(DeltaTracker delta, CallbackInfo ci) {
        if (isPanoramicMode() || entity() != minecraft.player) return;
        double zoom = MedicalClient.heartbeatZoom(minecraft);
        fov = HeartbeatFovPulse.applyZoom(fov, zoom);
        hudFov = HeartbeatFovPulse.applyZoom(hudFov, zoom);
    }
}
