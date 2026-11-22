package io.github.safedecimal.decayingaftermath.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** CU inertial hand/continuous wrap; fit-thread-tension and tilt-pour are explicit adaptations. */
public final class MedicalProcedureScreen extends Screen {
    private final MedicalInventoryScreen previous;
    private final int slot;
    private final BodyPart part;
    private final TreatmentKind kind;
    private final CuHandMotion hand=new CuHandMotion();
    private final ProcedureCursor cursor=new ProcedureCursor();
    private final BandageWrapMotion wrap=new BandageWrapMotion();
    private final FastenerMotion fasteners=new FastenerMotion();
    private ProcedureBudget budget;
    private boolean started,ended,completed,holding,injecting,sourceExhausted;
    private int elapsed,stage,errorTicks,nextSpray,sprayFrames;
    private float scale=1,remaining=1,initialFill=1,lastFill=1,pendingUse,outstanding;
    private int ox,oy;
    private double pointerX=128,pointerY=225,offsetX,offsetY,objectX=47,objectY=90;
    private double previousObjectX=47,previousObjectY=90,gripLocalX,gripLocalY;
    private double angle,previousAngle,deviceAngle=-18,potionTilt,syringeAngle;
    private float handRotation;
    private boolean syringeInserted;
    private double syringeLockX;
    private float delivered;
    private float toolWork;
    private MedicalLoopSound syringeLoop;
    private static final int SYRINGE_HEIGHT=123;
    private static final double SYRINGE_ENTRY_Y=128+219*.25,SYRINGE_END_Y=128+345*.25;

