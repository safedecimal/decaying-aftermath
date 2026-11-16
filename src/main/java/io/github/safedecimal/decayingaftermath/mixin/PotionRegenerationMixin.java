package io.github.safedecimal.decayingaftermath.mixin;

import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Disable food-based HP recovery without deleting hunger or wasting saturation on cancelled healing. */
@Mixin(FoodData.class)
public abstract class PotionRegenerationMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object decayingAftermath$noFoodRegeneration(GameRules rules, GameRule<?> rule, ServerPlayer player) {
        if (rule == GameRules.NATURAL_HEALTH_REGENERATION && ServerMedicalSystem.isReady(player)) return Boolean.FALSE;
        return rules.get(rule);
    }

    /** Preserve peaceful hunger/saturation recovery but not its separate one-HP healing call. */
    @Mixin(ServerPlayer.class)
    public abstract static class Peaceful {
        @Redirect(method = "tickRegeneration", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
        private void decayingAftermath$noPeacefulHp(ServerPlayer player, float amount) {
            if (!ServerMedicalSystem.isReady(player)) player.heal(amount);
        }
    }
}
