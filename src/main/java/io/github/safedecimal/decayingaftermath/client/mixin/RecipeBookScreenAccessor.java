package io.github.safedecimal.decayingaftermath.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads recipe-book visibility for panel layout without altering recipe-book behavior. */
@Mixin(AbstractRecipeBookScreen.class)
public interface RecipeBookScreenAccessor {
    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> decayingAftermath$getRecipeBookComponent();
}
