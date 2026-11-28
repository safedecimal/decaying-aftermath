package io.github.safedecimal.decayingaftermath.mixin;

import io.github.safedecimal.decayingaftermath.medical.BodyModel;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import io.github.safedecimal.decayingaftermath.server.ServerBodyOwner;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
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
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.safedecimal.decayingaftermath.compat.MedicalDamageContext;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes vanilla player HP & HP-death; routes all hits directly to anatomical medical body parts. */
@Mixin(Player.class)
public abstract class PlayerBodyMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void decayingAftermath$noUnconsciousAttack(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && !ServerMedicalSystem.canAct(player)) ci.cancel();
    }

    @WrapMethod(method = "actuallyHurt")
    private void decayingAftermath$damageBody(ServerLevel level, DamageSource source,
            float unmitigatedAmount, Operation<Void> original) {
        if ((Object) this instanceof ServerPlayer player) {
            if (player.isInvulnerableTo(level, source)) return;
            try (var scope = MedicalDamageContext.begin(player, source)) {
                float delivered = ServerMedicalSystem.applyIncomingHit(player, level, source, unmitigatedAmount);
                MedicalDamageContext.captureCruorHit(player, source, delivered);
                scope.complete();
            }
        } else original.call(level, source, unmitigatedAmount);
    }

    /** LivingEntity owns inherited health methods; vanilla player HP variable is completely excised. */
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
        @Override @Invoker("getDamageAfterMagicAbsorb")
        public abstract float decayingAftermath$afterMagic(DamageSource source, float amount);
        @Override @Invoker("doHurtEquipment")
        public abstract void decayingAftermath$hurtEquipment(DamageSource source, float amount, EquipmentSlot... slots);

        @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
        private void abjection$medicalHealthOnly(CallbackInfoReturnable<Float> cir) {
            if ((Object) this instanceof ServerPlayer player) {
                BodyModel body = decayingAftermath$body;
                cir.setReturnValue(body != null && body.mode() == BodySnapshot.Mode.DEAD ? 0.0F : player.getMaxHealth());
            }
        }

        @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
        private void abjection$removeVanillaHealthWrite(float value, CallbackInfo ci) {
            if ((Object) this instanceof Player) {
                ci.cancel();
            }
        }

        @Inject(method = "isDeadOrDying", at = @At("HEAD"), cancellable = true)
        private void abjection$medicalDeathCheckOnly(CallbackInfoReturnable<Boolean> cir) {
            if ((Object) this instanceof ServerPlayer) {
                BodyModel body = decayingAftermath$body;
                cir.setReturnValue(body != null && body.mode() == BodySnapshot.Mode.DEAD);
            }
        }

        @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
        private void abjection$medicalKill(ServerLevel level, CallbackInfo ci) {
            if ((Object) this instanceof ServerPlayer player) {
                ServerMedicalSystem.killMedically(player, player.damageSources().genericKill());
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
