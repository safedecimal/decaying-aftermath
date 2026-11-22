package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.AftermathClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class AftermathKeyboardMixin extends ClientInput {
    @Inject(method="tick()V",at=@At("TAIL"))
    private void decayingAftermath$lockKeys(CallbackInfo ci) {
        if (AftermathClient.locked()) { keyPresses=Input.EMPTY;moveVector=Vec2.ZERO; }
    }
}
