package io.github.safedecimal.decayingaftermath.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.DamageCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes vanilla /damage command since global HP damage is excised. */
@Mixin(DamageCommand.class)
public abstract class DamageCommandMixin {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void abjection$removeDamageCommand(CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext context, CallbackInfo ci) {
        ci.cancel();
    }
}
