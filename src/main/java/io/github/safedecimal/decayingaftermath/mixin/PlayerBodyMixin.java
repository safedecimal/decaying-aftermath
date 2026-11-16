package io.github.safedecimal.decayingaftermath.mixin;

import io.github.safedecimal.decayingaftermath.medical.BodyModel;
import io.github.safedecimal.decayingaftermath.server.ServerBodyOwner;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redirect only the final HP write, after vanilla blocking/armor/magic/absorption. */
@Mixin(Player.class)
public abstract class PlayerBodyMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void decayingAftermath$noUnconsciousAttack(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && !ServerMedicalSystem.canAct(player)) ci.cancel();
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void decayingAftermath$damageBody(Player player, float nextHealth,
            ServerLevel level, DamageSource source, float unmitigatedAmount) {
        if (player instanceof ServerPlayer serverPlayer) {
            ServerMedicalSystem.applyDamage(serverPlayer, source, player.getHealth() - nextHealth);
        } else player.setHealth(nextHealth);
    }

    /** LivingEntity owns inherited health methods; only actual ServerPlayer instances are intercepted. */
    @Mixin(LivingEntity.class)
    public abstract static class Health implements ServerBodyOwner {
        @Unique private BodyModel decayingAftermath$body;
        @Unique private boolean decayingAftermath$bodyReady;

        @Override public BodyModel decayingAftermath$getBody() {
            if (decayingAftermath$body == null) decayingAftermath$body = new BodyModel();
            return decayingAftermath$body;
        }
        @Override public void decayingAftermath$setBody(BodyModel body) {
            decayingAftermath$body = java.util.Objects.requireNonNull(body);
            decayingAftermath$bodyReady = true;
        }
        @Override public boolean decayingAftermath$isBodyReady() { return decayingAftermath$bodyReady; }
        @Override public void decayingAftermath$setBodyReady(boolean ready) { decayingAftermath$bodyReady = ready; }
        @Override @Invoker("checkTotemDeathProtection")
        public abstract boolean decayingAftermath$tryTotem(DamageSource source);

        @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
        private void decayingAftermath$directHp(float value, CallbackInfo ci) {
            if ((Object) this instanceof ServerPlayer player && decayingAftermath$bodyReady
                    && !ServerMedicalSystem.isHealthBridge()) {
                ServerMedicalSystem.directHealth(player, value);
                ci.cancel();
            }
        }

        @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
        private void decayingAftermath$adaptHealing(float amount, CallbackInfo ci) {
            if ((Object) this instanceof ServerPlayer player && decayingAftermath$bodyReady
                    && !ServerMedicalSystem.isHealthBridge()) {
                ServerMedicalSystem.adaptHealing(player, amount);
                ci.cancel();
            }
        }

        @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
        private void decayingAftermath$beginLoad(ValueInput input, CallbackInfo ci) {
            if ((Object) this instanceof ServerPlayer) decayingAftermath$bodyReady = false;
        }
        @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
        private void decayingAftermath$loadBody(ValueInput input, CallbackInfo ci) {
            if ((Object) this instanceof ServerPlayer player) ServerMedicalSystem.load(player, input);
        }
        @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
        private void decayingAftermath$saveBody(ValueOutput output, CallbackInfo ci) {
            if ((Object) this instanceof ServerPlayer player) ServerMedicalSystem.save(player, output);
        }
    }
}
