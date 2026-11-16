package io.github.safedecimal.decayingaftermath.mixin;

import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

/** Cancel ONLY medicinal listeners, not consumption, sound, bottle, stats or other effects. */
@Mixin(PotionContents.class)
public abstract class PotionDrinkMixin {
    @Inject(method="onConsume",at=@At("HEAD"),cancellable=true)
    private void decayingAftermath$wastedMedicinalDrink(Level level,LivingEntity entity,ItemStack stack,
            Consumable consumable,CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player) || !MedicalItems.isHealingPotion(stack)) return;
        PotionContents self=(PotionContents)(Object)this;
        List<MobEffectInstance> others=new ArrayList<>();
        for (var effect:self.getAllEffects()) if (!effect.is(MobEffects.INSTANT_HEALTH)&&!effect.is(MobEffects.REGENERATION)) others.add(new MobEffectInstance(effect));
        if (!others.isEmpty()) new PotionContents(Optional.empty(),self.customColor(),others,self.customName())
                .applyToLivingEntity(player,stack.getOrDefault(DataComponents.POTION_DURATION_SCALE,1f));
        MedicalNetwork.thought(player,PotionPolicy.WASTED_HEALING_THOUGHT_KEY);
        ci.cancel();
    }
}
