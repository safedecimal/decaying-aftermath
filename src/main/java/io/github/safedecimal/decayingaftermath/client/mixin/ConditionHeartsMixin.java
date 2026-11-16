package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.PulseState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the player's heart strip only; armor, food, air and mount hearts stay vanilla. */
@Mixin(Hud.class)
public abstract class ConditionHeartsMixin {
    @Unique
    private static final Identifier DECAYING_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
    @Unique
    private static final Identifier DECAYING_FULL = Identifier.withDefaultNamespace("hud/heart/full");
    @Unique
    private static final Identifier DECAYING_HALF = Identifier.withDefaultNamespace("hud/heart/half");
    @Unique
    private static final Identifier DECAYING_FULL_BEAT = Identifier.withDefaultNamespace("hud/heart/full_blinking");
    @Unique
    private static final Identifier DECAYING_HALF_BEAT = Identifier.withDefaultNamespace("hud/heart/half_blinking");

    @Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true)
    private void decayingAftermath$conditionHearts(GuiGraphicsExtractor graphics, Player player,
            int left, int top, int rowHeight, int regenerationIndex, float maxHealth,
            int currentHealth, int displayedHealth, int absorption, boolean vanillaBlink,
            CallbackInfo ci) {
        int halfHearts = PulseState.filledHalfHearts();
        boolean beat = PulseState.beatPhase() < 0.12;
        for (int index = 0; index < 10; index++) {
            int x = left + index * 8;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DECAYING_CONTAINER, x, top, 9, 9);
            int remaining = halfHearts - index * 2;
            if (remaining <= 0) {
                continue;
            }
            Identifier sprite = remaining >= 2
                    ? (beat ? DECAYING_FULL_BEAT : DECAYING_FULL)
                    : (beat ? DECAYING_HALF_BEAT : DECAYING_HALF);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, top, 9, 9);
        }
        ci.cancel();
    }
}
