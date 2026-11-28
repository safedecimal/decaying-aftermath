package io.github.safedecimal.decayingaftermath.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes vanilla and condition hearts from the hotbar; shifts armor bar cleanly down to the bottom row. */
@Mixin(Hud.class)
public abstract class ConditionHeartsMixin {
    @Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true)
    private void abjection$hideHearts(GuiGraphicsExtractor graphics, Player player,
            int left, int top, int rowHeight, int regenerationIndex, float maxHealth,
            int currentHealth, int displayedHealth, int absorption, boolean vanillaBlink,
            CallbackInfo ci) {
        ci.cancel();
    }

    @ModifyVariable(method = "extractArmor", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static int abjection$alignArmorWithoutHearts(int top) {
        return top + 10;
    }
}
