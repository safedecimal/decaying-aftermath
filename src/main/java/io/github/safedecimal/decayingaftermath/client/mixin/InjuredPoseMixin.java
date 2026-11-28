package io.github.safedecimal.decayingaftermath.client.mixin;

import io.github.safedecimal.decayingaftermath.client.InjuredAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies injured.json animations when badly hurt and collapsed ragdoll pose when unable to walk. */
@Mixin(HumanoidModel.class)
public abstract class InjuredPoseMixin<T extends HumanoidRenderState> {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void abjection$applyInjuredOrCollapsedPose(T state, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar) {
            InjuredAnimations.apply((HumanoidModel<?>) (Object) this, avatar);
        }
    }
}
