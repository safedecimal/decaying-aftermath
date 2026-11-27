package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.AABB;

/** Biome/dimension and mental-health environment inputs sampled on the server, never trusted from a client packet. */
public final class ServerSurvivalEnvironment {
    private ServerSurvivalEnvironment(){ }

    public static BodyEnvironment sample(ServerPlayer player,float mass) {
        SurvivalConfig config=SurvivalConfig.get();
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        float ambient=20;
        if(config.temperature) {
            ambient=level.getBiome(pos).value().getBaseTemperature()*25;
            ambient-=Math.max(0,player.getY()-96)*.035f;
            if(Level.NETHER.equals(level.dimension()))ambient=48;
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
        boolean wet=player.isInWater()||level.isRainingAt(pos);
        // Stored breath belongs to vanilla AirSupply; no free recovery once that air is depleted.
        boolean breathable=!player.isUnderWater()||player.getAirSupply()>0;

        // Mental health environmental probes (sampled safely on server)
        BlockPos eyePos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        int lightLevel = level.getMaxLocalRawBrightness(eyePos);
        int skyLight = level.getBrightness(LightLayer.SKY, eyePos);
        boolean hasSky = level.canSeeSky(eyePos) || skyLight >= 12;
        boolean overworld = Level.OVERWORLD.equals(level.dimension());
        boolean isDay = overworld && level.isBrightOutside();
        boolean daylightSurface = overworld && isDay && hasSky && !level.isThundering() && lightLevel >= 10;
        boolean undergroundCave = (!overworld || (skyLight == 0 && eyePos.getY() < 55)) && lightLevel <= 7;

        boolean nearCampfire = false;
        boolean naturalGreenery = false;
        float jukeboxMood = 0;

        int minX = pos.getX() - 8, maxX = pos.getX() + 8;
        int minY = pos.getY() - 4, maxY = pos.getY() + 4;
        int minZ = pos.getZ() - 8, maxZ = pos.getZ() + 8;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    cursor.set(bx, by, bz);
                    if (!level.isLoaded(cursor)) continue;
                    var state = level.getBlockState(cursor);
                    var block = state.getBlock();
                    if ((block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE)
                            && state.getValueOrElse(CampfireBlock.LIT, false)
                            && cursor.distSqr(pos) <= 36) {
                        nearCampfire = true;
                    } else if (block == Blocks.JUKEBOX && level.getBlockEntity(cursor) instanceof JukeboxBlockEntity jukebox) {
                        if (jukebox.getSongPlayer().isPlaying()) {
                            ItemStack disc = jukebox.getTheItem();
                            if (disc.is(Items.MUSIC_DISC_11) || disc.is(Items.MUSIC_DISC_13)) {
                                jukeboxMood = -1.0f;
                            } else if (disc.has(DataComponents.JUKEBOX_PLAYABLE) && jukeboxMood >= 0) {
                                jukeboxMood = 1.0f;
                            }
                        }
                    } else if (!naturalGreenery && (block == Blocks.GRASS_BLOCK || block == Blocks.DANDELION
                            || block == Blocks.POPPY || block == Blocks.SUNFLOWER || block == Blocks.AZURE_BLUET
                            || block == Blocks.CORNFLOWER || block == Blocks.LILY_OF_THE_VALLEY)) {
                        if (cursor.distSqr(pos) <= 25) naturalGreenery = true;
                    }
                }
            }
        }

        AABB mobBox = player.getBoundingBox().inflate(12.0);
        int hostileMobs = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, mobBox)) {
            if (!mob.isAlive()) continue;
            if (mob instanceof Enemy || mob.getTarget() == player) {
                hostileMobs++;
                if (hostileMobs >= 4) break;
            }
        }

        int timeSinceRest = Math.max(0, player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)));

        return new BodyEnvironment(ambient,insulation,wet,breathable,player.isSleeping(),player.isSprinting(),
                !player.isPassenger()&&(player.getDeltaMovement().horizontalDistance()>.01
                        ||player.position().subtract(player.oldPosition()).horizontalDistance()>.01),
                config.encumbrance?mass:0,capacity,config.metabolism,config.temperature,
                lightLevel,daylightSurface,nearCampfire,undergroundCave,naturalGreenery,
                hostileMobs,jukeboxMood,timeSinceRest,config.mentalHealth);
    }
}
