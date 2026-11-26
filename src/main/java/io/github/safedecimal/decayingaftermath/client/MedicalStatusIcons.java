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

    enum BleedType {
        CAPILLARY("capillary"), VENOUS("venous"), ARTERIAL("arterial");

        final Icon icon;
        final String translationKey;

        BleedType(String name) {
            icon = new Icon("wound_type_"+name,16,16);
            translationKey = "decaying_aftermath.status.bleed_type."+name;
        }
    }

    static final float MIN_VISIBLE_BLEED = .0001f;
    // "arterial" is the legacy asset name for the highest severity, not a vessel type.
    private static final String[] LEVELS = {"light", "medium", "heavy", "arterial"};
    private static final int[] WIDTHS = {13,15,23,25}, HEIGHTS = {17,22,32,40};
    // WoundView level1/1596, serialized bleedGradient (Blend; seven color/two alpha keys).
    // Alpha keys are both 1. Times are the native unsigned-16-bit gradient times, not guesses.
    private static final int[] BLEED_TIMES = {0,66,3277,6554,13107,29491,65535};
    private static final float[][] BLEED_RGB = {
            {0,0,0},{.43396228551864624f,.43396228551864624f,.43396228551864624f},
            {1,1,1},{1,1,0},{.7358490228652954f,.5219544768333435f,0},
            {.5377358198165894f,.1018349751830101f,0},{.20754718780517578f,.025904959067702293f,0}};
    private MedicalStatusIcons() {}

    static boolean hasExternalBleeding(BodySnapshot.PartSnapshot part) {
        return part.externalBleeding().total() > MIN_VISIBLE_BLEED;
    }

    static List<BleedType> bleedingTypes(BodySnapshot.PartSnapshot part) {
        var profile = part.externalBleeding();
        List<BleedType> types=new ArrayList<>(3);
        if(profile.capillary()>MIN_VISIBLE_BLEED)types.add(BleedType.CAPILLARY);
        if(profile.venous()>MIN_VISIBLE_BLEED)types.add(BleedType.VENOUS);
        if(profile.arterial()>MIN_VISIBLE_BLEED)types.add(BleedType.ARTERIAL);
        return List.copyOf(types);
    }

    public static Icon bleeding(BodySnapshot.PartSnapshot part) {
        if (!hasExternalBleeding(part)) return null;
        // The source's normalized thresholds are 3.75/100, 8/100 and 20/100.
        float severity = part.bleeding();
        int level = severity>.20f?3:severity>.08f?2:severity>.0375f?1:0;
        return new Icon("bleeding_"+(part.bandageCare()>.005f?"covered_":"")+LEVELS[level], WIDTHS[level], HEIGHTS[level]);
    }
    public static Icon infection(BodySnapshot.PartSnapshot part) {
        if(part.infection()<.25f)return null;
        return new Icon(part.infectionDelta()>0?"infection_increasing":"infection_decreasing",24,22);
    }
    public static Icon disinfection(BodySnapshot.PartSnapshot part) {
        return part.disinfected() ? new Icon("disinfected",17,23) : null;
    }
    public static int disinfectionColor(BodySnapshot.PartSnapshot part) {
        int value = channel(.1f + part.disinfectionTicks() / (20f * 220));
        return 0xFF000000 | value << 16 | value << 8 | value;
    }
    /** Same BleedImageFromLimb gradient for covered/uncovered and procedure/body icons. */
    public static int bleedingColor(BodySnapshot.PartSnapshot part) {
        float value=part.bleeding();int upper=1;
        while(upper<BLEED_TIMES.length-1&&value>BLEED_TIMES[upper]/65535f)upper++;
        float lowerTime=BLEED_TIMES[upper-1]/65535f,upperTime=BLEED_TIMES[upper]/65535f;
        float t=Math.clamp((value-lowerTime)/(upperTime-lowerTime),0,1);
        float[] a=BLEED_RGB[upper-1],b=BLEED_RGB[upper];
        return 0xFF000000|channel(a[0]+(b[0]-a[0])*t)<<16
                |channel(a[1]+(b[1]-a[1])*t)<<8|channel(a[2]+(b[2]-a[2])*t);
    }
    /** WoundView.UpdateView: Color32.Lerp(white, (163,0,182,255), infectionAmount/100). */
    public static int infectionColor(BodySnapshot.PartSnapshot part) {
        float t=part.infection();
        return 0xFF000000|((int)(255+(163-255)*t))<<16
                |((int)(255-255*t))<<8|(int)(255+(182-255)*t);
    }
    public static int fractureColor(BodySnapshot.PartSnapshot part) {
        // CU uses boneHealTimer, not splinted. No timer is transmitted by this body model:
        // a pending fracture remains red until healed instead of whitening on splint placement.
        return part.fractured()?0xFFFF0000:0xFFFFFFFF;
    }
    private static int channel(float value) {return (int)(Math.clamp(value,0,1)*255);}
    public static List<Placement> placements(BodySnapshot.PartSnapshot part,MinecraftBodyLayout.Box box) {
        List<Icon> icons=new ArrayList<>();Icon bleed=bleeding(part),infection=infection(part);
        if(bleed!=null)icons.add(bleed);if(part.fractured())icons.add(new Icon("fracture",21,32));
        if(infection!=null)icons.add(infection);
        Icon protection=disinfection(part);if(protection!=null)icons.add(protection);
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
            int tint=icon.name().startsWith("infection_")?infectionColor(part)
                    :icon.name().equals("fracture")?fractureColor(part)
                    :icon.name().equals("disinfected")?disinfectionColor(part):bleedingColor(part);
            result.add(new Placement(icon,x,y,size,tint));
        }
        return List.copyOf(result);
    }
    public static void body(GuiGraphicsExtractor graphics, BodySnapshot.PartSnapshot part, MinecraftBodyLayout.Box box) {
        for(var p:placements(part,box)) {
            int color=p.color();
            if(p.icon().name().startsWith("bleeding_")) {
                float ratio=p.size()/(float)Math.max(p.icon().width(),p.icon().height());
                int w=Math.max(1,Math.round(p.icon().width()*ratio)),h=Math.max(1,Math.round(p.icon().height()*ratio));
                graphics.fill(p.x()-w/2-1,p.y()-h/2-1,p.x()-w/2+w+1,p.y()-h/2+h+1,0xFF210909);
                drawBleeding(graphics,part,p.x(),p.y(),p.size(),visibleBleedingColor(color));
            } else {
                draw(graphics,p.icon(),p.x(),p.y(),p.size(),color);
            }
        }
    }
    public static void drawBleeding(GuiGraphicsExtractor graphics, BodySnapshot.PartSnapshot part, int cx, int cy, int size, int color) {
        Icon droplet=bleeding(part);
        if(droplet==null) return;
        draw(graphics,droplet,cx,cy,size,color);
        var types=bleedingTypes(part);
        int count=types.size();
        if(count==0) return;
        float ratio=size/(float)Math.max(droplet.width(),droplet.height());
        int w=Math.max(1,Math.round(droplet.width()*ratio)),h=Math.max(1,Math.round(droplet.height()*ratio));
        int gap=(w>=14&&count>1)?1:0;
        int badgeSize=count==1?Math.max(3,Math.round(w*.55f)):Math.max(2,(w-2-gap*(count-1))/count);
        int totalW=count*badgeSize+(count-1)*gap;
        int left=cx-totalW/2;
        int typeCy=cy+Math.round(h*.15f);
        for(int i=0;i<count;i++) {
            int typeCx=left+i*(badgeSize+gap)+badgeSize/2;
            draw(graphics,types.get(i).icon,typeCx,typeCy,badgeSize,0xFFFFFFFF);
        }
    }
    public static int visibleBleedingColor(int nativeColor) {
        return 0xFF000000|(Math.max(192,(nativeColor>>>16)&255)<<16)|(nativeColor&0xFFFF);
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
