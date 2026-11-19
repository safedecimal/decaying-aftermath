package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.MedicalClient;
import io.github.safedecimal.decayingaftermath.client.MedicalInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class MedicalInventoryOpenMixin {
    @Shadow @Final private Minecraft minecraft;

    @ModifyVariable(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"), argsOnly = true)
    private Screen decayingAftermath$chooseInventory(Screen incoming) {
        if ((incoming instanceof InventoryScreen || incoming instanceof MedicalInventoryScreen)
                && MedicalClient.blocksInventory(minecraft)) return null;
        var player = minecraft.player;
        // Let Gui.setScreen initialize the replacement itself, without re-entering it from AFTER_INIT.
        if (incoming != null && incoming.getClass() == InventoryScreen.class
                && player != null && !player.hasInfiniteMaterials()) {
            return new MedicalInventoryScreen(player);
        }
        return incoming;
    }
}
