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
