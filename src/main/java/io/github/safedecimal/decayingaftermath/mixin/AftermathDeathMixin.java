package io.github.safedecimal.decayingaftermath.mixin;

import io.github.safedecimal.decayingaftermath.server.ServerAftermathSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class AftermathDeathMixin {
    @Inject(method="die(Lnet/minecraft/world/damagesource/DamageSource;)V",at=@At("TAIL"))
    private void decayingAftermath$beginAfterlife(DamageSource source,CallbackInfo ci) {
        ServerAftermathSystem.onDeath((ServerPlayer)(Object)this,source);
    }
}
