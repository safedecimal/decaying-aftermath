package io.github.safedecimal.decayingaftermath.compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.safedecimal.decayingaftermath.compat.CruorVisualCompatibility;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.raiiiden.cruor.client.body.BodyBlood", remap = false)
public abstract class CruorBodyBloodCompatMixin {
    @Inject(method = "drip(Lnet/minecraft/world/entity/LivingEntity;Lcom/raiiiden/cruor/client/body/BodyBlood$Body;F)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void decayingAftermath$bleedFromInjuredPart(LivingEntity living, @Coerce Object body, float size, CallbackInfo ci) {
        if (CruorVisualCompatibility.emitMedicalDrip(this, living, body, size)) ci.cancel();
    }
    @Inject(method = "tick(Lnet/minecraft/client/multiplayer/ClientLevel;JLnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), remap = false)
    private void decayingAftermath$actualWoundFlow(ClientLevel level, long time, Vec3 camera, CallbackInfo ci) {
        CruorVisualCompatibility.syncVisualBleeding(this);
    }
    // Native tick has three getHealth reads. Ordinal1 is stain healing and must remain untouched.
    @WrapOperation(method = "tick(Lnet/minecraft/client/multiplayer/ClientLevel;JLnet/minecraft/world/phys/Vec3;)V",
            at = {
                @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getHealth()F", ordinal = 0),
                @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getHealth()F", ordinal = 2)
            }, remap = false)
    private float decayingAftermath$noHpOnlyBleeding(LivingEntity entity, Operation<Float> original) {
        return CruorVisualCompatibility.healthForBleedFallback(entity, original.call(entity));
    }
}
