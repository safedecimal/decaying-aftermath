package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import io.github.safedecimal.decayingaftermath.medical.DefibGame;
import io.github.safedecimal.decayingaftermath.medical.MedicalItems;
import io.github.safedecimal.decayingaftermath.medical.MedicalPhysiology;
import io.github.safedecimal.decayingaftermath.network.DefibNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

/** CU-monitor-style presentation of the two defibrillator procedures (pads + automatic shock, or rhythm-lock). */
public final class DefibScreen extends Screen {
    private static final String KEY="decaying_aftermath.defib.";
    private static final Identifier MONITOR_FONT=id("textures/font/monitor.png");
    private static final int WIDTH=280, HEIGHT=182, HEADER_H=24;
    private static final int FRAME_X=8, FRAME_Y=28, FRAME_W=264, FRAME_H=136;
    private static final int ECG_X=13, ECG_Y=48, ECG_W=254, ECG_H=32;
    private static final int STRIP_X=14, STRIP_Y=114, STRIP_W=252, STRIP_H=14;
    private static final int CELL_W=116, CELL_H=34;
    private static final int BG=0xFF141F1C, HEADER_BG=0xFF1F302A, EDGE=0xFF33524A, SCREEN_BG=0xFF07110E,
            SCREEN_EDGE=0xFF274A3C, TRACK=0xFF13231E, WINDOW=0xFF327D59, PHOSPHOR=0xFF18B93B,
            ECG_COLOR=0x002BE080, TEXT=0xFFFFF2D6, MUTED=0xFF9DB4A6, GREEN=0xFF7FE4A7,
            AMBER=0xFFFFA763, RED=0xFFF19486, DIM=0xFF5E7368;
    private static final String[] SYMBOLS={"◆","●","■","▲"};

    private final int session,layout;
    private final boolean automatic;
    private int pads,locks,result,wait,feedback,flash,lockFlash,ticks;
    private long readyAt=Long.MIN_VALUE;
    private final EcgTrace ecg=new EcgTrace();

