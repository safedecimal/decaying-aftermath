package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/** Biome/dimension inputs are sampled on the server, never trusted from a client packet. */
public final class ServerSurvivalEnvironment {
    private ServerSurvivalEnvironment(){ }
    public static BodyEnvironment sample(ServerPlayer player,float mass) {
        SurvivalConfig config=SurvivalConfig.get();
        float ambient=20;
        if(config.temperature) {
            ambient=player.level().getBiome(player.blockPosition()).value().getBaseTemperature()*25;
            ambient-=Math.max(0,player.getY()-96)*.035f;
            if(net.minecraft.world.level.Level.NETHER.equals(player.level().dimension()))ambient=48;
            if(player.isOnFire())ambient=75;
            if(player.getTicksFrozen()>0)ambient=-25;
        }
        float insulation=.05f;
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            var gear=player.getItemBySlot(slot); if(gear.isEmpty())continue;
            insulation+=gear.is(Items.LEATHER_HELMET)||gear.is(Items.LEATHER_CHESTPLATE)
                    ||gear.is(Items.LEATHER_LEGGINGS)||gear.is(Items.LEATHER_BOOTS)?.18f:.07f;
        }
        float capacity=config.carryingCapacity;
        if(player.getItemBySlot(EquipmentSlot.CHEST).is(SurvivalItems.BACKPACK))capacity+=config.backpackCapacityBonus;
        boolean wet=player.isInWater()||player.level().isRainingAt(player.blockPosition());
        // Stored breath belongs to vanilla AirSupply; no free recovery once that air is depleted.
        boolean breathable=!player.isUnderWater()||player.getAirSupply()>0;
        return new BodyEnvironment(ambient,insulation,wet,breathable,player.isSleeping(),player.isSprinting(),
                !player.isPassenger()&&(player.getDeltaMovement().horizontalDistance()>.01
                        ||player.position().subtract(player.oldPosition()).horizontalDistance()>.01),
                config.encumbrance?mass:0,capacity,config.metabolism,config.temperature);
    }
}
