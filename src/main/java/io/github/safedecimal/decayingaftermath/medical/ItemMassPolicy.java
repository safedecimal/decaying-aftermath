package io.github.safedecimal.decayingaftermath.medical;

import java.util.Map;

/** Scaled game mass, not the impossible real mass of a cubic-metre Minecraft stone block. */
public final class ItemMassPolicy {
    private ItemMassPolicy(){ }
    private static final Map<String,Float> FIXED=Map.ofEntries(
            Map.entry("minecraft:iron_ingot",.18f),Map.entry("minecraft:gold_ingot",.25f),
            Map.entry("minecraft:diamond",.03f),Map.entry("minecraft:torch",.01f),
            Map.entry("minecraft:arrow",.025f),Map.entry("minecraft:feather",.005f),
            Map.entry("minecraft:bucket",.5f),Map.entry("minecraft:water_bucket",1.5f),
            Map.entry("minecraft:lava_bucket",1.8f),Map.entry("minecraft:shulker_box",1.5f),
            Map.entry("minecraft:bundle",.2f),Map.entry("abjection:backpack",2f),
            Map.entry("abjection:water_bottle",.5f),Map.entry("abjection:dirty_water_bottle",.5f),
            Map.entry("abjection:blood_bag",.45f),Map.entry("abjection:saline",.5f));
    public static float unitMass(String id,boolean block,float blockMass,float ordinaryMass,Map<String,Float> overrides) {
        Float custom=overrides.get(id);
        if(custom!=null&&Float.isFinite(custom)&&custom>=0)return custom;
        Float fixed=FIXED.get(id); if(fixed!=null)return fixed;
        if(id.endsWith("_chestplate"))return 4;
        if(id.endsWith("_leggings"))return 3;
        if(id.endsWith("_helmet"))return 1.5f;
        if(id.endsWith("_boots"))return 1.5f;
        if(id.endsWith("_sword")||id.endsWith("_axe")||id.endsWith("_pickaxe"))return .9f;
        return Math.max(0,block?blockMass:ordinaryMass);
    }
    public static float movementPenalty(float ratio) {
        return Float.isFinite(ratio)?Math.clamp((ratio-1)*.20f,0,.75f):.75f;
    }
}
