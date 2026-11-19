package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.network.DefibNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class DefibrillatorItem extends Item {
    private final boolean automatic;
    public DefibrillatorItem(Properties properties,boolean automatic) {
        super(properties); this.automatic=automatic;
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
