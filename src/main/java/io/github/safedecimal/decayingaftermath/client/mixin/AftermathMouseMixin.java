package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.AftermathClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class AftermathMouseMixin {
    @Inject(method="turnPlayer(D)V",at=@At("HEAD"),cancellable=true)
    private void decayingAftermath$lockMouse(double delta,CallbackInfo ci) {
        if (AftermathClient.locked()) ci.cancel();
    }
}