    public MedicalProcedureScreen(MedicalInventoryScreen previous,int slot,BodyPart part,TreatmentKind kind) {
        super(Component.translatable("decaying_aftermath.procedure.title"));
        this.previous=previous;this.slot=slot;this.part=part;this.kind=kind;
        budget=new ProcedureBudget(0,MedicalSupplyPolicy.ticksPerFill(kind));
        if(kind.injection()) {objectX=116;objectY=128+168*.25-SYRINGE_HEIGHT;}
        if(isWrap()) {objectX=128;objectY=31;hand.outsideRadius(250);}
        previousObjectX=objectX;previousObjectY=objectY;
    }
    @Override protected void init() {
        scale=Math.max(.25f,Math.min(1f,Math.min((width-32f)/280,(height-64f)/286)));
        ox=(int)((width-256*scale)/2);oy=(int)((height-256*scale)/2);
        if(!started) {
            started=true;initialFill=remaining=lastFill=MedicalNetwork.remainingFill(source());
            if(minecraft.player!=null&&!minecraft.player.hasInfiniteMaterials()
                    &&!MedicalNetwork.canSeparateSupply(minecraft.player.inventoryMenu,slot,source())) {
                ThoughtTyper.enqueue(Component.translatable("decaying_aftermath.thought.supply_space").getString());onClose();return;
            }
            ClientMedicalState.beginProcedure(slot,part,kind);
        }
    }
    private ItemStack source() {return minecraft.player==null?ItemStack.EMPTY:minecraft.player.inventoryMenu.getSlot(slot).getItem();}
    private boolean isWrap() {return MedicalSupplyPolicy.wrap(kind);}
    private boolean isPotion() {return kind==TreatmentKind.HEALING_POTION||kind==TreatmentKind.REGENERATION_POTION||kind==TreatmentKind.POTION||kind==TreatmentKind.ANTISEPTIC;}
    private boolean isAntiseptic() {return kind==TreatmentKind.ANTISEPTIC;}
    private boolean draggingTool() {return holding&&(isWrap()||kind.injection()||isPotion()||stage==0);}
    private double pivotX() {return isWrap()?0:kind.injection()?11.5:isPotion()||kind.simpleTool()?16:kind==TreatmentKind.SPLINT?13.5:22.5;}
    private double pivotY() {return isWrap()?0:kind.injection()?SYRINGE_HEIGHT:isPotion()||kind.simpleTool()?16:24.5;}
    private double toolAngle() {return isWrap()?angle*3:kind.injection()?syringeAngle:isPotion()?potionTilt:kind==TreatmentKind.SPLINT?deviceAngle:0;}
    private void captureGrip(double x,double y) {
        offsetX=objectX-x;offsetY=objectY-y;
        var grip=ProcedureToolPose.localGrip(x,y,objectX,objectY,pivotX(),pivotY(),toolAngle());
        gripLocalX=grip.x();gripLocalY=grip.y();
    }
    private int toolWidth() {return kind.injection()?23:isPotion()||kind.simpleTool()?32:kind==TreatmentKind.SPLINT?27:45;}
    private int toolHeight() {return kind.injection()?123:isPotion()||kind.simpleTool()?32:49;}
    private boolean canGrab(double x,double y,double toolX,double toolY,double rollAngle) {
        if(isWrap())return Math.hypot(x-toolX,y-toolY)<=rollSize()/2.0+4;
        if(kind.injection()||isPotion()||stage==0) {
            var local=ProcedureToolPose.localGrip(x,y,toolX,toolY,pivotX(),pivotY(),toolAngle());
            return Math.abs(local.x()+pivotX()-toolWidth()/2.0)<=toolWidth()/2.0+4
                    && Math.abs(local.y()+pivotY()-toolHeight()/2.0)<=toolHeight()/2.0+4;
        }
        if(kind==TreatmentKind.SPLINT||stage==1)
            return Math.abs(x-strapX())<20&&Math.abs(y-strapY())<16;
        return CuProcedureGeometry.windlassGrip(x,y,rollAngle);
    }
    private int strapX() {return (stage%2)==1?96:148;}
    private int strapY() {return stage<3?112:164;}
    private void drawGrip(GuiGraphicsExtractor g,double x,double y,double rollAngle,boolean ready) {
        if(isWrap()) {
            // Point on the roll facing away from the wrap disk, reachable even on a full roll.
            double r=Math.atan2(y-128,x-128);
            double edge=Math.max(0,rollSize()/2.0-5);
            double px=x+Math.cos(r)*edge,py=y+Math.sin(r)*edge;
            // Keep the marker in the scissored surface when the roll starts near its top edge.
            ProcedureVisuals.grip(g,Math.clamp(px,-4,259),Math.clamp(py,20,226),ready);
        } else if(kind.injection()||isPotion()||stage==0) {
            var p=ProcedureToolPose.grip(x,y,pivotX(),pivotY(),toolWidth()/2.0-pivotX(),
                    (kind.injection()?toolHeight()*.36:toolHeight()/2.0)-pivotY(),toolAngle());
            ProcedureVisuals.grip(g,p.x(),p.y(),ready);
        } else if(kind==TreatmentKind.SPLINT||stage==1) {
            ProcedureVisuals.grip(g,strapX(),strapY(),ready);
        } else {
            double a=Math.toRadians(rollAngle);
            for(int side:new int[]{-1,1})ProcedureVisuals.grip(g,128+side*22*Math.cos(a),128+side*22*Math.sin(a),ready);
        }
    }
    private void target(double x,double y) {pointerX=Math.clamp((x-ox)/scale,-3,259);pointerY=Math.clamp((y-oy)/scale,-3,259);}
    @Override public void mouseMoved(double x,double y) {target(x,y);}
    @Override public void tick() {
        elapsed++;if(errorTicks>0)errorTicks--;if(sprayFrames>0)sprayFrames--;
        if(minecraft.player==null||!minecraft.player.isAlive()) {end();return;}
        var mode=ClientMedicalState.snapshot().mode();
        if(mode==BodySnapshot.Mode.UNCONSCIOUS||mode==BodySnapshot.Mode.DEAD) {onClose();return;}
        syncSupply();
        if(sourceExhausted) {if(MedicalSupplyPolicy.installation(kind)&&completed)finish();else onClose();return;}
        previousObjectX=objectX;previousObjectY=objectY;previousAngle=angle;hand.beginTick();
        for(int i=0;i<3&&!ended;i++) {
            hand.step(pointerX,pointerY,isWrap()&&holding,ClientMedicalState.snapshot().pain(),
                    ClientMedicalState.snapshot().consciousness(),ClientMedicalState.snapshot().energy());
            if(isWrap())hand.outsideRadius(250);
            if(holding)interact(CuHandMotion.STEP);
        }
        if(ended)return;
        if(completed&&!minecraft.player.hasInfiniteMaterials())queueUse(Math.max(0,remaining-outstanding-pendingUse));
        flushUse();syncSupply();
        if(completed&&minecraft.player.hasInfiniteMaterials()&&elapsed>=50) {finish();return;}
        if(kind.injection()&&injecting&&syringeInserted&&holding&&remaining>0) {
            float depth=(float)Math.clamp((objectY+SYRINGE_HEIGHT-SYRINGE_ENTRY_Y)/(SYRINGE_END_Y-SYRINGE_ENTRY_Y),0,1);
            if(depth>0) {if(syringeLoop==null)syringeLoop=MedicalLoopSound.start("procedure_syringe_loop",0,1);syringeLoop.set(depth*.35f,1.6f-remaining);}else stopSyringeLoop();
        } else stopSyringeLoop();
        if(completed&&sourceExhausted&&elapsed>=50)finish();
    }
    private void syncSupply() {
        ItemStack stack=source();
        if(stack.isEmpty()||MedicalItems.treatment(stack)!=kind) {remaining=0;sourceExhausted=true;return;}
        if(kind.reusable()) {
            // This is work budget, NOT a disappearing fraction of the metal tool.
            remaining=Math.max(0,initialFill-budget.delivered());outstanding=0;lastFill=remaining;
            if(remaining<=MedicalSupplyPolicy.EMPTY)sourceExhausted=true;return;
        }
        float live=MedicalNetwork.remainingFill(stack);
        // This percentage follows the server item, not a local completion or guessed dose.
        if(live>lastFill+.001f) {remaining=0;sourceExhausted=true;return;}
        outstanding=minecraft.player.hasInfiniteMaterials()?0:Math.max(0,outstanding-Math.max(0,lastFill-live));lastFill=remaining=live;
        if(live<=MedicalSupplyPolicy.EMPTY)sourceExhausted=true;
    }
    private void queueUse(float amount) {pendingUse+=Math.max(0,Math.min(amount,remaining-outstanding-pendingUse));}
    private void flushUse() {
        if(pendingUse<=0)return;
        float granted=budget.grant(Math.max(0,elapsed-4),pendingUse,Math.max(0,remaining-outstanding));
        if(granted<=0)return;
        pendingUse=Math.max(0,pendingUse-granted);outstanding+=granted;
        ClientMedicalState.sendTreatment(slot,part,kind,granted);if(kind.injection())delivered+=granted;
        if(minecraft.player.hasInfiniteMaterials()&&!MedicalSupplyPolicy.installation(kind)&&budget.delivered()>=1-MedicalSupplyPolicy.EMPTY)
            budget=new ProcedureBudget(Math.max(0,elapsed-4),MedicalSupplyPolicy.ticksPerFill(kind));
    }
    private void interact(double seconds) {
        if(isWrap()) {
            int turns=wrap.turns();double rotation=wrap.advance(hand.x(),hand.y());angle=wrap.angle();
            var grip=ProcedureToolPose.grip(0,0,0,0,gripLocalX,gripLocalY,angle*3);
            objectX=hand.x()-grip.x();objectY=hand.y()-grip.y();
            queueUse(MedicalSupplyPolicy.wrapCost(rotation));
            if(wrap.turns()>turns)MedicalClient.sound("procedure_bandage",.65f,1);
        } else if(kind.injection()) {
            double desiredX=Math.clamp(hand.x()+offsetX,40,190),desiredY=Math.clamp(hand.y()+offsetY,10,SYRINGE_END_Y-SYRINGE_HEIGHT);
            boolean entering=desiredY+SYRINGE_HEIGHT>SYRINGE_ENTRY_Y;
            if(entering&&!syringeInserted&&Math.abs(desiredX+11.5-128)>6) {desiredY=SYRINGE_ENTRY_Y-SYRINGE_HEIGHT;errorTicks=10;entering=false;}
            if(entering&&!syringeInserted) {syringeLockX=desiredX;MedicalClient.sound("procedure_syringe",.6f,1);}
            syringeAngle=entering?(desiredX-syringeLockX)*.5:syringeAngle*(1-4*seconds);
            if(entering&&Math.abs(desiredX-syringeLockX)>20) {
                MedicalClient.sound("procedure_syringe_fail",.6f,1);ThoughtTyper.enqueue(Component.translatable("decaying_aftermath.thought.careful").getString());onClose();return;
            }
            if(syringeInserted&&!entering&&delivered>0) {syringeInserted=false;onClose();return;}
            objectX=entering?syringeLockX:desiredX;objectY=desiredY;syringeInserted=entering;
            if(injecting&&entering) {float depth=(float)Math.clamp((objectY+SYRINGE_HEIGHT-SYRINGE_ENTRY_Y)/(SYRINGE_END_Y-SYRINGE_ENTRY_Y),0,1);queueUse((float)(depth*seconds/6));}
        } else if(kind.simpleTool()) {
            objectX=Math.clamp(hand.x()+offsetX,15,210);objectY=Math.clamp(hand.y()+offsetY,15,205);
            var wound=CuProcedureGeometry.part(part,111,80,34,116);
            boolean aligned=objectX+16>=wound.x()-8&&objectX+16<=wound.x()+wound.width()+8
                    &&objectY+16>=wound.y()&&objectY+16<=wound.y()+wound.height();
            if(aligned) {
                float work=(float)(seconds/3);toolWork=Math.min(1,toolWork+work);queueUse(work*initialFill);
                if(toolWork>=1-MedicalSupplyPolicy.EMPTY&&MedicalSupplyPolicy.installation(kind)){completed=true;holding=false;}
            }else errorTicks=8;
        } else if(isPotion()) {
            // Keep the acquired point under the hand when turning the bottle, not just when translating it.
            var grip=ProcedureToolPose.grip(0,0,pivotX(),pivotY(),gripLocalX,gripLocalY,potionTilt);
            objectX=Math.clamp(hand.x()-grip.x(),15,210);objectY=Math.clamp(hand.y()-grip.y(),15,205);
            var mouth=CuProcedureGeometry.bottleMouth(objectX,objectY,potionTilt);
            if(isAntiseptic()) {
                stage=CuProcedureGeometry.contains(CuProcedureGeometry.antisepticZone(part),mouth)?1:0;
            } else {
                float rate=CuProcedureGeometry.pourStrength(potionTilt);
                if(CuProcedureGeometry.overWound(part,mouth)&&rate>0) {stage=1;queueUse((float)(seconds*rate/6));}
                else {stage=0;if(rate>0)errorTicks=8;}
            }
        } else if(stage==0) {
            var grip=ProcedureToolPose.grip(0,0,pivotX(),pivotY(),gripLocalX,gripLocalY,toolAngle());
            objectX=Math.clamp(hand.x()-grip.x(),15,215);objectY=Math.clamp(hand.y()-grip.y(),15,205);
        } else if(kind==TreatmentKind.SPLINT||stage==1) {
            var pull=fasteners.pull(stage,hand.x(),hand.y(),seconds);queueUse(pull.fraction()*initialFill);
            if(pull.offRail())errorTicks=8;
            if(pull.complete()) {
                stage++;holding=false;
                if(kind==TreatmentKind.SPLINT&&stage>=5) {completed=true;MedicalClient.sound("procedure_splint",.6f,1);}
                if(kind==TreatmentKind.TOURNIQUET&&stage==2)wrap.grab(hand.x(),hand.y());
            }
        } else if(kind==TreatmentKind.TOURNIQUET&&stage==2) {
            queueUse((float)(wrap.advance(hand.x(),hand.y())*initialFill*.75/3));angle=wrap.angle();
            if(wrap.turns()>=3) {completed=true;holding=false;}
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        target(event.x(),event.y());
        if(ended)return false;
        if(event.button()==3&&isAntiseptic()&&holding) {
            // Check the current bottle pose, not a stage left over from an earlier grab.
            var nozzle=CuProcedureGeometry.bottleMouth(objectX,objectY,potionTilt);
            if(minecraft.player!=null && !sourceExhausted && elapsed>=nextSpray
                    && CuProcedureGeometry.contains(CuProcedureGeometry.antisepticZone(part),nozzle)) {
                boolean creative=minecraft.player.hasInfiniteMaterials();
                float available=creative?1:remaining;
                float cost=MedicalSupplyPolicy.sprayCost(available);
                float granted=budget.grantSpray(Math.max(0,elapsed-4),cost,available);
                if(granted>0) {
                    ClientMedicalState.sendTreatment(slot,part,kind,granted);
                    nextSpray=elapsed+MedicalSupplyPolicy.SPRAY_COOLDOWN+2;
                    sprayFrames=8;MedicalClient.sound("procedure_spray",.65f,1);
                    if(creative&&budget.delivered()>=1-MedicalSupplyPolicy.EMPTY)
                        budget=new ProcedureBudget(Math.max(0,elapsed-4),MedicalSupplyPolicy.ticksPerFill(kind));
                }
            }
            return true;
        }
        if(event.button()==3&&kind.injection()&&syringeInserted&&holding) {injecting=true;return true;}
        if(event.button()!=1)return false;
        double x=hand.x(),y=hand.y();
        if(completed||!(holding=canGrab(x,y,objectX,objectY,angle)))return false;
        if(draggingTool())captureGrip(x,y);
        if(isWrap()||kind==TreatmentKind.TOURNIQUET&&stage==2)wrap.grab(x,y);
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {target(event.x(),event.y());return holding&&!ended;}
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        target(event.x(),event.y());
        if(event.button()==3) {injecting=false;stopSyringeLoop();return true;}
        if(event.button()!=1)return false;
        if(holding&&stage==0&&MedicalSupplyPolicy.installation(kind)&&!kind.simpleTool()) {
            if(CuProcedureGeometry.deviceAligned(objectX,objectY,deviceAngle,kind==TreatmentKind.SPLINT)) {objectX=116;objectY=105;stage=1;}
            else errorTicks=12;
        }
        holding=false;injecting=false;stopSyringeLoop();return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        target(x,y);
        if(vertical!=0&&(InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)||InputConstants.isKeyDown(InputConstants.KEY_RSHIFT))) {
            handRotation=(float)Math.IEEEremainder(handRotation+Math.toRadians(vertical*12),2*Math.PI);return true;
        }
        if(holding&&kind==TreatmentKind.SPLINT&&stage==0) {deviceAngle=Math.clamp(deviceAngle+vertical*3,-45,45);return true;}
        if(holding&&isPotion()&&!isAntiseptic()) {potionTilt=Math.clamp(potionTilt+vertical*8,0,110);return true;}
        return false;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        if(!ended)cursor.hide(minecraft.getWindow().handle());
        double toolX=ProcedureToolPose.interpolate(previousObjectX,objectX,partialTick),toolY=ProcedureToolPose.interpolate(previousObjectY,objectY,partialTick);
        double handX=hand.visualX(partialTick),handY=hand.visualY(partialTick);
        double visualAngle=previousAngle+(((angle-previousAngle+540)%360)-180)*Math.clamp(partialTick,0,1);
        if(draggingTool()&&isWrap()) {
            // The rotating roll follows the constrained physical hand, never the other way round.
            // Linear interpolation of roll positions or a rotating off-center grip cuts through the disk.
            var grip=ProcedureToolPose.grip(0,0,0,0,gripLocalX,gripLocalY,visualAngle*3);
            toolX=handX-grip.x();toolY=handY-grip.y();
        } else if(draggingTool()) {
            var grip=ProcedureToolPose.grip(toolX,toolY,pivotX(),pivotY(),gripLocalX,gripLocalY,toolAngle());
            handX=grip.x();handY=grip.y();
        }
        extractTransparentBackground(graphics);MedicalPanelRenderer.extract(graphics,MedicalPanelLayout.forScreen(width,height));
        graphics.pose().pushMatrix();graphics.pose().translate(ox,oy);graphics.pose().scale(scale,scale);ProcedureVisuals.frame(graphics);
        graphics.centeredText(font,Component.translatable(part.translationKey()).getString(),128,-1,0xFFDDE3DE);
        if(isWrap()) {
            MedicalClient.blit(graphics,"bandage_inner",65,65,126,126,164,164,0xFFFFFFFF);
            int diameter=Math.round(150*wrap.sizeMultiplier());
            MedicalClient.radialTexture(graphics,"bandage",128-diameter/2,128-diameter/2,diameter,200,wrap.coverage(),0xFFFFFFFF);
            double radians=Math.toRadians(angle);
            graphics.pose().pushMatrix();graphics.pose().translate(128,128);graphics.pose().rotate((float)radians);
            MedicalClient.blit(graphics,"bandage_pointer",-101,-101,202,202,300,300,0xFFFFFFFF);graphics.pose().popMatrix();
            if(!holding)drawBandageRoll(graphics,toolX,toolY,visualAngle);
            MedicalStatusIcons.draw(graphics,MedicalStatusIcons.bleeding(ClientMedicalState.snapshot().part(part)),128,128,32,MedicalStatusIcons.bleedingColor(ClientMedicalState.snapshot().part(part)));
        } else if(kind.injection()) {
            MedicalClient.blit(graphics,"syringe_ground",-22,(int)Math.round(SYRINGE_ENTRY_Y),300,44,200,29,0xFFFFFFFF);
            CuMedicalHand.line(graphics,122,(int)SYRINGE_ENTRY_Y,134,(int)SYRINGE_ENTRY_Y);
            if(!holding)drawSyringe(graphics,toolX,toolY);
        } else if(isPotion()) {
            drawSelectedPart(graphics,111,80,34,116);
            if(isAntiseptic()) {
                var zone=CuProcedureGeometry.antisepticZone(part);
                graphics.fill(zone.x(),zone.y(),zone.x()+zone.width(),zone.y()+zone.height(),0x242ED5C7);
                graphics.outline(zone.x(),zone.y(),zone.width(),zone.height(),holding&&stage==1?0xFF8EF2BC:0xFFA4CCC6);
                var wound=CuProcedureGeometry.part(part,111,80,34,116);
                // Render the server's protection status even before returning to the inventory.
                var state=ClientMedicalState.snapshot().part(part);
                MedicalStatusIcons.draw(graphics,MedicalStatusIcons.disinfection(state),
                        wound.x()+wound.width()+17,wound.y()+wound.height()/2,23,
                        MedicalStatusIcons.disinfectionColor(state));
            }
        } else if(kind.simpleTool()) {
            drawSelectedPart(graphics,111,80,34,116);
            if(!holding)drawSimpleTool(graphics,toolX,toolY);
            var wound=CuProcedureGeometry.part(part,111,80,34,116);
            graphics.fill(wound.x()-3,wound.y(),wound.x()-1,wound.y()+wound.height(),0xFFBDD1C0);
        } else {
            drawSelectedPart(graphics,117,75,27,116);
            if(stage==0) {
                int toolWidth=kind==TreatmentKind.SPLINT?27:45;
                if(!holding)drawDevice(graphics,toolX,toolY,toolWidth);
                int cue=errorTicks>0?0xFFF08888:CuProcedureGeometry.deviceAligned(objectX,objectY,deviceAngle,kind==TreatmentKind.SPLINT)?0xFFBDD1C0:0xFF707A73;
                graphics.fill(114,103,116,113,cue);graphics.fill(114,103,124,105,cue);
                graphics.fill(116+toolWidth+1,146,116+toolWidth+3,156,cue);graphics.fill(116+toolWidth-7,154,116+toolWidth+3,156,cue);
            } else {
                MedicalClient.blit(graphics,kind==TreatmentKind.SPLINT?"splint":"tourniquet",116,104,kind==TreatmentKind.SPLINT?27:45,49,kind==TreatmentKind.SPLINT?16:36,kind==TreatmentKind.SPLINT?29:39,0xFFFFFFFF);
                if(kind==TreatmentKind.SPLINT) {ProcedureVisuals.rail(graphics,112,fasteners.progress(1),fasteners.progress(2),stage<=2);ProcedureVisuals.rail(graphics,164,fasteners.progress(3),fasteners.progress(4),stage>2);}
                else {ProcedureVisuals.rail(graphics,112,fasteners.progress(1),0,stage==1);if(stage>=2) {graphics.pose().pushMatrix();graphics.pose().translate(128,128);graphics.pose().rotate((float)Math.toRadians(angle));CuMedicalHand.cube(graphics,-23,-3,46,6,3);CuMedicalHand.cube(graphics,-26,-6,7,12,2);CuMedicalHand.cube(graphics,19,-6,7,12,2);graphics.pose().popMatrix();}}
            }
        }
        ProcedureVisuals.beginWorkArea(graphics);
        float handAngle=handRotation+hand.rotation(isWrap())+(kind.injection()?(float)Math.toRadians(65):0);
        CuMedicalHand.extract(graphics,handX,handY,handAngle,holding||injecting);
        // Held items stay readable in front of the translucent arm, at the same frame-time grip.
        if(draggingTool()) {
            if(isWrap())drawBandageRoll(graphics,toolX,toolY,visualAngle);
            else if(kind.injection())drawSyringe(graphics,toolX,toolY);
            else if(kind.simpleTool())drawSimpleTool(graphics,toolX,toolY);
            else if(!isPotion())drawDevice(graphics,toolX,toolY,kind==TreatmentKind.SPLINT?27:45);
        }
        if(isPotion())drawPotion(graphics,toolX,toolY);
        if(!holding&&!completed)drawGrip(graphics,toolX,toolY,visualAngle,canGrab(hand.x(),hand.y(),objectX,objectY,angle));
        graphics.disableScissor();
        Component supplyLabel=kind.reusable()?Component.translatable("decaying_aftermath.procedure.reusable")
                :Component.translatable("decaying_aftermath.procedure.remaining",(int)Math.ceil(remaining*100-.00001f));
        graphics.centeredText(font,supplyLabel.getString(),128,244,0xFFE4E9E4);
        float bar=kind.reusable()?(source().getMaxDamage()-source().getDamageValue())/(float)Math.max(1,source().getMaxDamage()):remaining;
        graphics.fill(89,259,167,261,0xFF414A43);graphics.fill(89,259,89+Math.round(78*bar),261,0xFFC3CFC4);
        if(errorTicks>0)graphics.centeredText(font,Component.translatable("decaying_aftermath.procedure.guide.steady").getString(),128,222,0xFFF08888);
        graphics.pose().popMatrix();
        var lines=ProcedureVisuals.lines(guide()+" "+Component.translatable("decaying_aftermath.procedure.rotate_hand").getString(),280,font::width);
        graphics.pose().pushMatrix();graphics.pose().translate(ox,oy);graphics.pose().scale(scale,scale);
        int gy=-lines.size()*11-17;for(String line:lines) {graphics.centeredText(font,line,128,gy,0xFFE4E9E4);gy+=11;}
        graphics.pose().popMatrix();
        graphics.centeredText(font,Component.translatable("decaying_aftermath.procedure.cancel").getString(),width/2,Math.min(height-12,(int)(oy+272*scale+6)),0xFFB8C0BA);
        // Medical foreground is extracted once at the end of Gui.extractRenderState.
        MedicalPanelRenderer.extractTooltip(graphics,MedicalPanelLayout.forScreen(width,height),mouseX,mouseY);
    }
    private int rollSize() {double scale=1-(1-remaining)*(1-remaining);return Math.max(1,(int)(78*scale));}
    private void drawBandageRoll(GuiGraphicsExtractor graphics,double x,double y,double visualAngle) {
        int size=rollSize();graphics.pose().pushMatrix();graphics.pose().translate((float)x,(float)y);graphics.pose().rotate((float)Math.toRadians(visualAngle*3));
        MedicalClient.blit(graphics,"bandage_roll",-size/2,-size/2,size,size,104,104,0xFFFFFFFF);graphics.pose().popMatrix();
    }
    private void drawDevice(GuiGraphicsExtractor graphics,double x,double y,int width) {
        graphics.pose().pushMatrix();graphics.pose().translate((float)x+width/2f,(float)y+24.5f);graphics.pose().rotate((float)Math.toRadians(kind==TreatmentKind.SPLINT?deviceAngle:0));
        MedicalClient.blit(graphics,kind==TreatmentKind.SPLINT?"splint":"tourniquet",-width/2,-24,width,49,kind==TreatmentKind.SPLINT?16:36,kind==TreatmentKind.SPLINT?29:39,0xFFFFFFFF);graphics.pose().popMatrix();
    }
    private void drawSyringe(GuiGraphicsExtractor graphics,double x,double y) {
        graphics.pose().pushMatrix();graphics.pose().translate((float)x+11.5f,(float)y+SYRINGE_HEIGHT);graphics.pose().rotate((float)Math.toRadians(syringeAngle));
        MedicalClient.blit(graphics,"syringe",-12,-SYRINGE_HEIGHT,23,123,30,164,0xFFFFFFFF);
        int fillHeight=Math.round(59*remaining);
        if(fillHeight>0) {
            int sourceHeight=Math.max(1,Math.round(78*remaining));
            graphics.blit(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("decaying_aftermath","textures/gui/procedures/syringe_fill.png"),
                    -9,-40-fillHeight,0f,78-sourceHeight,15,fillHeight,20,sourceHeight,20,78,syringeColor());
        }
        graphics.fill(-15,-SYRINGE_HEIGHT+Math.round((1-remaining)*20)-1,14,-SYRINGE_HEIGHT+Math.round((1-remaining)*20)+1,0xFFEAEAEA);graphics.pose().popMatrix();
    }
    private int syringeColor(){return switch(kind){case ANESTHETIC->0xFF9B58C9;case BLOOD_BAG->0xFFB43A35;
        case SALINE->0xFFB7DAE3;case NALOXONE->0xFFDCB559;case COMBAT_STIMULANT->0xFFE59A45;default->0xFF0084AD;};}
    private void drawSimpleTool(GuiGraphicsExtractor graphics,double x,double y) {
        graphics.pose().pushMatrix();graphics.pose().translate((float)x+16,(float)y+16);graphics.pose().scale(2,2);
        graphics.item(source(),-8,-8);graphics.pose().popMatrix();
    }
    private void drawPotion(GuiGraphicsExtractor graphics,double x,double y) {
        graphics.pose().pushMatrix();graphics.pose().translate((float)x+16,(float)y+16);graphics.pose().rotate((float)Math.toRadians(potionTilt));graphics.pose().scale(2,2);graphics.item(source(),-8,-8);graphics.pose().popMatrix();
        if(holding&&(isAntiseptic()?sprayFrames>0:stage==1)) {
            var mouth=CuProcedureGeometry.bottleMouth(x,y,potionTilt);var wound=CuProcedureGeometry.part(part,111,80,34,116);
            int bottom=isAntiseptic()?Math.max((int)mouth.y()+10,wound.y()+9):wound.y()+wound.height()-4;
            int span=Math.max(1,bottom-(int)mouth.y()-3);
            for(int i=0;i<5;i++) {int dy=(int)mouth.y()+3+(elapsed*3+i*7)%span;int dx=isAntiseptic()?(i-2)*Math.min(4,Math.max(1,(dy-(int)mouth.y())/5)):0;graphics.fill((int)mouth.x()+dx-1,dy,(int)mouth.x()+dx+2,Math.min(dy+3,bottom),isAntiseptic()?0xFF83E5D6:0xFFB0BDB3);}
        }
    }
    private void drawSelectedPart(GuiGraphicsExtractor graphics,int x,int y,int w,int h) {ProcedureVisuals.part(graphics,part,x,y,w,h);}
    private String guide() {
        String suffix=isWrap()?"wrap":kind.injection()?"syringe":isAntiseptic()?"antiseptic":isPotion()?"potion":kind.simpleTool()?"tool":kind==TreatmentKind.SPLINT?(stage==0?"splint_align":"splint_straps"):(stage==0?"tourniquet_fit":"tourniquet_tighten");
        return Component.translatable("decaying_aftermath.procedure.guide."+suffix).getString();
    }
    private void finish() {
        if(ended)return;
        if(MedicalSupplyPolicy.installation(kind))ClientMedicalState.finishProcedure(slot,part,kind);
        end();minecraft.gui.setScreen(previous);
    }
    private void stopSyringeLoop() {if(syringeLoop!=null){syringeLoop.close();syringeLoop=null;}}
    private void end() {stopSyringeLoop();cursor.restore();if(!ended){ended=true;ClientMedicalState.endProcedure();}}
    @Override public void onClose() {end();if(minecraft.player!=null)minecraft.gui.setScreen(previous);}
    @Override public void removed() {end();super.removed();}
    @Override public boolean isPauseScreen() {return false;}
}