    private DefibScreen(DefibNetwork.State state) {
        super(Component.translatable(KEY+(state.automatic()?"automatic":"manual")));
        session=state.session();layout=state.layout()&3;automatic=state.automatic();
        pads=state.pads();locks=state.locks();result=state.result();readyAt=state.readyAt();
    }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DefibNetwork.State.TYPE,(state,context)->context.client().execute(()->{
            if(state.result()==DefibNetwork.OPEN) context.client().gui.setScreen(new DefibScreen(state));
            else if(context.client().gui.screen() instanceof DefibScreen screen && screen.session==state.session()) screen.update(state);
        }));
    }
    private void update(DefibNetwork.State state) {
        pads=state.pads();locks=state.locks();result=state.result();wait=0;readyAt=state.readyAt();
        if(result==DefibNetwork.MISS)feedback=15;
        if(result==DefibNetwork.PROGRESS) {
            if(!automatic)lockFlash=10;
            beep(1.4f);
        }
        if(result==DefibNetwork.DONE) {flash=16;pling();}
    }
    private boolean finished() {return result==DefibNetwork.DONE||result==DefibNetwork.ABORT;}
    private void send(int control) {
        if(ClientPlayNetworking.canSend(DefibNetwork.Input.TYPE))
            ClientPlayNetworking.send(new DefibNetwork.Input(session,control));
    }
    private void beep(float pitch) {
        if(minecraft!=null)MedicalClient.sound("text_ping",.35f,pitch);
    }
    private void pling() {
        if(minecraft!=null)minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace("block.note_block.pling")),1.2f,.8f));
    }
    @Override public boolean isPauseScreen() {return false;}
    @Override public void removed() {if(!finished())send(DefibGame.CANCEL);super.removed();}
    @Override public void tick() {
        if(minecraft==null||minecraft.player==null||!minecraft.player.isAlive()){onClose();return;}
        ticks++;
        if(wait>0)wait--; if(feedback>0)feedback--;
        if(lockFlash>0)lockFlash--; if(flash>0)flash--;
        if(finished())return;
        BodySnapshot body=ClientMedicalState.snapshot();
        if(Float.isFinite(body.bpm()))ecg.advance(0.05,Math.max(0,body.bpm()));
    }
    private int x() {return (width-WIDTH)/2;}
    private int y() {return (height-HEIGHT)/2;}
    private long now() {return minecraft==null||minecraft.level==null?0:minecraft.level.getGameTime();}
    private int phase() {
        if(readyAt==Long.MIN_VALUE)return -1;
        long elapsed=now()-readyAt;
        return elapsed<0?-1:(int)(elapsed%DefibGame.CYCLE);
    }
    private boolean windowOpen(int phase) {return phase>=DefibGame.WINDOW_MIN && phase<=DefibGame.WINDOW_MAX;}
    private int progress() {
        if(readyAt==Long.MIN_VALUE)return 0;
        long elapsed=now()-readyAt;
        return elapsed<0?0:Math.clamp((int)(elapsed*100/DefibGame.SCAN),0,100);
    }
    private boolean in(int px,int py,int bw,int bh,MouseButtonEvent e) {
        double mx=e.x()-x(), my=e.y()-y(); return mx>=px && mx<px+bw && my>=py && my<py+bh;
    }
    private int cellX(int i) {return (i%2)==0?30:156;}
    private int cellY(int i) {return (i/2)==0?58:100;}
    private int cellAt(MouseButtonEvent e) {
        double mx=e.x()-x(), my=e.y()-y();
        for(int i=0;i<4;i++) {
            int lx=cellX(i), ly=cellY(i);
            if(mx>=lx && mx<lx+CELL_W && my>=ly && my<ly+CELL_H)return i;
        }
        return -1;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(event.button()!=1)return false;
        if(finished()) {if(in(WIDTH/2-75,112,150,24,event))onClose();return true;}
        if(automatic&&pads<2) {
            int cell=cellAt(event);
            if(cell>=0)act(cell);
            return true;
        }
        if(!automatic&&pads==2&&in(WIDTH/2-75,140,150,24,event))act(DefibGame.CONFIRM);
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int key=event.key();
        if(finished()&&(key==257||key==32)){onClose();return true;}
        if(automatic&&pads<2&&key>=49&&key<=52){act(key-49);return true;}
        if(!automatic&&pads==2&&(key==32||key==257)){act(DefibGame.CONFIRM);return true;}
        return super.keyPressed(event);
    }
    private void act(int control) {
        if(finished()||wait>0)return;
        wait=6;send(control);
    }
    private void center(GuiGraphicsExtractor g,Component text,int yy,int color) {
        g.centeredText(font,text.getString(),WIDTH/2,yy,color);
    }
    private void button(GuiGraphicsExtractor g,int px,int py,int w,int h,String label,int color) {
        g.fill(px,py,px+w,py+h,0xFF263731);
        g.outline(px,py,w,h,color);
        g.centeredText(font,label,px+w/2,py+(h-8)/2,color);
    }
    private void frame(GuiGraphicsExtractor g) {
        g.fill(0,0,WIDTH,HEIGHT,BG);
        g.fill(0,0,WIDTH,HEADER_H,HEADER_BG);
        g.outline(0,0,WIDTH,HEIGHT,EDGE);
        g.fill(0,HEADER_H,WIDTH,HEADER_H+1,0xFF0B1411);
    }
    private void header(GuiGraphicsExtractor g) {
        g.item(new ItemStack(automatic?MedicalItems.AED:MedicalItems.MANUAL_DEFIBRILLATOR),7,5);
        center(g,getTitle(),8,TEXT);
        g.centeredText(font,"ESC",WIDTH-22,8,DIM);
    }
    private void monitor(GuiGraphicsExtractor g) {
        g.fill(FRAME_X,FRAME_Y,FRAME_X+FRAME_W,FRAME_Y+FRAME_H,SCREEN_BG);
        g.outline(FRAME_X,FRAME_Y,FRAME_W,FRAME_H,SCREEN_EDGE);
    }
    private void monitorTitle(GuiGraphicsExtractor g,String left,String right,int color) {
        monitorText(g,left,FRAME_X+12,FRAME_Y+10,PHOSPHOR);
        if(right!=null&&!right.isEmpty()) {
            int width=0;for(int i=0;i<right.length();i++)width+=MedicalPanelGlyphs.advance(right.charAt(i));
            monitorText(g,right,FRAME_X+FRAME_W-12-width,FRAME_Y+10,color);
        }
    }
    private void monitorText(GuiGraphicsExtractor g,String value,int x,int y,int color) {
        if(value==null||value.isEmpty())return;
        for(int i=0;i<value.length();i++) {
            int glyph=MedicalPanelGlyphs.index(value.charAt(i));
            g.blit(RenderPipelines.GUI_TEXTURED,MONITOR_FONT,x,y,(glyph%16)*8f,(glyph/16)*10f,8,10,128,60,color);
            x+=MedicalPanelGlyphs.advance(value.charAt(i));
        }
    }
    private void ecg(GuiGraphicsExtractor g,int x,int y,int w,int h) {
        g.fill(x,y,x+w,y+h,0xFF060F0C);
        int srcW=EcgTrace.WIDTH, srcH=EcgTrace.HEIGHT;
        for(int column=0;column<srcW;column++) {
            int gx=x+column*w/srcW;
            int gw=(column+1)*w/srcW-gx; if(gw<1)gw=1;
            for(int row=0;row<srcH;row++) {
                int alpha=ecg.alpha(column,row);
                if(alpha==0)continue;
                int gy=y+row*h/srcH;
                int gh=(row+1)*h/srcH-gy; if(gh<1)gh=1;
                g.fill(gx,gy,gx+gw,gy+gh,(alpha<<24)|ECG_COLOR);
            }
        }
        g.fill(x,y+h/2,x+w,y+h/2+1,0x001B4D2E);
    }
    private void strip(GuiGraphicsExtractor g,int x,int y,int w,int h,int phase,boolean flashed) {
        g.fill(x,y,x+w,y+h,flashed?0xFF153A28:TRACK);
        int zx=x+w*DefibGame.WINDOW_MIN/(DefibGame.CYCLE-1);
        int zw=w*(DefibGame.WINDOW_MAX-DefibGame.WINDOW_MIN)/(DefibGame.CYCLE-1);
        g.fill(zx,y+2,zx+zw,y+h-2,flashed?0xFF3DA96E:WINDOW);
        g.outline(zx,y+1,zw,h-2,flashed?GREEN:0xFF5FBF85);
        int mx=phase<0?x+2:x+(w-1)*phase/(DefibGame.CYCLE-1);
        g.fill(mx-1,y-3,mx+1,y+h+3,phase<0?0xFF5E7368:TEXT);
    }
    private void pips(GuiGraphicsExtractor g,int cx,int y) {
        for(int i=0;i<3;i++) {
            int px=cx-21+i*16;
            boolean on=locks>i;
            g.fill(px,y,px+10,y+10,on?GREEN:0xFF233129);
            g.outline(px,y,10,10,on?0xFF9DFFBE:0xFF42554B);
        }
    }
    private void padUi(GuiGraphicsExtractor g) {
        int desired=DefibGame.port(layout,pads);
        center(g,Component.translatable(KEY+"pad",String.valueOf((char)('A'+pads)),SYMBOLS[desired]),40,TEXT);
        for(int i=0;i<4;i++) {
            boolean placed=i/2<pads&&i==DefibGame.port(layout,i/2);
            boolean target=i/2==pads;
            int symbolColor=placed?GREEN:(target?AMBER:0xFF7E8F86);
            int cellFill=placed?0xFF153A28:0xFF1A2420;
            int cellOutline=placed?0xFF7FE4A7:(target?0xFFE0A763:0xFF33443C);
            int lx=cellX(i), ly=cellY(i);
            g.fill(lx,ly,lx+CELL_W,ly+CELL_H,cellFill);
            g.outline(lx,ly,CELL_W,CELL_H,cellOutline);
            if(target&&feedback<=0) {
                int pulse=(150+(int)(70*Math.sin(ticks/6.0)))&0xFF;
                g.fill(lx+3,ly+2,lx+CELL_W-3,ly+4,(pulse<<24)|0xFFE0A763);
            }
            g.centeredText(font,SYMBOLS[i],lx+CELL_W/2,ly+(CELL_H-8)/2,symbolColor);
            g.centeredText(font,String.valueOf(i+1),lx+CELL_W-12,ly+(CELL_H-8)/2,placed?GREEN:DIM);
        }
        center(g,Component.translatable(KEY+(feedback>0?"miss_pad":"keys")),142,feedback>0?RED:0xFFB8CBBF);
    }
    private void scanUi(GuiGraphicsExtractor g) {
        monitorTitle(g,"AED",bpmText(),PHOSPHOR);
        ecg(g,ECG_X,ECG_Y,ECG_W,ECG_H);
        int p=progress();
        int bx=FRAME_X+16, bw=FRAME_W-32;
        g.fill(bx,FRAME_Y+62,bx+bw,FRAME_Y+72,TRACK);
        g.fill(bx,FRAME_Y+62,bx+bw*p/100,FRAME_Y+72,AMBER);
        g.outline(bx,FRAME_Y+62,bw,10,SCREEN_EDGE);
        center(g,Component.translatable(KEY+"scan"),FRAME_Y+82,TEXT);
        center(g,Component.translatable(KEY+"automatic_fire"),FRAME_Y+108,MUTED);
    }
    private void manualUi(GuiGraphicsExtractor g) {
        monitorTitle(g,"RHYTHM LOCK",bpmText(),bpmColor());
        ecg(g,ECG_X,ECG_Y,ECG_W,ECG_H);
        int phase=phase();
        boolean inWindow=windowOpen(phase);
        String status; int statusColor;
        if(feedback>0){status=Component.translatable(KEY+"miss_beat").getString();statusColor=RED;}
        else if(phase<0){status=Component.translatable(KEY+"calibrating").getString();statusColor=MUTED;}
        else if(inWindow){status=Component.translatable(KEY+"now").getString();statusColor=GREEN;}
        else{status=Component.translatable(KEY+"wait").getString();statusColor=MUTED;}
        center(g,Component.literal(status),90,statusColor);
        pips(g,WIDTH/2,102);
        strip(g,STRIP_X,STRIP_Y,STRIP_W,STRIP_H,phase,lockFlash>0);
        button(g,WIDTH/2-75,140,150,24,Component.translatable(KEY+"lock").getString(),inWindow?GREEN:MUTED);
    }
    private String bpmText() {
        BodySnapshot body=ClientMedicalState.snapshot();
        int bpm=(int)Math.round(body.bpm());
        if(bpm<=0)return "--";
        return String.format("%d|%d/%d",bpm,MedicalPhysiology.systolic(body),MedicalPhysiology.diastolic(body));
    }
    private int bpmColor() {
        return ClientMedicalState.snapshot().bpm()>185?AMBER:PHOSPHOR;
    }
    private void finishedUi(GuiGraphicsExtractor g) {
        boolean done=result==DefibNetwork.DONE;
        g.fill(0,0,WIDTH,HEIGHT,0xFF0B1411);
        g.outline(0,0,WIDTH,HEIGHT,EDGE);
        g.item(new ItemStack(automatic?MedicalItems.AED:MedicalItems.MANUAL_DEFIBRILLATOR),WIDTH/2-8,54);
        center(g,Component.translatable(KEY+(done?"done":"abort")),80,done?GREEN:RED);
        button(g,WIDTH/2-75,112,150,24,Component.translatable(KEY+"close").getString(),TEXT);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        extractTransparentBackground(g);
        if(minecraft==null||minecraft.level==null)return;
        g.pose().pushMatrix();g.pose().translate(x(),y());
        frame(g);
        if(finished())finishedUi(g);
        else {
            header(g);
            monitor(g);
            if(automatic&&pads<2)padUi(g);
            else if(automatic)scanUi(g);
            else manualUi(g);
        }
        g.pose().popMatrix();
        if(flash>0&&finished()) {
            int alpha=Math.min(220,flash*14);
            g.fill(0,0,width,height,(alpha<<24)|0xFFFFFF);
        }
    }
    private static Identifier id(String path) {return Identifier.fromNamespaceAndPath(DecayingAftermath.MOD_ID,path);}
}
