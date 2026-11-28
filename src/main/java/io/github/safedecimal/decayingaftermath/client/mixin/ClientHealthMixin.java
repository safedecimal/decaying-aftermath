package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.AftermathCorpse;
import io.github.safedecimal.decayingaftermath.client.ClientMedicalState;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-side removal of vanilla HP and deathTime checks for the local player. */
@Mixin(LivingEntity.class)
public abstract class ClientHealthMixin {
    @Shadow public int deathTime;

    @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
    private void abjection$clientMedicalHealth(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Player player && !(player instanceof AftermathCorpse)
                && player == Minecraft.getInstance().player) {
            boolean dead = ClientMedicalState.received()
                    && ClientMedicalState.snapshot().mode() == BodySnapshot.Mode.DEAD;
            cir.setReturnValue(dead ? 0.0F : ((LivingEntity) (Object) this).getMaxHealth());
        }
    }

    @Inject(method = "isDeadOrDying", at = @At("HEAD"), cancellable = true)
    private void abjection$clientMedicalDead(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && !(player instanceof AftermathCorpse)
                && player == Minecraft.getInstance().player) {
            boolean dead = ClientMedicalState.received()
                    && ClientMedicalState.snapshot().mode() == BodySnapshot.Mode.DEAD;
            cir.setReturnValue(dead);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void abjection$clearStaleClientDeathTime(CallbackInfo ci) {
        if ((Object) this instanceof Player player && !(player instanceof AftermathCorpse)
                && player == Minecraft.getInstance().player) {
            if (!ClientMedicalState.received() || ClientMedicalState.snapshot().mode() != BodySnapshot.Mode.DEAD) {
                this.deathTime = 0;
            }
        }
    }

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void abjection$preventVanillaClientDeathFlip(CallbackInfo ci) {
        if ((Object) this instanceof Player player && !(player instanceof AftermathCorpse)
                && player == Minecraft.getInstance().player) {
            if (!ClientMedicalState.received() || ClientMedicalState.snapshot().mode() != BodySnapshot.Mode.DEAD) {
                this.deathTime = 0;
                ci.cancel();
            }
        }
    }
}
