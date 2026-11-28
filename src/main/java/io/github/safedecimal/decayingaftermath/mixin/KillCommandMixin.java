package io.github.safedecimal.decayingaftermath.mixin;

import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Reworks /kill to execute a medical death on players rather than HP damage. */
@Mixin(KillCommand.class)
public abstract class KillCommandMixin {
    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private static void abjection$medicalKill(CommandSourceStack source,
            Collection<? extends Entity> targets, CallbackInfoReturnable<Integer> cir) {
        int count = 0;
        for (Entity entity : targets) {
            if (entity instanceof ServerPlayer player) {
                ServerMedicalSystem.killMedically(player, player.damageSources().genericKill());
            } else {
                entity.kill(source.getLevel());
            }
            count++;
        }
        if (targets.size() == 1) {
            Entity single = targets.iterator().next();
            source.sendSuccess(() -> Component.translatable("commands.kill.success.single", single.getDisplayName()), true);
        } else {
            int finalCount = count;
            source.sendSuccess(() -> Component.translatable("commands.kill.success.multiple", finalCount), true);
        }
        cir.setReturnValue(count);
    }
}
