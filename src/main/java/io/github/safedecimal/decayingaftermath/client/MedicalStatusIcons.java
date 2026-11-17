package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** WoundView.icons and BleedImageFromLimb, not inventory pictures of applied supplies. */
public final class MedicalStatusIcons {
    public record Icon(String name, int width, int height) {}
    private static final String[] LEVELS = {"light", "medium", "heavy", "arterial"};
    private static final int[] WIDTHS = {13,15,23,25}, HEIGHTS = {17,22,32,40};
    private MedicalStatusIcons() {}
    public static Icon bleeding(BodySnapshot.PartSnapshot part) {
        if (part.bleeding() <= .0001f) return null;
        // The source's normalized thresholds are 3.75/100, 8/100 and 20/100.
        int level = part.bleeding()>.20f?3:part.bleeding()>.08f?2:part.bleeding()>.0375f?1:0;
        return new Icon("bleeding_"+(part.bandaged()?"covered_":"")+LEVELS[level], WIDTHS[level], HEIGHTS[level]);
    }
    public static void body(GuiGraphicsExtractor graphics, BodySnapshot.PartSnapshot part, MinecraftBodyLayout.Box box) {
        Icon bleed = bleeding(part), fracture = part.fractured()?new Icon("fracture",21,32):null;
        int count=(bleed==null?0:1)+(fracture==null?0:1), size=13;
        if (count==0) return;
        boolean horizontal = box.width() >= count*(size+2);
        int cx=box.x()+box.width()/2, cy=box.y()+box.height()/2, index=0;
        for (Icon icon : new Icon[]{bleed,fracture}) if(icon!=null) {
            int offset=(index++*2-count+1)*(size+2)/2;
            draw(graphics,icon,cx+(horizontal?offset:0),cy+(horizontal?0:offset),size,
                    icon.name().equals("fracture")?(part.splinted()?0xFFF2F2F2:0xFFFF8282):0xFFF08888);
        }
    }
    public static void draw(GuiGraphicsExtractor graphics, Icon icon, int cx, int cy, int size, int color) {
        if (icon==null) return;
        float ratio=size/(float)Math.max(icon.width(),icon.height());
        int w=Math.max(1,Math.round(icon.width()*ratio)),h=Math.max(1,Math.round(icon.height()*ratio));
        Identifier id=Identifier.fromNamespaceAndPath(DecayingAftermath.MOD_ID,"textures/gui/status/"+icon.name()+".png");
        // Destination size and source region are independent; the shorter overload crops to w/h.
        graphics.blit(RenderPipelines.GUI_TEXTURED,id,cx-w/2,cy-h/2,0f,0f,w,h,
                icon.width(),icon.height(),icon.width(),icon.height(),color);
    }
}
