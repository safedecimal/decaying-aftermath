package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.SurvivalItems;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;

/** Crouch-use an empty bottle to collect untreated surface water; ordinary bottle use is unchanged. */
public final class SurvivalWaterSources {
    private static boolean registered;
    private SurvivalWaterSources(){ }
    public static void register() {
        if(registered)return;registered=true;
        UseItemCallback.EVENT.register((player,level,hand)->{
            ItemStack stack=player.getItemInHand(hand);
            if(!player.isShiftKeyDown()||!stack.is(Items.GLASS_BOTTLE))return InteractionResult.PASS;
            var eye=player.getEyePosition();
            var hit=level.clip(new ClipContext(eye,eye.add(player.getLookAngle().scale(player.blockInteractionRange())),
                    ClipContext.Block.OUTLINE,ClipContext.Fluid.SOURCE_ONLY,player));
            if(hit.getType()!=HitResult.Type.BLOCK||!level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER))return InteractionResult.PASS;
            if(!level.mayInteract(player,hit.getBlockPos())||!player.mayUseItemAt(hit.getBlockPos(),hit.getDirection(),stack))return InteractionResult.FAIL;
            if(player instanceof ServerPlayer server) {
                if(!ServerMedicalSystem.canAct(server))return InteractionResult.FAIL;
                ItemStack filled=new ItemStack(SurvivalItems.DIRTY_WATER_BOTTLE);
                if(!player.getAbilities().instabuild)stack.shrink(1);
                if(stack.isEmpty())player.setItemInHand(hand,filled);
                else if(!player.getInventory().add(filled))player.drop(filled,false,net.minecraft.util.Prediction.SERVER_ONLY);
                player.containerMenu.broadcastChanges();
            }
            return InteractionResult.SUCCESS;
        });
    }
}
