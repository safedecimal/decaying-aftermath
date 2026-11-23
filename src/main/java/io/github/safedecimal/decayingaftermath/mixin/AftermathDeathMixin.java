package io.github.safedecimal.decayingaftermath.mixin;

import io.github.safedecimal.decayingaftermath.server.ServerAftermathSystem;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import io.github.safedecimal.decayingaftermath.medical.MedicalDeathPolicy;
import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ServerPlayer.class,priority=1500)
public abstract class AftermathDeathMixin {
    @Inject(method="die(Lnet/minecraft/world/damagesource/DamageSource;)V",at=@At("HEAD"),cancellable=true)
    private void decayingAftermath$blackoutIsNotDeath(DamageSource source,CallbackInfo ci) {
        ServerPlayer player=(ServerPlayer)(Object)this;
        boolean explicit=source.is(DamageTypes.GENERIC_KILL)||source.is(DamageTypes.FELL_OUT_OF_WORLD);
        var body=ServerMedicalSystem.get(player).snapshot();
        if(MedicalDeathPolicy.rejectBlackoutDeath(body,explicit)) {
            // A late vanilla/compat HP=0 callback cannot turn a recoverable blackout into a death.
            ServerMedicalSystem.mirrorHealth(player);MedicalNetwork.sync(player,body);ci.cancel();
        }
    }
    @Inject(method="die(Lnet/minecraft/world/damagesource/DamageSource;)V",at=@At("TAIL"))
    private void decayingAftermath$beginAfterlife(DamageSource source,CallbackInfo ci) {
        ServerAftermathSystem.onDeath((ServerPlayer)(Object)this,source);
    }
}
