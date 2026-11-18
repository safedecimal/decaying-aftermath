package io.github.safedecimal.decayingaftermath.server;

import io.github.safedecimal.decayingaftermath.medical.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.ContainerComponent;

/** Includes armour, offhand, crafting inputs, cursor and native nested container contents once. */
public final class InventoryMass {
    private InventoryMass(){ }
    public static float carried(ServerPlayer player) {
        float total=0;
        // Slot 0 is a preview of the crafting result, NOT an additional owned item.
        for(int i=1;i<player.inventoryMenu.slots.size();i++)total+=mass(player.inventoryMenu.getSlot(i).getItem(),0);
        total+=mass(player.containerMenu.getCarried(),0);
        return Math.min(10000,total);
    }
    public static float mass(ItemStack stack,int depth) {
        if(stack.isEmpty())return 0;
        SurvivalConfig c=SurvivalConfig.get();
        float total=stack.getCount()*ItemMassPolicy.unitMass(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getItem() instanceof BlockItem,c.blockMass,c.ordinaryItemMass,c.itemMass);
        var container=stack.get(DataComponents.CONTAINER);
        var bundle=stack.get(DataComponents.BUNDLE_CONTENTS);
        var projectiles=stack.get(DataComponents.CHARGED_PROJECTILES);
        if(depth>=8 && (container!=null||bundle!=null||projectiles!=null))return Math.min(10000,total+1000);
        // Stored contents are per stack unit. A counted modded container must not hide its extra copies.
        float inside=0;
        if(container!=null)inside+=children(container,depth);
        if(bundle!=null)inside+=children(bundle,depth);
        if(projectiles!=null)inside+=children(projectiles,depth);
        return Math.min(10000,total+inside*stack.getCount());
    }
    private static float children(ContainerComponent<?> component,int depth) {
        try(var stream=component.itemCopies()) {return (float)stream.mapToDouble(s->mass(s,depth+1)).sum();}
    }
}
