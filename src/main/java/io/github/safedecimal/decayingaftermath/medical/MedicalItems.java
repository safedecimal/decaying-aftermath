package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.alchemy.PotionContents;

/** Registered Minecraft items; all procedures consume inventory on the server. */
public final class MedicalItems {
    public static final Item BANDAGE = item("bandage", 16);
    public static final Item PRESSURE_DRESSING = item("pressure_dressing", 16);
    public static final Item SPLINT = item("splint", 16);
    public static final Item TOURNIQUET = item("tourniquet", 16);
    public static final Item FENTANYL = item("fentanyl", 1);
    public static final Item ANESTHETIC = item("anesthetic", 16);
    public static final Item ANTISEPTIC = item("antiseptic", 16);
    private static boolean registered;

    private MedicalItems() { }

    public static void register() {
        if (registered) return;
        registered = true;
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(BANDAGE);
            output.accept(PRESSURE_DRESSING);
            output.accept(SPLINT);
            output.accept(TOURNIQUET);
            output.accept(FENTANYL);
            output.accept(ANESTHETIC);
            output.accept(ANTISEPTIC);
        });
    }

    /** Null means this item cannot be dragged onto the medical body. */
    public static TreatmentKind treatment(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item == BANDAGE) return TreatmentKind.BANDAGE;
        if (item == PRESSURE_DRESSING) return TreatmentKind.PRESSURE_DRESSING;
        if (item == SPLINT) return TreatmentKind.SPLINT;
        if (item == TOURNIQUET) return TreatmentKind.TOURNIQUET;
        if (item == FENTANYL) return TreatmentKind.FENTANYL;
        if (item == ANESTHETIC) return TreatmentKind.ANESTHETIC;
        if (item == ANTISEPTIC) return TreatmentKind.ANTISEPTIC;
        if(item==SurvivalItems.NALOXONE)return TreatmentKind.NALOXONE;
        if(item==SurvivalItems.COMBAT_PEN)return TreatmentKind.COMBAT_STIMULANT;
        if(item==SurvivalItems.BLOOD_BAG)return TreatmentKind.BLOOD_BAG;
        if(item==SurvivalItems.SALINE)return TreatmentKind.SALINE;
        if(item==SurvivalItems.ICE_PACK)return TreatmentKind.ICE_PACK;
        if(item==SurvivalItems.CHEST_DRAIN)return TreatmentKind.CHEST_DRAIN;
        if(item==SurvivalItems.TWEEZERS)return TreatmentKind.REMOVE_FOREIGN_BODY;
        if (!isRegularPotion(stack)) return null;

        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        boolean regeneration = false;
        for (var effect : contents.getAllEffects()) {
            if (effect.getEffect().equals(MobEffects.INSTANT_HEALTH)) return TreatmentKind.HEALING_POTION;
            if (effect.getEffect().equals(MobEffects.REGENERATION)) regeneration = true;
        }
        return regeneration ? TreatmentKind.REGENERATION_POTION : TreatmentKind.POTION;
    }

    /** Only healing/regeneration drinkable potions receive the wasted-drink rule. */
    public static boolean isHealingPotion(ItemStack stack) {
        if (!isRegularPotion(stack)) return false;
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (var effect : contents.getAllEffects()) {
            if (effect.getEffect().equals(MobEffects.INSTANT_HEALTH)
                    || effect.getEffect().equals(MobEffects.REGENERATION)) return true;
        }
        return false;
    }

    private static boolean isRegularPotion(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PotionItem
                && !(stack.getItem() instanceof ThrowablePotionItem);
    }

    private static Item item(String name, int stackSize) {
        Identifier id = Identifier.fromNamespaceAndPath(DecayingAftermath.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key,
                new Item(new Item.Properties().setId(key).stacksTo(stackSize)));
    }
}
