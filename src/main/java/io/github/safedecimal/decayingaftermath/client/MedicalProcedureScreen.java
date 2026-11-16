package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Interactive procedures. CU wrap/injection mechanics, adapted fit/fasten mechanics for its direct-use items. */
public final class MedicalProcedureScreen extends Screen {
    private final MedicalInventoryScreen previous;
    private final int slot;
    private final BodyPart part;
    private final TreatmentKind kind;
    private boolean started,ended,completed;
    private int elapsed;
    private float scale=1;
    private int ox,oy;
    private boolean holding;
    private double pointerX,pointerY,offsetX,offsetY;
    private double objectX=47,objectY=90;
    private double angle,previousAngle;
    private int stage;
    private double strapStart;
    private boolean syringeInserted;
    private double syringeLockX;
    private float remaining=1,delivered,pourProgress;
    private int soundTicks;

    public MedicalProcedureScreen(MedicalInventoryScreen previous,int slot,BodyPart part,TreatmentKind kind) {
        super(Component.literal("Medical procedure"));
        this.previous=previous;this.slot=slot;this.part=part;this.kind=kind;
        if(kind==TreatmentKind.FENTANYL) {objectX=116;objectY=25;}
    }

    @Override protected void init() {
        scale=Math.max(0.25f,Math.min(1f,Math.min((width-24f)/256,(height-32f)/256)));
        ox=(int)((width-256*scale)/2);oy=(int)((height-256*scale)/2);
        if(!started) {
            started=true;
            if(kind==TreatmentKind.FENTANYL&&minecraft.player!=null)
                remaining=MedicalNetwork.remainingFill(minecraft.player.inventoryMenu.getSlot(slot).getItem());
            ClientMedicalState.beginProcedure(slot,part,kind);
        }
    }

    @Override public void tick() {
        elapsed++;
        if(minecraft.player==null||!minecraft.player.isAlive()) {end();return;}
        BodySnapshot.Mode mode=ClientMedicalState.snapshot().mode();
        if(mode==BodySnapshot.Mode.UNCONSCIOUS||mode==BodySnapshot.Mode.DEAD) {onClose();return;}
        if(kind==TreatmentKind.FENTANYL&&holding&&syringeInserted&&remaining>0) {
            float depth=(float)Math.clamp((objectY+123-175)/55,0,1);
            float amount=Math.min(remaining,depth/120f);
            if(amount>0) {
                ClientMedicalState.sendTreatment(slot,part,kind,amount);
                remaining=Math.max(0,remaining-amount);delivered+=amount;
                if(++soundTicks>=10) {MedicalClient.sound("procedure_syringe_loop",0.35f,1f);soundTicks=0;}
            }
        }
        if(isPotion()&&holding&&stage==1&&pointerX>=90&&pointerX<=170&&pointerY>=85&&pointerY<=185) {
            pourProgress=Math.min(1,pourProgress+1f/40);
            if(pourProgress>=1&&elapsed>=50) finish();
        }
        if(kind!=TreatmentKind.FENTANYL&&!isPotion()&&completed&&elapsed>=50) finish();
    }

