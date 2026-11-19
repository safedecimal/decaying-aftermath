package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.DefibGame;
import io.github.safedecimal.decayingaftermath.medical.MedicalItems;
import io.github.safedecimal.decayingaftermath.network.DefibNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

/** Two electrodes, then either manual rhythm locks or automatic analysis. */
public final class DefibScreen extends Screen {
    private static final String KEY="decaying_aftermath.defib.";
    private static final int WIDTH=238, HEIGHT=149, GREEN=0xFF7FE4A7, RED=0xFFF19486;
    private final int session,layout;
    private final boolean automatic;
    private int pads,locks,ticks,result,wait,feedback;
    private static final String[] SYMBOLS={"◆","●","■","▲"};

    private DefibScreen(DefibNetwork.State state) {
        super(Component.translatable(KEY+(state.automatic()?"automatic":"manual")));
        session=state.session();layout=state.layout()&3;automatic=state.automatic();pads=state.pads();
    }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DefibNetwork.State.TYPE,(state,context)->context.client().execute(()->{
            if(state.result()==DefibNetwork.OPEN) context.client().gui.setScreen(new DefibScreen(state));
            else if(context.client().gui.screen() instanceof DefibScreen screen && screen.session==state.session()) screen.update(state);
        }));
    }
    private void update(DefibNetwork.State state) {
        if(pads<2 && state.pads()==2)ticks=0;
        pads=state.pads(); locks=state.locks(); result=state.result();wait=0;
        if(result==DefibNetwork.MISS) feedback=15;
        if(result==DefibNetwork.DONE) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace("block.note_block.pling")),1.2f,.8f));
    }
    private boolean finished() {return result==DefibNetwork.DONE||result==DefibNetwork.ABORT;}
    private void send(int control) {
        if(ClientPlayNetworking.canSend(DefibNetwork.Input.TYPE))
            ClientPlayNetworking.send(new DefibNetwork.Input(session,control));
    }
    @Override public boolean isPauseScreen() {return false;}
    @Override public void removed() {if(!finished())send(DefibGame.CANCEL);super.removed();}
    @Override public void tick() {
        if(minecraft.player==null || !minecraft.player.isAlive()) {onClose();return;}
        if(finished())return;
        ticks++; if(wait>0)wait--; if(feedback>0)feedback--;
    }
    private int x() {return (width-WIDTH)/2;}
    private int y() {return (height-HEIGHT)/2;}
    private boolean in(int px,int py,int bw,int bh,MouseButtonEvent e) {
        double mx=e.x()-x(), my=e.y()-y(); return mx>=px && mx<px+bw && my>=py && my<py+bh;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(event.button()!=1)return false;
        if(finished()) {if(in(70,111,98,24,event))onClose();return true;}
        if(pads<2) {
            for(int i=0;i<4;i++)if(in(29+(i%2)*106,60+(i/2)*24,74,20,event)) act(i);
        } else if(!automatic && in(65,111,108,24,event))act(DefibGame.CONFIRM);
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int key=event.key();
        if(finished()&&key==257) {onClose();return true;}
        if(pads<2&&key>=49&&key<=52) {act(key-49);return true;}
        if(!automatic&&pads==2&&(key==32||key==257)) {act(DefibGame.CONFIRM);return true;}
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
        g.fill(px,py,px+w,py+h,0xFF26373B);
        g.outline(px,py,w,h,color);
        g.centeredText(font,label,px+w/2,py+(h-8)/2,color);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        extractTransparentBackground(g);
        g.pose().pushMatrix();g.pose().translate(x(),y());
        g.fill(0,0,WIDTH,HEIGHT,0xFF172327);
        g.outline(0,0,WIDTH,HEIGHT,0xFF6C8D84);
        g.fill(1,1,WIDTH-1,28,0xFF334844);
        g.item(new ItemStack(automatic?MedicalItems.AED:MedicalItems.MANUAL_DEFIBRILLATOR),7,6);
        center(g,getTitle(),10,0xFFFFF2D6);
        if(finished()) {
            center(g,Component.translatable(KEY+(result==DefibNetwork.DONE?"done":"abort")),62,
                    result==DefibNetwork.DONE?GREEN:RED);
            button(g,70,111,98,24,Component.translatable(KEY+"close").getString(),0xFFACC6C0);
        } else if(pads<2) {
            int desired=DefibGame.port(layout,pads);
            center(g,Component.translatable(KEY+"pad",String.valueOf((char)('A'+pads)),SYMBOLS[desired]),39,0xFFFFE0A4);
            for(int i=0;i<4;i++) {
                int color=i/2<pads && i==DefibGame.port(layout,i/2)?GREEN:0xFFACC6C0;
                button(g,29+(i%2)*106,60+(i/2)*24,74,20,SYMBOLS[i]+"   ["+(i+1)+"]",color);
            }
            center(g,Component.translatable(KEY+(feedback>0?"miss_pad":"keys")),119,feedback>0?RED:0xFFACBDB4);
        } else if(automatic) {
            center(g,Component.translatable(KEY+"scan"),58,0xFFFFE0A4);
            g.fill(29,84,209,97,0xFF304545);
            g.fill(29,84,29+180*Math.min(DefibGame.SCAN,ticks)/DefibGame.SCAN,97,GREEN);
            center(g,Component.translatable(KEY+"automatic_fire"),111,0xFFACBDB4);
        } else {
            int phase=Math.max(0,ticks-4)%DefibGame.CYCLE;
            boolean window=phase>=12&&phase<=19;
            center(g,Component.translatable(KEY+"calibrate",locks),40,0xFFFFE0A4);
            g.fill(29,63,209,76,0xFF304545);
            g.fill(29+180*12/DefibGame.CYCLE,63,29+180*20/DefibGame.CYCLE,76,0xFF327D59);
            int mark=29+179*phase/(DefibGame.CYCLE-1);
            g.fill(mark,60,mark+2,79,0xFFFFFFFF);
            center(g,Component.translatable(KEY+(feedback>0?"miss_beat":window?"now":"wait")),88,
                    feedback>0?RED:window?GREEN:0xFFACBDB4);
            button(g,65,111,108,24,Component.translatable(KEY+"lock").getString(),window?GREEN:0xFFACC6C0);
        }
        g.pose().popMatrix();
    }
}
