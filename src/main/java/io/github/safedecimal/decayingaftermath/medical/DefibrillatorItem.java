package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.network.DefibNetwork;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class DefibrillatorItem extends Item {
    public static final String BATTERY_KEY="decaying_aftermath:defib_battery";
    private final boolean automatic;
    public DefibrillatorItem(Properties properties,boolean automatic) {
        super(properties); this.automatic=automatic;
    }
    public boolean automatic() { return automatic; }
    public static float battery(ItemStack stack) {
        float legacy=stack.getMaxDamage()>0?1-stack.getDamageValue()/(float)stack.getMaxDamage():1;
        return DefibGame.sanitizeBattery(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY)
                .copyTag().getFloatOr(BATTERY_KEY,legacy));
    }
    public static void battery(ItemStack stack,float remaining) {
        float value=DefibGame.sanitizeBattery(remaining);
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putFloat(BATTERY_KEY,value));
        if(stack.getMaxDamage()>0)stack.setDamageValue(Math.round((1-value)*stack.getMaxDamage()));
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand) {
        if(player instanceof ServerPlayer server)DefibNetwork.open(server,server,hand,automatic);
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity entity,InteractionHand hand) {
        if(!(entity instanceof Player))return InteractionResult.PASS;
        if(player instanceof ServerPlayer server && entity instanceof ServerPlayer patient)
            DefibNetwork.open(server,patient,hand,automatic);
        return InteractionResult.SUCCESS;
    }
}