    private boolean isWrap() {return kind==TreatmentKind.BANDAGE||kind==TreatmentKind.PRESSURE_DRESSING;}
    private boolean isPotion() {return kind==TreatmentKind.HEALING_POTION||kind==TreatmentKind.REGENERATION_POTION||kind==TreatmentKind.POTION;}

    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        extractTransparentBackground(graphics);
        MedicalPanelRenderer.extract(graphics,MedicalPanelLayout.topLeft());
        graphics.pose().pushMatrix();graphics.pose().translate(ox,oy);graphics.pose().scale(scale,scale);
        if(isWrap()) {
            MedicalClient.blit(graphics,"bandage_inner",65,65,126,126,164,164,0xFFFFFFFF);
            MedicalClient.radialTexture(graphics,"bandage",32,32,192,192,
                    (float)Math.min(angle/360,1),0xFFFFFFFF);
            double radians=Math.toRadians(angle);
            int x=128+(int)(Math.sin(radians)*91),y=128-(int)(Math.cos(radians)*91);
            MedicalClient.blit(graphics,"bandage_roll",x-18,y-18,36,36,104,104,0xFFFFFFFF);
            graphics.centeredText(font,Math.round(Math.min(1,angle/360)*100)+"%",128,122,0xFFFFFFFF);
        } else if(kind==TreatmentKind.FENTANYL) {
            MedicalClient.blit(graphics,"syringe_ground",28,175,200,29,200,29,0xFFFFFFFF);
            MedicalClient.blit(graphics,"syringe",(int)objectX,(int)objectY,23,123,30,164,0xFFFFFFFF);
            // Original fill pixels, clipped to the actual amount left in this syringe.
            int fillHeight=Math.round(59*remaining);
            if(fillHeight>0) {
                graphics.enableScissor(ox+(int)((objectX+3)*scale),oy+(int)((objectY+20+59-fillHeight)*scale),
                        ox+(int)((objectX+18)*scale),oy+(int)((objectY+79)*scale));
                MedicalClient.blit(graphics,"syringe_fill",(int)objectX+3,(int)objectY+20,15,59,20,78,0xFF63C9D6);
                graphics.disableScissor();
            }
            graphics.centeredText(font,"Remaining "+Math.round(remaining*100)+"%",128,220,0xFFFFFFFF);
        } else if(isPotion()) {
            drawSelectedPart(graphics,115,86,34,106);
            ItemStack bottle=minecraft.player==null?ItemStack.EMPTY:minecraft.player.inventoryMenu.getSlot(slot).getItem();
            graphics.pose().pushMatrix();graphics.pose().translate((float)objectX,(float)objectY);graphics.pose().scale(2,2);
            graphics.item(bottle,0,0);graphics.pose().popMatrix();
            graphics.centeredText(font,"Applied "+Math.round(pourProgress*100)+"%",128,210,0xFFFFFFFF);
        } else {
            drawSelectedPart(graphics,117,75,27,116);
            String texture=kind==TreatmentKind.SPLINT?"splint":"tourniquet";
            int sw=kind==TreatmentKind.SPLINT?16:36,sh=kind==TreatmentKind.SPLINT?29:39;
            MedicalClient.blit(graphics,texture,(int)objectX,(int)objectY,27,49,sw,sh,0xFFFFFFFF);
            if(stage>0&&kind==TreatmentKind.SPLINT) {
                MedicalClient.blit(graphics,"bandage_pointer",94,104,54,25,54,25,stage>=2?0xFF2BD881:0xFFFFFFFF);
                MedicalClient.blit(graphics,"bandage_pointer",94,153,54,25,54,25,stage>=3?0xFF2BD881:0xFFFFFFFF);
            }
            if(kind==TreatmentKind.TOURNIQUET&&stage>0)
                graphics.centeredText(font,Math.round(Math.min(1,angle/360)*100)+"%",128,205,0xFFFFFFFF);
        }
        graphics.pose().popMatrix();
        graphics.centeredText(font,guide(),width/2,Math.max(5,oy-12),0xFFFFFFFF);
        graphics.centeredText(font,"Esc: cancel; already injected amount is not returned",width/2,
                Math.min(height-12,(int)(oy+256*scale+6)),0xFFD0D0D0);
    }

    private void drawSelectedPart(GuiGraphicsExtractor graphics,int x,int y,int w,int h) {
        String[] names={"head","torso","left_arm","right_arm","left_leg","right_leg"};
        int[][] sizes={{46,44},{40,65},{99,27},{99,27},{31,105},{31,105}};
        int[] size=sizes[part.ordinal()];
        // Preserve each source part's aspect ratio inside the available skin area.
        float ratio=Math.min(w/(float)size[0],h/(float)size[1]);
        int dw=Math.max(1,Math.round(size[0]*ratio)),dh=Math.max(1,Math.round(size[1]*ratio));
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                MedicalClient.id("textures/gui/body/"+names[part.ordinal()]+".png"),x+(w-dw)/2,y+(h-dh)/2,
                0f,0f,dw,dh,size[0],size[1],size[0],size[1],0xFFE7C5AD);
    }

    private String guide() {
        if(isWrap()) return "Grab the roll and wrap clockwise around the limb";
        if(kind==TreatmentKind.FENTANYL) return "Drag syringe down, inject, then pull it out";
        if(isPotion()) return "Drag potion onto the body and hold to apply it";
        if(kind==TreatmentKind.SPLINT) return stage==0?"Align the splint, then pull both straps":"Pull each fastening strap across the limb";
        return stage==0?"Fit the tourniquet onto the limb":"Hold and turn clockwise to tighten";
    }

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(event.button()!=0||ended) return false;
        pointerX=(event.x()-ox)/scale;pointerY=(event.y()-oy)/scale;
        if(isWrap()) {
            double a=polar(pointerX,pointerY),difference=normalize(a-angle);
            double radius=Math.hypot(pointerX-128,pointerY-128);
            holding=radius>=65&&radius<=119&&Math.abs(difference)<26;
            previousAngle=a;return holding;
        }
        if(kind==TreatmentKind.FENTANYL||isPotion()||stage==0) {
            int w=kind==TreatmentKind.FENTANYL?23:32,h=kind==TreatmentKind.FENTANYL?123:49;
            holding=pointerX>=objectX-4&&pointerX<=objectX+w+4&&pointerY>=objectY-4&&pointerY<=objectY+h+4;
            offsetX=objectX-pointerX;offsetY=objectY-pointerY;return holding;
        }
        if(kind==TreatmentKind.SPLINT) {
            int targetY=stage==1?104:153;
            holding=pointerX>=88&&pointerX<=150&&pointerY>=targetY&&pointerY<=targetY+25;
            strapStart=pointerX;return holding;
        }
        holding=Math.hypot(pointerX-128,pointerY-130)<76;
        previousAngle=polar(pointerX,pointerY);return holding;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        pointerX=(event.x()-ox)/scale;pointerY=(event.y()-oy)/scale;
        if(!holding||ended) return false;
        if(isWrap()||kind==TreatmentKind.TOURNIQUET&&stage>0) {
            double next=polar(pointerX,pointerY),delta=normalize(next-previousAngle);
            // No teleporting the pointer over a wrap: CU uses small angular advances.
            if(Math.abs(delta)<=45) angle=Math.max(0,angle+delta);
            previousAngle=next;
            if(angle>=360) {
                completed=true;holding=false;
                MedicalClient.sound(isWrap()?"procedure_bandage":"procedure_tourniquet",0.65f,1f);
                if(elapsed>=50) finish();
            }
        } else if(kind==TreatmentKind.FENTANYL) {
            double desiredX=Math.clamp(pointerX+offsetX,40,190);
            objectY=Math.clamp(pointerY+offsetY,10,125);
            boolean inserted=objectY+123>=175;
            if(inserted&&!syringeInserted) {
                syringeLockX=desiredX;MedicalClient.sound("procedure_syringe",0.6f,1f);
            }
            if(inserted) {
                if(Math.abs(desiredX-syringeLockX)>24) {
                    ThoughtTyper.enqueue("Careful...");holding=false;onClose();return true;
                }
                objectX=syringeLockX;
            } else objectX=desiredX;
            if(syringeInserted&&!inserted&&delivered>0) {syringeInserted=false;onClose();return true;}
            syringeInserted=inserted;
        } else if(isPotion()) {
            objectX=Math.clamp(pointerX+offsetX,15,210);objectY=Math.clamp(pointerY+offsetY,15,200);
            if(pointerX>=90&&pointerX<=170&&pointerY>=85&&pointerY<=185) stage=1;
        } else if(stage==0) {
            objectX=Math.clamp(pointerX+offsetX,15,215);objectY=Math.clamp(pointerY+offsetY,15,205);
        } else if(kind==TreatmentKind.SPLINT&&pointerX-strapStart>=35) {
            stage++;holding=false;MedicalClient.sound("procedure_splint",0.6f,1f);
            if(stage>=3) {completed=true;if(elapsed>=50) finish();}
        }
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if(event.button()!=0) return false;
        if(holding&&stage==0&&(kind==TreatmentKind.SPLINT||kind==TreatmentKind.TOURNIQUET)
                &&Math.abs(objectX-116)<35&&Math.abs(objectY-105)<50) {
            objectX=116;objectY=105;stage=1;
        }
        holding=false;return true;
    }

    private static double polar(double x,double y) {return (Math.toDegrees(Math.atan2(x-128,128-y))+360)%360;}
    private static double normalize(double angle) {return (angle+540)%360-180;}

    private void finish() {
        if(ended) return;
        if(kind!=TreatmentKind.FENTANYL) ClientMedicalState.sendTreatment(slot,part,kind,1f);
        end();minecraft.gui.setScreen(previous);
    }
    private void end() {if(!ended) {ended=true;ClientMedicalState.endProcedure();}}
    @Override public void onClose() {end();if(minecraft.player!=null) minecraft.gui.setScreen(previous);}
    @Override public void removed() {end();super.removed();}
    @Override public boolean isPauseScreen() {return false;}
}
