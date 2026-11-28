package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.equipment.Equippable;
import java.util.List;

/** Native CU item sprites with native Minecraft item lifecycles, not a CU binary dependency. */
public final class SurvivalItems {
    public static final Item ANTIBIOTICS=oral("antibiotics",TreatmentKind.ANTIBIOTICS);
    public static final Item PAINKILLERS=oral("painkillers",TreatmentKind.PAINKILLERS);
    public static final Item COAGULANT=oral("coagulant",TreatmentKind.COAGULANT);
    public static final Item ANTIDEPRESSANTS=oral("antidepressants",TreatmentKind.ANTIDEPRESSANT);
    public static final Item ANXIOLYTIC=oral("anxiolytic",TreatmentKind.ANXIOLYTIC);
    public static final Item HERBAL_TEA=register("herbal_tea",new SurvivalConsumableItem(properties("herbal_tea").stacksTo(16)
            .component(DataComponents.CONSUMABLE,Consumable.builder().consumeSeconds(1.6f)
                    .animation(ItemUseAnimation.DRINK).sound(SoundEvents.GENERIC_DRINK).hasConsumeParticles(false).build()),
            TreatmentKind.HERBAL_TEA,.35f,0,0,true,"abjection.hint.herbal_tea"));
    public static final Item NALOXONE=plain("naloxone",16);
    public static final Item COMBAT_PEN=plain("combatpen",1);
    public static final Item BLOOD_BAG=plain("blood_bag",1);
    public static final Item SALINE=plain("saline",16);
    public static final Item ICE_PACK=plain("ice_pack",16);
    public static final Item CHEST_DRAIN=plain("chest_drain",16);
    public static final Item TWEEZERS=register("tweezers",new MedicalSupplyItem(properties("tweezers").durability(64),"tweezers"));
    public static final Item WATER_BOTTLE=drink("water_bottle",.45f,0,0);
    public static final Item DIRTY_WATER_BOTTLE=drink("dirty_water_bottle",.4f,.35f,0);
    public static final Item COFFEE=drink("coffee",.2f,0,1);
    public static final Item PEMMICAN=register("pemmican",new SurvivalConsumableItem(properties("pemmican").stacksTo(16)
            .food(new FoodProperties(8,.8f,false)),null,0,0,0,false,"abjection.hint.pemmican"));
    public static final Item BACKPACK=register("backpack",new MedicalSupplyItem(properties("backpack").stacksTo(1)
            .component(DataComponents.EQUIPPABLE,Equippable.builder(EquipmentSlot.CHEST)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).setDamageOnHurt(false).build()),"backpack"));
    public static final List<Item> ALL=List.of(ANTIBIOTICS,PAINKILLERS,COAGULANT,ANTIDEPRESSANTS,ANXIOLYTIC,HERBAL_TEA,
            NALOXONE,COMBAT_PEN,BLOOD_BAG,SALINE,ICE_PACK,CHEST_DRAIN,TWEEZERS,WATER_BOTTLE,DIRTY_WATER_BOTTLE,
            COFFEE,PEMMICAN,BACKPACK);
    private static boolean registered;
    private SurvivalItems(){ }
    public static void register() {
        if(registered)return;registered=true;
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output->ALL.forEach(output::accept));
    }
    private static Item oral(String id,TreatmentKind kind) {
        return register(id,new SurvivalConsumableItem(properties(id).stacksTo(16)
                .component(DataComponents.CONSUMABLE,Consumable.builder().consumeSeconds(1.6f)
                        .animation(ItemUseAnimation.EAT).sound(SoundEvents.GENERIC_EAT).hasConsumeParticles(false).build()),
                kind,0,0,0,false,"abjection.hint."+id));
    }
    private static Item drink(String id,float water,float pathogens,float caffeine) {
        return register(id,new SurvivalConsumableItem(properties(id).stacksTo(16)
                .component(DataComponents.CONSUMABLE,Consumable.builder().consumeSeconds(1.6f)
                        .animation(ItemUseAnimation.DRINK).sound(SoundEvents.GENERIC_DRINK).hasConsumeParticles(false).build()),
                null,water,pathogens,caffeine,true,"abjection.hint."+id));
    }
    private static Item plain(String id,int count){return register(id,new MedicalSupplyItem(properties(id).stacksTo(count),id));}
    private static Item.Properties properties(String id){return new Item.Properties().setId(key(id));}
    private static ResourceKey<Item> key(String id){return ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath(DecayingAftermath.MOD_ID,id));}
    private static Item register(String id,Item item){return Registry.register(BuiltInRegistries.ITEM,key(id),item);}
}
