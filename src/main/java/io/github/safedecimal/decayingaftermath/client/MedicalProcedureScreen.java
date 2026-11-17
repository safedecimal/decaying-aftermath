package io.github.safedecimal.decayingaftermath.client;

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
    private final BandageWrapMotion wrap=new BandageWrapMotion();
    private final FastenerMotion fasteners=new FastenerMotion();
    private ProcedureBudget budget;
    private boolean started,ended,completed,holding,injecting,sourceExhausted;
    private int elapsed,stage,errorTicks;
    private float scale=1,remaining=1,initialFill=1,lastFill=1,pendingUse,outstanding;
    private int ox,oy;
    private double pointerX=128,pointerY=225,offsetX,offsetY,objectX=47,objectY=90;
    private double angle,deviceAngle=-18,potionTilt,syringeAngle;
    private boolean syringeInserted;
    private double syringeLockX;
    private float delivered;
    private MedicalLoopSound syringeLoop;
    private static final int SYRINGE_HEIGHT=123;
    private static final double SYRINGE_ENTRY_Y=128+219*.25,SYRINGE_END_Y=128+345*.25;

    public MedicalProcedureScreen(MedicalInventoryScreen previous,int slot,BodyPart part,TreatmentKind kind) {
        super(Component.translatable("decaying_aftermath.procedure.title"));
        this.previous=previous;this.slot=slot;this.part=part;this.kind=kind;
        budget=new ProcedureBudget(0,MedicalSupplyPolicy.ticksPerFill(kind));
        if(kind==TreatmentKind.FENTANYL) {objectX=116;objectY=128+168*.25-SYRINGE_HEIGHT;}
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
    private boolean isPotion() {return kind==TreatmentKind.HEALING_POTION||kind==TreatmentKind.REGENERATION_POTION||kind==TreatmentKind.POTION;}
    private void target(double x,double y) {pointerX=Math.clamp((x-ox)/scale,-3,259);pointerY=Math.clamp((y-oy)/scale,-3,259);}
    @Override public void mouseMoved(double x,double y) {target(x,y);}
    @Override public void tick() {
        elapsed++;if(errorTicks>0)errorTicks--;
        if(minecraft.player==null||!minecraft.player.isAlive()) {end();return;}
        var mode=ClientMedicalState.snapshot().mode();
        if(mode==BodySnapshot.Mode.UNCONSCIOUS||mode==BodySnapshot.Mode.DEAD) {onClose();return;}
        syncSupply();
        if(sourceExhausted) {if(MedicalSupplyPolicy.installation(kind)&&completed)finish();else onClose();return;}
        hand.beginTick();
        for(int i=0;i<3&&!ended;i++) {
            hand.step(pointerX,pointerY,isWrap()&&holding,ClientMedicalState.snapshot().pain());
            if(isWrap())hand.outsideRadius(250);
            if(holding)interact(CuHandMotion.STEP);
        }
        if(ended)return;
        if(completed&&!minecraft.player.hasInfiniteMaterials())queueUse(Math.max(0,remaining-outstanding-pendingUse));
        flushUse();syncSupply();
        if(completed&&minecraft.player.hasInfiniteMaterials()&&elapsed>=50) {finish();return;}
        if(kind==TreatmentKind.FENTANYL&&injecting&&syringeInserted&&holding&&remaining>0) {
            float depth=(float)Math.clamp((objectY+SYRINGE_HEIGHT-SYRINGE_ENTRY_Y)/(SYRINGE_END_Y-SYRINGE_ENTRY_Y),0,1);
            if(depth>0) {if(syringeLoop==null)syringeLoop=MedicalLoopSound.start("procedure_syringe_loop",0,1);syringeLoop.set(depth*.35f,1.6f-remaining);}else stopSyringeLoop();
        } else stopSyringeLoop();
        if(completed&&sourceExhausted&&elapsed>=50)finish();
    }
    private void syncSupply() {
        ItemStack stack=source();
        if(stack.isEmpty()||MedicalItems.treatment(stack)!=kind) {remaining=0;sourceExhausted=true;return;}
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
        ClientMedicalState.sendTreatment(slot,part,kind,granted);if(kind==TreatmentKind.FENTANYL)delivered+=granted;
        if(minecraft.player.hasInfiniteMaterials()&&!MedicalSupplyPolicy.installation(kind)&&budget.delivered()>=1-MedicalSupplyPolicy.EMPTY)
            budget=new ProcedureBudget(Math.max(0,elapsed-4),MedicalSupplyPolicy.ticksPerFill(kind));
    }
    private void interact(double seconds) {
        if(isWrap()) {
            int turns=wrap.turns();double rotation=wrap.advance(hand.x(),hand.y());angle=wrap.angle();
            queueUse(MedicalSupplyPolicy.wrapCost(rotation));
            if(wrap.turns()>turns)MedicalClient.sound("procedure_bandage",.65f,1);
        } else if(kind==TreatmentKind.FENTANYL) {
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
        } else if(isPotion()) {
            objectX=Math.clamp(hand.x()+offsetX,15,210);objectY=Math.clamp(hand.y()+offsetY,15,205);
            boolean aimed=CuProcedureGeometry.overWound(part,CuProcedureGeometry.bottleMouth(objectX,objectY,potionTilt));
            float rate=CuProcedureGeometry.pourStrength(potionTilt);
            if(aimed&&rate>0) {stage=1;queueUse((float)(seconds*rate/6));}else {stage=0;if(holding&&potionTilt>=35)errorTicks=8;}
        } else if(stage==0) {
            objectX=Math.clamp(hand.x()+offsetX,15,215);objectY=Math.clamp(hand.y()+offsetY,15,205);
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
        if(event.button()==3&&kind==TreatmentKind.FENTANYL&&syringeInserted&&holding) {injecting=true;return true;}
        if(event.button()!=1)return false;
        double x=hand.x(),y=hand.y();
        if(isWrap()) {
            double radius=Math.hypot(x-128,y-128),difference=BandageWrapMotion.normalize(BandageWrapMotion.polar(x,y)-angle);
            holding=radius>=63&&radius<=116&&Math.abs(difference)<26;wrap.grab(x,y);return holding;
        }
        if(kind==TreatmentKind.FENTANYL||isPotion()||stage==0) {
            int w=kind==TreatmentKind.FENTANYL?23:isPotion()?32:kind==TreatmentKind.SPLINT?27:45;
            int h=kind==TreatmentKind.FENTANYL?123:isPotion()?32:49;
            holding=CuProcedureGeometry.toolGrip(x,y,objectX,objectY,w,h,isPotion()?potionTilt:kind==TreatmentKind.SPLINT?deviceAngle:0,4);
            offsetX=objectX-x;offsetY=objectY-y;return holding;
        }
        if(kind==TreatmentKind.SPLINT||stage==1) {
            int row=stage<3?112:164,start=(stage%2)==1?96:148;
            holding=Math.abs(x-start)<20&&Math.abs(y-row)<16;return holding;
        }
        holding=CuProcedureGeometry.windlassGrip(x,y,angle);wrap.grab(x,y);return holding;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {target(event.x(),event.y());return holding&&!ended;}
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        target(event.x(),event.y());
        if(event.button()==3) {injecting=false;stopSyringeLoop();return true;}
        if(event.button()!=1)return false;
        if(holding&&stage==0&&MedicalSupplyPolicy.installation(kind)) {
            if(CuProcedureGeometry.deviceAligned(objectX,objectY,deviceAngle,kind==TreatmentKind.SPLINT)) {objectX=116;objectY=105;stage=1;}
            else errorTicks=12;
        }
        holding=false;injecting=false;stopSyringeLoop();return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        target(x,y);
        if(holding&&kind==TreatmentKind.SPLINT&&stage==0) {deviceAngle=Math.clamp(deviceAngle+vertical*3,-45,45);return true;}
        if(holding&&isPotion()) {potionTilt=Math.clamp(potionTilt+vertical*8,0,110);return true;}
        return false;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        extractTransparentBackground(graphics);MedicalPanelRenderer.extract(graphics,MedicalPanelLayout.topLeft());
        graphics.pose().pushMatrix();graphics.pose().translate(ox,oy);graphics.pose().scale(scale,scale);ProcedureVisuals.frame(graphics);
        graphics.centeredText(font,Component.translatable(part.translationKey()).getString(),128,-1,0xFFDDE3DE);
        if(isWrap()) {
            MedicalClient.blit(graphics,"bandage_inner",65,65,126,126,164,164,0xFFFFFFFF);
            int diameter=Math.round(150*wrap.sizeMultiplier());
            MedicalClient.radialTexture(graphics,"bandage",128-diameter/2,128-diameter/2,diameter,200,wrap.coverage(),0xFFFFFFFF);
            double radians=Math.toRadians(angle),rollScale=1-(1-remaining)*(1-remaining),radius=(253+137*rollScale)*.25;
            int x=128+(int)(Math.sin(radians)*radius),y=128-(int)(Math.cos(radians)*radius),size=Math.max(1,(int)(78*rollScale));
            graphics.pose().pushMatrix();graphics.pose().translate(128,128);graphics.pose().rotate((float)radians);
            MedicalClient.blit(graphics,"bandage_pointer",-101,-101,202,202,300,300,0xFFFFFFFF);graphics.pose().popMatrix();
            graphics.pose().pushMatrix();graphics.pose().translate(x,y);graphics.pose().rotate((float)(radians*3));
            MedicalClient.blit(graphics,"bandage_roll",-size/2,-size/2,size,size,104,104,0xFFFFFFFF);graphics.pose().popMatrix();
            MedicalStatusIcons.draw(graphics,MedicalStatusIcons.bleeding(ClientMedicalState.snapshot().part(part)),128,128,32,0xFFF08888);
        } else if(kind==TreatmentKind.FENTANYL) {
            MedicalClient.blit(graphics,"syringe_ground",-22,(int)Math.round(SYRINGE_ENTRY_Y),300,44,200,29,0xFFFFFFFF);
            CuMedicalHand.line(graphics,122,(int)SYRINGE_ENTRY_Y,134,(int)SYRINGE_ENTRY_Y);
            graphics.pose().pushMatrix();graphics.pose().translate((float)objectX+11.5f,(float)objectY+SYRINGE_HEIGHT);graphics.pose().rotate((float)Math.toRadians(syringeAngle));
            MedicalClient.blit(graphics,"syringe",-12,-SYRINGE_HEIGHT,23,123,30,164,0xFFFFFFFF);
            int fillHeight=Math.round(59*remaining);
            if(fillHeight>0) {
                int sourceHeight=Math.max(1,Math.round(78*remaining));
                graphics.blit(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("decaying_aftermath","textures/gui/procedures/syringe_fill.png"),
                        -9,-40-fillHeight,0f,78-sourceHeight,15,fillHeight,20,sourceHeight,20,78,0xFF0084AD);
            }
            graphics.fill(-15,-SYRINGE_HEIGHT+Math.round((1-remaining)*20)-1,14,-SYRINGE_HEIGHT+Math.round((1-remaining)*20)+1,0xFFEAEAEA);graphics.pose().popMatrix();
        } else if(isPotion()) {
            drawSelectedPart(graphics,111,80,34,116);
        } else {
            drawSelectedPart(graphics,117,75,27,116);
            if(stage==0) {
                int toolWidth=kind==TreatmentKind.SPLINT?27:45;
                graphics.pose().pushMatrix();graphics.pose().translate((float)objectX+toolWidth/2f,(float)objectY+24.5f);graphics.pose().rotate((float)Math.toRadians(kind==TreatmentKind.SPLINT?deviceAngle:0));
                MedicalClient.blit(graphics,kind==TreatmentKind.SPLINT?"splint":"tourniquet",-toolWidth/2,-24,toolWidth,49,kind==TreatmentKind.SPLINT?16:36,kind==TreatmentKind.SPLINT?29:39,0xFFFFFFFF);graphics.pose().popMatrix();
                int cue=errorTicks>0?0xFFF08888:CuProcedureGeometry.deviceAligned(objectX,objectY,deviceAngle,kind==TreatmentKind.SPLINT)?0xFFBDD1C0:0xFF707A73;
                graphics.fill(114,103,116,113,cue);graphics.fill(114,103,124,105,cue);
                graphics.fill(116+toolWidth+1,146,116+toolWidth+3,156,cue);graphics.fill(116+toolWidth-7,154,116+toolWidth+3,156,cue);
            } else {
                MedicalClient.blit(graphics,kind==TreatmentKind.SPLINT?"splint":"tourniquet",116,104,kind==TreatmentKind.SPLINT?27:45,49,kind==TreatmentKind.SPLINT?16:36,kind==TreatmentKind.SPLINT?29:39,0xFFFFFFFF);
                if(kind==TreatmentKind.SPLINT) {ProcedureVisuals.rail(graphics,112,fasteners.progress(1),fasteners.progress(2),stage<=2);ProcedureVisuals.rail(graphics,164,fasteners.progress(3),fasteners.progress(4),stage>2);}
                else {ProcedureVisuals.rail(graphics,112,fasteners.progress(1),0,stage==1);if(stage>=2) {graphics.pose().pushMatrix();graphics.pose().translate(128,128);graphics.pose().rotate((float)Math.toRadians(angle));CuMedicalHand.cube(graphics,-23,-3,46,6,3);CuMedicalHand.cube(graphics,-26,-6,7,12,2);CuMedicalHand.cube(graphics,19,-6,7,12,2);graphics.pose().popMatrix();}}
            }
        }
        graphics.enableScissor(ox-(int)(10*scale),oy+(int)(14*scale),ox+(int)(266*scale),oy+(int)(233*scale));
        float handAngle=hand.rotation(isWrap())+(kind==TreatmentKind.FENTANYL?(float)Math.toRadians(65):0);
        CuMedicalHand.extract(graphics,hand.visualX(partialTick),hand.visualY(partialTick),handAngle,holding||injecting);
        if(isPotion())drawPotion(graphics);graphics.disableScissor();
        graphics.centeredText(font,Component.translatable("decaying_aftermath.procedure.remaining",(int)Math.ceil(remaining*100-.00001f)).getString(),128,244,0xFFE4E9E4);
        graphics.fill(89,259,167,261,0xFF414A43);graphics.fill(89,259,89+Math.round(78*remaining),261,0xFFC3CFC4);
        if(errorTicks>0)graphics.centeredText(font,Component.translatable("decaying_aftermath.procedure.guide.steady").getString(),128,222,0xFFF08888);
        graphics.pose().popMatrix();
        var lines=ProcedureVisuals.lines(guide(),280,font::width);
        graphics.pose().pushMatrix();graphics.pose().translate(ox,oy);graphics.pose().scale(scale,scale);
        int gy=-lines.size()*11-17;for(String line:lines) {graphics.centeredText(font,line,128,gy,0xFFE4E9E4);gy+=11;}
        graphics.pose().popMatrix();
        graphics.centeredText(font,Component.translatable("decaying_aftermath.procedure.cancel").getString(),width/2,Math.min(height-12,(int)(oy+272*scale+6)),0xFFB8C0BA);
    }
    private void drawPotion(GuiGraphicsExtractor graphics) {
        // The grasped bottle and stream must remain legible in front of the opaque black palm.
        graphics.pose().pushMatrix();graphics.pose().translate((float)objectX+16,(float)objectY+16);graphics.pose().rotate((float)Math.toRadians(potionTilt));graphics.pose().scale(2,2);graphics.item(source(),-8,-8);graphics.pose().popMatrix();
        if(stage==1&&holding) {
            var mouth=CuProcedureGeometry.bottleMouth(objectX,objectY,potionTilt);var wound=CuProcedureGeometry.part(part,111,80,34,116);
            int bottom=wound.y()+wound.height()-4,span=Math.max(1,bottom-(int)mouth.y());
            for(int i=0;i<5;i++) {int dy=(int)mouth.y()+3+(elapsed+i*7)%span;graphics.fill((int)mouth.x()-1,dy,(int)mouth.x()+2,Math.min(dy+3,bottom),0xFFB0BDB3);}
        }
    }
    private void drawSelectedPart(GuiGraphicsExtractor graphics,int x,int y,int w,int h) {ProcedureVisuals.part(graphics,part,x,y,w,h);}
    private String guide() {
        String suffix=isWrap()?"wrap":kind==TreatmentKind.FENTANYL?"syringe":isPotion()?"potion":kind==TreatmentKind.SPLINT?(stage==0?"splint_align":"splint_straps"):(stage==0?"tourniquet_fit":"tourniquet_tighten");
        return Component.translatable("decaying_aftermath.procedure.guide."+suffix).getString();
    }
    private void finish() {
        if(ended)return;
        if(MedicalSupplyPolicy.installation(kind))ClientMedicalState.finishProcedure(slot,part,kind);
        end();minecraft.gui.setScreen(previous);
    }
    private void stopSyringeLoop() {if(syringeLoop!=null){syringeLoop.close();syringeLoop=null;}}
    private void end() {stopSyringeLoop();if(!ended){ended=true;ClientMedicalState.endProcedure();}}
    @Override public void onClose() {end();if(minecraft.player!=null)minecraft.gui.setScreen(previous);}
    @Override public void removed() {end();super.removed();}
    @Override public boolean isPauseScreen() {return false;}
}
