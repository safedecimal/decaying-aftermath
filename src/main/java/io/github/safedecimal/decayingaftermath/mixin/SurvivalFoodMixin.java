package io.github.safedecimal.decayingaftermath.mixin;

import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Eating remains vanilla. It supplies metabolism, not vanilla HP or instantaneous tissue repair. */
@Mixin(FoodProperties.class)
public abstract class SurvivalFoodMixin {
    @Inject(method="onConsume",at=@At("TAIL"))
    private void decayingAftermath$feedReserves(Level level,LivingEntity entity,ItemStack stack,
            Consumable consumable,CallbackInfo ci) {
        if(entity instanceof ServerPlayer player && ServerMedicalSystem.canAct(player)) {
            int nutrition=((FoodProperties)(Object)this).nutrition();
            ServerMedicalSystem.get(player).consume(Math.max(0,nutrition)*.05f,0);
        }
    }
}
