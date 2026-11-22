package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.AftermathClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class AftermathCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    // Override before the view/frustum is cached, so culling uses the directed shot too.
    @Inject(method="update(Lnet/minecraft/client/DeltaTracker;)V",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/Camera;getViewRotationMatrix(Lorg/joml/Matrix4f;)Lorg/joml/Matrix4f;"))
    private void decayingAftermath$directCamera(DeltaTracker delta,CallbackInfo ci) {
        if (!AftermathClient.active()) return;
        var shot=AftermathClient.cameraShot(Minecraft.getInstance());
        setPosition(new Vec3(shot.position().x(),shot.position().y(),shot.position().z()));
        setRotation(shot.yaw(),shot.pitch());
    }
}
