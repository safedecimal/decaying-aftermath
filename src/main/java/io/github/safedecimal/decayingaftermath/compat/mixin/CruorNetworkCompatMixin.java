package io.github.safedecimal.decayingaftermath.compat.mixin;

import io.github.safedecimal.decayingaftermath.compat.MedicalDamageContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.raiiiden.cruor.network.CruorNetwork", remap = false)
public abstract class CruorNetworkCompatMixin {
    @Inject(method = "send(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;FZ)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void decayingAftermath$deferNativeHit(LivingEntity victim, DamageSource source,
            float damage, boolean fatal, CallbackInfo ci) {
        if (MedicalDamageContext.captureCruorHit(victim, source, damage)) ci.cancel();
    }
}
