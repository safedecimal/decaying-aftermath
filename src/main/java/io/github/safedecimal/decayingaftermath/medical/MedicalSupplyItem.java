package io.github.safedecimal.decayingaftermath.medical;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

public final class MedicalSupplyItem extends Item {
    private final String hint;
    public MedicalSupplyItem(Properties properties,String id){super(properties);hint="decaying_aftermath.hint."+id;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,
            Consumer<Component> tooltip,TooltipFlag flag) {
        super.appendHoverText(stack,context,display,tooltip,flag);tooltip.accept(Component.translatable(hint));
    }
}
