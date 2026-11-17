package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.MedicalItems;
import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Lower left is remaining supply; lower right is the real stack count, including one. */
public final class MedicalSupplyOverlay {
    private MedicalSupplyOverlay() {}
    public static boolean replaceItemCount(GuiGraphicsExtractor graphics,ItemStack stack,int x,int y) {
        if(stack.isEmpty()||MedicalItems.treatment(stack)==null)return false;
        extract(graphics,stack,x,y);return true;
    }
    public static void extract(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (MedicalItems.treatment(stack) == null) return;
        var font = Minecraft.getInstance().font;
        String percent = SupplyOverlayLayout.percent(MedicalNetwork.remainingFill(stack));
        String count = Integer.toString(stack.getCount());
        int pw = font.width(percent), cw = font.width(count);
        float scale = SupplyOverlayLayout.scale(pw, cw);
        graphics.fill(x, y+11, x+16, y+16, 0xC0000000);
        graphics.pose().pushMatrix(); graphics.pose().translate(x, y+12);
        graphics.pose().scale(scale, scale);
        graphics.text(font, percent, 0, 0, 0xFFD9E7DD, false);
        graphics.pose().popMatrix();
        graphics.pose().pushMatrix(); graphics.pose().translate(x+SupplyOverlayLayout.countX(cw, scale), y+12);
        graphics.pose().scale(scale, scale);
        graphics.text(font, count, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popMatrix();
    }
}
