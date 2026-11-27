package io.github.safedecimal.decayingaftermath.medical;

import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/** Adds independent pools, never replaces vanilla or another mod's chest entries. */
public final class MedicalLoot {
    private static final Set<Identifier> CHESTS = Set.of(
            Identifier.withDefaultNamespace("chests/village/village_temple"),
            Identifier.withDefaultNamespace("chests/simple_dungeon"),
            Identifier.withDefaultNamespace("chests/stronghold_corridor"),
            Identifier.withDefaultNamespace("chests/stronghold_crossing"),
            Identifier.withDefaultNamespace("chests/stronghold_library"));
    private static boolean registered;

    private MedicalLoot() { }

    public static void register() {
        if (registered) return;
        registered = true;
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (!source.isBuiltin() || !CHESTS.contains(key.identifier())) return;
            table.withPool(pool(MedicalItems.BANDAGE, 0.15f, 1, 3));
            table.withPool(pool(MedicalItems.SPLINT, 0.05f, 1, 1));
            table.withPool(pool(MedicalItems.FENTANYL, 0.02f, 1, 1));
            table.withPool(pool(MedicalItems.ANESTHETIC, 0.04f, 1, 2));
            table.withPool(pool(MedicalItems.ANTISEPTIC, 0.12f, 1, 3));
            table.withPool(pool(SurvivalItems.ANTIBIOTICS,.08f,1,3));
            table.withPool(pool(SurvivalItems.PAINKILLERS,.10f,1,2));
            table.withPool(pool(SurvivalItems.COAGULANT,.04f,1,2));
            table.withPool(pool(SurvivalItems.ANTIDEPRESSANTS,.06f,1,2));
            table.withPool(pool(SurvivalItems.ANXIOLYTIC,.05f,1,2));
            table.withPool(pool(SurvivalItems.HERBAL_TEA,.08f,1,2));
            table.withPool(pool(SurvivalItems.NALOXONE,.05f,1,1));
            table.withPool(pool(SurvivalItems.COMBAT_PEN,.015f,1,1));
            table.withPool(pool(SurvivalItems.BLOOD_BAG,.025f,1,1));
            table.withPool(pool(SurvivalItems.SALINE,.08f,1,2));
            table.withPool(pool(SurvivalItems.CHEST_DRAIN,.035f,1,1));
            table.withPool(pool(SurvivalItems.TWEEZERS,.06f,1,1));
            table.withPool(pool(SurvivalItems.WATER_BOTTLE,.12f,1,2));
            table.withPool(pool(SurvivalItems.COFFEE,.06f,1,1));
            table.withPool(pool(SurvivalItems.PEMMICAN,.12f,1,3));
        });
    }

    private static LootPool.Builder pool(Item item, float chance, int minimum, int maximum) {
        return LootPool.lootPool()
                .setRolls(ContextIntProviders.exactly(1))
                .when(Holder.direct(LootItemRandomChanceCondition.randomChance(chance).build()))
                .add(LootItem.lootTableItem(item).apply(Holder.direct(
                        SetItemCountFunction.setCount(ContextIntProviders.between(minimum, maximum)).build())));
    }
}
