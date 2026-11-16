package io.github.safedecimal.decayingaftermath.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos") int decayingAftermath$getLeft();
    @Accessor("leftPos") void decayingAftermath$setLeft(int left);
    @Accessor("topPos") int decayingAftermath$getTop();
    @Accessor("imageWidth") int decayingAftermath$getImageWidth();
    @Accessor("imageHeight") int decayingAftermath$getImageHeight();
}
