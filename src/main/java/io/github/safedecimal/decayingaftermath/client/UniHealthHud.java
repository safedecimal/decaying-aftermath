package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import io.github.safedecimal.decayingaftermath.medical.MedicalPhysiology;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/** Native UNI-HEALTH metal/CRT device, not a reskinned vanilla heart/hunger strip. */
public final class UniHealthHud {
    public static final int GREEN=0xFF31DF83, DIM=0xFFA8C3AE, RED=0xFFE96C6C, AMBER=0xFFE9B66B;
    private static final int READOUT=0xFFF0FFEF;
    private static final Style OSD=Style.EMPTY.withFont(new FontDescription.Resource(MedicalClient.id("uni_health_osd")));
    private static int lastEffectCount;
    public record Badge(String icon,String label,int color,int amplifier,int seconds) { }
    private UniHealthHud() { }
    public static boolean enabled(Minecraft client) {
        return client!=null && client.player!=null && client.level!=null && !client.player.isSpectator()
                && !AftermathClient.active() && ClientMedicalState.received();
    }
    public static int lastEffectCount() {return lastEffectCount;}
    public static void extract(GuiGraphicsExtractor g,DeltaTracker delta) {
        Minecraft client=Minecraft.getInstance();if(!enabled(client))return;
        BodySnapshot body=ClientMedicalState.snapshot();
        var box=UniHealthHudLayout.at(g.guiWidth(),g.guiHeight());
        g.pose().pushMatrix();g.pose().translate(box.x(),box.y());g.pose().scale(box.scale(),box.scale());
        backdrop(g);
        pulse(g,body);
        text(g,"UNI HEALTH",109,8,DIM,.85f);
        icon(g,"brain",188,5,12,READOUT);text(g,percent(body.consciousness()),203,8,READOUT,.85f);
        icon(g,"energy",244,4,13,READOUT);text(g,percent(body.energy()),260,8,READOUT,.85f);
        metric(g,"food",28,READOUT,percent(body.survival().nutrition()));
        metric(g,"water",71,READOUT,percent(body.survival().hydration()));
        metric(g,"blood",114,body.blood()<.3f?RED:READOUT,percent(body.blood()));
        metric(g,"oxygen",157,body.oxygen()<.5f?AMBER:READOUT,percent(body.oxygen()));
        metric(g,"temperature",199,body.survival().thermalStress()>.2?AMBER:READOUT,
                String.format(Locale.ROOT,"%.1f",body.survival().temperature()));
        metric(g,"xp",253,READOUT,Integer.toString(client.player.experienceLevel));
        ItemStack offhand=client.player.getOffhandItem();
        effects(g,body,client.player.getActiveEffects().stream().toList(),!offhand.isEmpty());
        for(int slot=0;slot<9;slot++)slot(g,client.player.getInventory().getItem(slot),slot,
                client.player.getInventory().getSelectedSlot()==slot,UniHealthHudLayout.slotX(slot),45);
        if(!offhand.isEmpty()) {
            g.pose().pushMatrix();g.pose().translate(290,55);g.pose().scale(.875f,.875f);
            g.item(offhand,0,0);g.pose().popMatrix();
        }
        g.pose().popMatrix();
    }
    private static void pulse(GuiGraphicsExtractor g,BodySnapshot body) {
        int color=body.racingHeart()?AMBER:READOUT;
        icon(g,"pulse",25,4,15,color);
        text(g,Integer.toString(Math.round(body.bpm())),45,3,color,1.5f);
        text(g,"BPM",76,10,DIM,.85f);
    }
    private static String percent(float value) {return Math.round(value*100)+"%";}
    private static void metric(GuiGraphicsExtractor g,String icon,int x,int color,String value) {
        icon(g,icon,x,27,13,color);text(g,value,x+16,30,color,1);
    }
    public static List<Badge> medicalBadges(BodySnapshot b) {
        List<Badge> out=new ArrayList<>();boolean fracture=false,infection=false,bleed=false;
        for(var p:b.parts()) {fracture|=p.fractured();infection|=p.infection()>.25;bleed|=p.bleeding()>0||p.internalBleeding()>0;}
        if(bleed)out.add(new Badge("blood","BLEED",RED,0,-1));
        if(fracture)out.add(new Badge("warning","FRACTURE",RED,0,-1));
        if(infection)out.add(new Badge("effect","INFECTION",0xFFC986D6,0,-1));
        if(b.pain()>.35)out.add(new Badge("warning","PAIN",AMBER,0,-1));
        if(b.oxygen()<.6)out.add(new Badge("oxygen","HYPOXIA",AMBER,0,-1));
        if(b.survival().nausea()>.25)out.add(new Badge("nausea","NAUSEA",0xFFC986D6,0,-1));
        if(b.energy()<.2||b.survival().fatigue()>.7)out.add(new Badge("energy","FATIGUE",AMBER,0,-1));
        if(b.survival().radiationGy()>.1)out.add(new Badge("radiation","RADIATION",AMBER,0,-1));
        if(MedicalPhysiology.mood(b)<-.75)out.add(new Badge("brain","LOW MOOD",0xFFAD97B9,0,-1));
        return List.copyOf(out);
    }
    private static void effects(GuiGraphicsExtractor g,BodySnapshot body,List<MobEffectInstance> mobEffects,boolean offhand) {
        List<Badge> badges=new ArrayList<>(medicalBadges(body));
        mobEffects.stream().filter(MobEffectInstance::showIcon).sorted(Comparator.comparing(MobEffectInstance::getDescriptionId)).forEach(effect->{
            String id=effect.getDescriptionId();String icon=id.contains("speed")?"speed":id.contains("strength")?"strength"
                    :id.contains("resistance")?"shield":id.contains("water_breathing")?"oxygen"
                    :id.contains("hunger")?"food":id.contains("poison")||id.contains("wither")?"nausea":"effect";
            badges.add(new Badge(icon,id,effect.getEffect().value().isBeneficial()?GREEN:0xFFC986D6,
                    effect.getAmplifier()+1,effect.isInfiniteDuration()?-1:(effect.getDuration()+19)/20));
        });
        lastEffectCount=badges.size();
        int limit=offhand?5:6;
        for(int index=0;index<Math.min(limit,badges.size());index++) {
            Badge badge=badges.get(index);int x=index<3?0:290,y=7+(index%3)*24;
            icon(g,"effect_ring",x,y,14,badge.color());icon(g,badge.icon(),x+3,y+3,8,badge.color());
            if(badge.amplifier()>1)text(g,Integer.toString(badge.amplifier()),x+9,y+9,badge.color(),.55f);
        }
        if(badges.size()>limit)text(g,"+"+(badges.size()-limit),290,70,AMBER,.55f);
    }
    private static void slot(GuiGraphicsExtractor g,ItemStack item,int index,boolean selected,int x,int y) {
        // Real ItemStack rendering: no illustrative silhouettes or nine empty square boxes.
        if(selected)g.fill(x,y+22,x+22,y+23,GREEN);
        if(index>=0)text(g,Integer.toString(index+1),x+8,y+25,selected?READOUT:DIM,.75f);
        if(!item.isEmpty()) {
            g.pose().pushMatrix();g.pose().translate(x+1,y);g.pose().scale(1.25f,1.25f);
            g.item(item,0,0);g.pose().popMatrix();
            if(item.getCount()>1)text(g,Integer.toString(item.getCount()),x+14,y+14,READOUT,.75f);
            if(item.isBarVisible()) {
                g.fill(x+4,y+20,x+18,y+21,0xFF101D16);
                g.fill(x+4,y+20,x+4+item.getBarWidth(),y+21,0xFF000000|item.getBarColor());
            }
        }
    }
    public static void icon(GuiGraphicsExtractor g,String name,int x,int y,int size,int color) {
        int source=switch(name) {case "pulse","xp","effect","warning","shield","speed","strength","slot","slot_selected","effect_ring"->32;default->16;};
        g.blit(RenderPipelines.GUI_TEXTURED,MedicalClient.id("textures/gui/uni_health/"+name+".png"),x,y,0f,0f,
                size,size,source,source,source,source,color);
    }
    public static void text(GuiGraphicsExtractor g,String text,int x,int y,int color,float scale) {
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale,scale);
        g.text(Minecraft.getInstance().font,Component.literal(text).withStyle(OSD),0,0,color,false);g.pose().popMatrix();
    }
    private static void backdrop(GuiGraphicsExtractor g) {
        int w=UniHealthHudLayout.WIDTH,h=UniHealthHudLayout.HEIGHT;
        g.blit(RenderPipelines.GUI_TEXTURED,MedicalClient.id("textures/gui/uni_health/compact_backdrop.png"),
                0,0,0f,0f,w,h,w,h,w,h,0xFFFFFFFF);
    }
}
