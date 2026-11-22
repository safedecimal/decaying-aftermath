package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.DefibGame;
import io.github.safedecimal.decayingaftermath.network.DefibNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLMouse;
import java.util.function.Consumer;

/** Original CU prefab geometry, sprites, inertial hand and separate charge/shock controls. */
public final class DefibScreen extends Screen {
    private static final String KEY="decaying_aftermath.defib.";
    private final int session;
    private final boolean automatic;
    private final Screen previous;
    private final DefibHandMotion hand=new DefibHandMotion();
    private final ProcedureCursor cursor=new ProcedureCursor();
    private final Consumer<DefibNetwork.Input> inputSender=DefibScreen::sendNetwork;
    private final EcgTrace ecg=new EcgTrace(122,41);
    private DefibNetwork.State state;
    private float scale=1,pad0X,pad0Y,pad1X,pad1Y,offsetX,offsetY,dialCharge;
    private double pointerX,pointerY;
    private int ox,oy,held=-1,staleTicks;
    private boolean pressed,aborted;

    private DefibScreen(DefibNetwork.State state,Screen previous) {
        super(Component.translatable(KEY+(state.automatic()?"automatic":"manual")));
        session=state.session();automatic=state.automatic();this.previous=previous;this.state=state;
        pad0X=state.pad0X();pad0Y=state.pad0Y();pad1X=state.pad1X();pad1Y=state.pad1Y();
        dialCharge=state.desiredCharge();
        if(automatic)MedicalClient.sound("cu_aedstart",1,1);
    }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DefibNetwork.State.TYPE,(state,context)->context.client().execute(()->{
            if(state.result()==DefibNetwork.OPEN)
                context.client().gui.setScreen(new DefibScreen(state,context.client().gui.screen()));
            else if(context.client().gui.screen() instanceof DefibScreen screen&&screen.session==state.session())screen.update(state);
        }));
    }
    @Override protected void init() {
        scale=Math.max(.25f,Math.min(1f,Math.min((width-16f)/262,(height-16f)/262)));
        ox=Math.round((width-256*scale)/2);oy=Math.round((height-256*scale)/2);
    }
    private DefibGame.Stage stage() {return DefibGame.Stage.values()[Math.clamp(state.stage(),0,5)];}
    private void update(DefibNetwork.State next) {
        if(automatic&&next.stage()!=state.stage()) {
            String sound=switch(DefibGame.Stage.values()[Math.clamp(next.stage(),0,5)]) {
                case ANALYZING -> "cu_aedanalyze";
                case READY -> "cu_aedstart";
                case NO_SHOCK -> "cu_aedfail";
                case CHARGING -> "cu_aedcharge";
                default -> null;
            };
            if(sound!=null)MedicalClient.sound(sound,1,1);
        }
        if(!automatic&&next.shocks()>state.shocks())MedicalClient.sound("cu_manualdefib",1,1);
        if(held!=0) {pad0X=next.pad0X();pad0Y=next.pad0Y();}
        if(held!=1) {pad1X=next.pad1X();pad1Y=next.pad1Y();}
        if(held!=2)dialCharge=next.desiredCharge();
        aborted|=next.result()==DefibNetwork.ABORT;
        state=next;staleTicks=0;
    }
    private void send(int control) {
        inputSender.accept(new DefibNetwork.Input(session,control,
                (float)Math.clamp(hand.x(),-524,524),(float)Math.clamp(hand.y(),-524,524)));
    }
    private static void sendNetwork(DefibNetwork.Input input) {
        if(ClientPlayNetworking.canSend(DefibNetwork.Input.TYPE))ClientPlayNetworking.send(input);
    }
    @Override public boolean isPauseScreen() {return false;}
    @Override public void onClose() {minecraft.gui.setScreen(minecraft.player!=null&&minecraft.player.isAlive()?previous:null);}
    @Override public void removed() {send(DefibGame.CANCEL);cursor.restore();super.removed();}
    private void target(double x,double y) {
        pointerX=Math.clamp(((x-ox)/scale-128)*4,-524,524);
        pointerY=Math.clamp((128-(y-oy)/scale)*4,-524,524);
    }
    @Override public void mouseMoved(double x,double y) {target(x,y);}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {target(event.x(),event.y());return pressed;}
    @Override public void tick() {
        if(minecraft.player==null||!minecraft.player.isAlive()) {onClose();return;}
        if(++staleTicks>200)aborted=true;
        if(aborted)return;
        var body=ClientMedicalState.snapshot();advanceHand(body.pain(),body.consciousness());
        ecg.advance(.05,state.bpm());
    }
    void advanceHand(float pain,float consciousness) {
        hand.beginTick();
        for(int i=0;i<3;i++)hand.step(pointerX,pointerY,pain,consciousness,held==2);
        if(pressed&&held>=0) {
            if(held==0) {pad0X=(float)hand.x()+offsetX;pad0Y=(float)hand.y()+offsetY;}
            if(held==1) {pad1X=(float)hand.x()+offsetX;pad1Y=(float)hand.y()+offsetY;}
            if(held==2)dialCharge=DefibGame.chargeForAngle(angle((float)hand.x(),(float)hand.y()));
            send(DefibGame.MOVE);
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        // MouseButtonEvent is SDL (left=1/right=3), not ContainerInput (left=0/right=1).
        if(event.button()==SDLMouse.SDL_BUTTON_RIGHT) {onClose();return true;}
        if(event.button()!=SDLMouse.SDL_BUTTON_LEFT)return false;
        if(aborted)return true; // An interrupted session never turns a left click into an exit.
        target(event.x(),event.y());pressed=true;
        float x=(float)hand.x(),y=(float)hand.y();
        if(automatic&&stage()==DefibGame.Stage.PLACING) {
            for(int pad=1;pad>=0;pad--) {
                float px=pad==0?pad0X:pad1X,py=pad==0?pad0Y:pad1Y;
                if(DefibGame.hit(x,y,px,py,267,pad==0?342:276)) {
                    held=pad;offsetX=px-x;offsetY=py-y;send(pad);break;
                }
            }
        } else if(automatic) {
            if(DefibGame.hit(x,y,0,-421,141,141))send(DefibGame.CONFIRM);
        } else if(DefibGame.hit(x,y,-225,-174,150,150)) {held=2;send(DefibGame.DIAL);}
        else if(DefibGame.hit(x,y,8,-174,111,111))send(DefibGame.CHARGE);
        else if(DefibGame.hit(x,y,234.3f,-174,111,111))send(DefibGame.SHOCK);
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if(event.button()!=SDLMouse.SDL_BUTTON_LEFT)return false;
        if(held>=0)send(DefibGame.RELEASE);
        held=-1;pressed=false;return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {return super.keyPressed(event);}
    private static float angle(float x,float y) {
        float a=(float)Math.toDegrees(Math.atan2(y+174,x+225))-90;return (a+540)%360-180;
    }
    private static void sprite(GuiGraphicsExtractor g,String name,float cx,float cy,int w,int h,int sw,int sh,int color) {
        g.blit(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("decaying_aftermath","textures/gui/defib/"+name+".png"),
                Math.round(128+cx*.25f-w*.125f),Math.round(128-cy*.25f-h*.125f),0f,0f,
                Math.round(w*.25f),Math.round(h*.25f),sw,sh,sw,sh,color);
    }
    private void text(GuiGraphicsExtractor g,String text,float x,float y,float nativeSize,float nativeWidth,int color) {
        text(g,text,x,y,nativeSize,nativeWidth,color,2);
    }
    private void text(GuiGraphicsExtractor g,String text,float x,float y,float nativeSize,float nativeWidth,int color,int alignment) {
        if(CuDefibFont.draw(g,text,x,y,nativeSize,nativeWidth,color,alignment))return;
        // The original CU Retro GamingPix face is ASCII-only. Russian text needs the host fallback font.
        float size=nativeSize/36f;
        if(font.width(text)*size>nativeWidth*.25f)size=nativeWidth*.25f/Math.max(1,font.width(text));
        float offset=alignment==1?-nativeWidth*.125f+font.width(text)*size/2:alignment==4?nativeWidth*.125f-font.width(text)*size/2:0;
        g.pose().pushMatrix();g.pose().translate(128+x*.25f+offset,128-y*.25f);g.pose().scale(size,size);
        g.centeredText(font,text,0,-4,color);g.pose().popMatrix();
    }
    private void guide(GuiGraphicsExtractor g) {
        String key=KEY+(automatic?"cu_aed_guide":"cu_manual_guide");
        g.pose().pushMatrix();g.pose().translate(128,18);g.pose().scale(.65f,.65f);
        var lines=font.split(Component.translatable(key),350);
        for(int i=0;i<lines.size();i++)g.text(font,lines.get(i),-font.width(lines.get(i))/2,i*10,0xFFFFFFFF,false);
        g.pose().popMatrix();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        extractTransparentBackground(g);
        if(!aborted)cursor.hide(minecraft.getWindow().handle());else cursor.restore();
        g.pose().pushMatrix();g.pose().translate(ox,oy);g.pose().scale(scale,scale);
        sprite(g,"base_minigamescreen",0,0,1048,1048,256,256,0xFFFFFFFF);
        guide(g);
        if(automatic)drawAed(g);else drawManual(g);
        sprite(g,automatic?"device_aed":"device_manual_defibrillator",-381,-155,40,automatic?40:29,automatic?12:14,automatic?12:10,0xFFFFFFFF);
        text(g,getTitle().getString()+" ("+Math.round(state.battery()*100)+"%)",-257,369,20,400,0xFFFFFFFF);
        if(aborted)text(g,Component.translatable(KEY+"abort").getString(),0,-330,36,1000,0xFFFFA0A0);
        text(g,Component.translatable(KEY+"cu_exit").getString(),0,-493,24,850,0xFFFFFFFF);
        if(!aborted) {
            double hx=hand.visualX(partialTick,held==2),hy=hand.visualY(partialTick,held==2);
            boolean grasp=automatic?stage().ordinal()<=1:hx<=-60;
            extractHand(g,128+hx*.25,128-hy*.25,hand.rotation(),state.leftHand(),grasp&&pressed);
        }
        g.pose().popMatrix();
    }
    static void extractHand(GuiGraphicsExtractor g,double x,double y,float rotation,boolean leftHand,boolean grasp) {
        g.pose().pushMatrix();g.pose().translate((float)x,(float)y);g.pose().rotate(rotation);
        if(leftHand)g.pose().scale(-1,1);
        // Same 4x12 Minecraft block arm as inventory and every other medical minigame.
        CuMedicalHand.extract(g,0,0,0,grasp);
        g.pose().popMatrix();
    }
    private void drawAed(GuiGraphicsExtractor g) {
        sprite(g,"aed_container_line",0,0,774,768,129,128,0xFFFFFFFF);
        sprite(g,"aed_container_line_1",-209.8f,155.2f,414,60,69,1,0xAAFFFFFF);
        sprite(g,"aed_container_line_2",209.9f,-141.8f,414,60,69,1,0xAAFFFFFF);
        sprite(g,"aed_pad1",pad0X,pad0Y,267,342,89,114,0xFFFFFFFF);
        sprite(g,"aed_pad2",pad1X,pad1Y,267,276,89,92,0xFFFFFFFF);
        sprite(g,"aed_shock",0,-421,141,141,47,47,0xFFFFFFFF);
        String label=Component.translatable(KEY+"cu_aed_state"+stage().ordinal()).getString();
        if(stage()==DefibGame.Stage.ANALYZING||stage()==DefibGame.Stage.CHARGING)label+=".".repeat(1+(staleTicks/3)%3);
        text(g,label,0,-289,36,1000,0xFFFFFFFF);
    }
    private void drawManual(GuiGraphicsExtractor g) {
        sprite(g,"manual_screen",0,33,768,660,256,220,0xFFFFFFFF);
        g.pose().pushMatrix();g.pose().translate(128-225*.25f,128+174*.25f);
        g.pose().rotate((float)Math.toRadians(-DefibGame.angleForCharge(dialCharge)));
        g.blit(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("decaying_aftermath","textures/gui/defib/manual_dial.png"),
                -19,-19,0f,0f,38,38,50,50,50,50,0xFFFFFFFF);g.pose().popMatrix();
        sprite(g,"manual_charge",8,-174,111,111,37,37,0xFFFFFFFF);
        sprite(g,"manual_shock",234.3f,-174,111,111,37,37,0xFFFFFFFF);
        g.enableScissor(181,71,181+Math.round(10*state.battery()),78);
        sprite(g,"manual_screen_battery",232.5f,214.5f,39,27,13,9,0xFFFFFFFF);g.disableScissor();
        if(stage()==DefibGame.Stage.CHARGING)sprite(g,"manual_screen_charging",151.5f,204,99,24,33,8,0xFFFFFFFF);
        text(g,Integer.toString(state.shocks()),-220.586f,214.6f,33.333f,50.916f,0xFFFFFFFF);
        text(g,Integer.toString(Math.round(state.currentCharge())),-76.4f,214.6f,33.333f,50.916f,0xFFFFFFFF,4);
        text(g,Integer.toString(Math.round(dialCharge)),8.5f,214.6f,33.333f,50.916f,0xFFFFFFFF,1);
        text(g,Integer.toString(Math.round(state.bpm())),183.8f,127.5f,66.667f,50.916f,0xFFFFFFFF);
        text(g,state.estimateLow()+"-"+state.estimateHigh(),-157.5f,19,33.333f,162.736f,0xFFFFFFFF,1);
        text(g,Integer.toString(Math.round(dialCharge)),-157.5f,-40.3f,33.333f,162.736f,0xFFFFFFFF,1);
        text(g,"n/a",119.4f,30.5f,33.333f,162.736f,0xFFFFFFFF,1); // No fabricated CU blood-pressure field in Minecraft.
        text(g,Math.round(state.oxygen()*100)+"%",119.4f,-33.3f,33.333f,162.736f,0xFFFFFFFF,1);
        g.pose().pushMatrix();g.pose().translate(61.25f,80.75f);g.pose().scale(.75f,.75f);
        for(int row=0;row<41;row++)for(int column=0;column<122;column++) {
            int alpha=ecg.alpha(column,row);if(alpha>12)g.fill(column,row,column+1,row+1,(alpha<<24)|0x0045ADFF);
        }
        g.pose().popMatrix();
    }
}
