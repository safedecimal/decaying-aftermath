package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/** WoundView.icons and BleedImageFromLimb, not inventory pictures of applied supplies. */
public final class MedicalStatusIcons {
    public record Icon(String name, int width, int height) {}
    public record Placement(Icon icon,int x,int y,int size,int color) { }
    private static final String[] LEVELS = {"light", "medium", "heavy", "arterial"};
    private static final int[] WIDTHS = {13,15,23,25}, HEIGHTS = {17,22,32,40};
    private MedicalStatusIcons() {}
    public static Icon bleeding(BodySnapshot.PartSnapshot part) {
        if (part.bleeding() <= .0001f) return null;
        // The source's normalized thresholds are 3.75/100, 8/100 and 20/100.
        int level = part.bleeding()>.20f?3:part.bleeding()>.08f?2:part.bleeding()>.0375f?1:0;
        return new Icon("bleeding_"+(part.bandageCare()>.005f?"covered_":"")+LEVELS[level], WIDTHS[level], HEIGHTS[level]);
    }
    public static Icon infection(BodySnapshot.PartSnapshot part) {
        if(part.infection()<.25f)return null;
        return new Icon(part.disinfected()?"infection_decreasing":"infection_increasing",24,22);
    }
    public static List<Placement> placements(BodySnapshot.PartSnapshot part,MinecraftBodyLayout.Box box) {
        List<Icon> icons=new ArrayList<>();Icon bleed=bleeding(part),infection=infection(part);
        if(bleed!=null)icons.add(bleed);if(part.fractured())icons.add(new Icon("fracture",21,32));
        if(infection!=null)icons.add(infection);
        int count=icons.size(),size=Math.min(13,Math.max(1,Math.min(box.width(),box.height())));
        if(count==0)return List.of();
        int cx=box.x()+box.width()/2,cy=box.y()+box.height()/2;
        boolean horizontal=box.width()>=count*(size+2),vertical=box.height()>=count*(size+2);
        List<Placement> result=new ArrayList<>();
        for(int index=0;index<count;index++) {
            int x=cx,y=cy;
            if(horizontal||vertical) {
                int offset=(index*2-count+1)*(size+2)/2;
                if(horizontal)x+=offset;else y+=offset;
            } else {
                int columns=2,rows=(count+1)/2,row=index/columns;
                size=Math.min(size,Math.min(box.width()/columns-2,box.height()/rows-2));
                int rowColumns=Math.min(columns,count-row*columns);
                x+=(index%columns*2-rowColumns+1)*(size+2)/2;
                y+=(row*2-rows+1)*(size+2)/2;
            }
            Icon icon=icons.get(index);
            int tint=icon.name().startsWith("infection_")?0xFFFFFFFF
                    :icon.name().equals("fracture")?(part.splinted()?0xFFF2F2F2:0xFFFF8282):0xFFF08888;
            result.add(new Placement(icon,x,y,size,tint));
        }
        return List.copyOf(result);
    }
    public static void body(GuiGraphicsExtractor graphics, BodySnapshot.PartSnapshot part, MinecraftBodyLayout.Box box) {
        for(var p:placements(part,box))draw(graphics,p.icon(),p.x(),p.y(),p.size(),p.color());
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
