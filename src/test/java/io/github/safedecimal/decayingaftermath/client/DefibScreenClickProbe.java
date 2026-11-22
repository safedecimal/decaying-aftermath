package io.github.safedecimal.decayingaftermath.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.safedecimal.decayingaftermath.medical.DefibGame;
import io.github.safedecimal.decayingaftermath.network.DefibNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.sdl.SDLMouse;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** TEST-ONLY fixture executing the real screen's SDL click/drag/release methods without a GPU. */
public final class DefibScreenClickProbe {
    private static final Unsafe UNSAFE;
    static {
        try {var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);UNSAFE=(Unsafe)f.get(null);}
        catch(Exception error) {throw new ExceptionInInitializerError(error);}
    }
    static void set(Object target,String name,Object value)throws Exception {
        Class<?> type=target.getClass();Field f=null;
        while(type!=null) {try {f=type.getDeclaredField(name);break;}catch(NoSuchFieldException absent){type=type.getSuperclass();}}
        if(f==null)throw new NoSuchFieldException(name);f.setAccessible(true);f.set(target,value);
    }
    static Object get(Object target,String name)throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static DefibScreen screen(boolean automatic,List<DefibNetwork.Input> sent)throws Exception {
        var screen=(DefibScreen)UNSAFE.allocateInstance(DefibScreen.class);
        set(screen,"session",17);set(screen,"automatic",automatic);set(screen,"scale",1f);set(screen,"held",-1);
        set(screen,"hand",new DefibHandMotion());set(screen,"pad0X",0f);set(screen,"pad0Y",0f);set(screen,"pad1X",300f);set(screen,"pad1Y",300f);
        set(screen,"state",new DefibNetwork.State(17,DefibNetwork.PROGRESS,automatic,automatic?0:2,-1,100,100,0,0,100,1,0,0,300,300,72,1,100,50,150,false));
        try {set(screen,"inputSender",(Consumer<DefibNetwork.Input>)sent::add);}catch(NoSuchFieldException oldVersion){}
        return screen;
    }
    static MouseButtonEvent event(double x,double y,int button) {return new MouseButtonEvent(128+x*.25,128-y*.25,new MouseButtonInfo(button,0));}
    static void position(DefibScreen screen,double x,double y)throws Exception {
        var hand=get(screen,"hand");set(hand,"x",x);set(hand,"y",y);set(hand,"previousX",x);set(hand,"previousY",y);set(hand,"vx",0d);set(hand,"vy",0d);
    }
    static boolean closingAttempt(DefibScreen screen,MouseButtonEvent event)throws Exception {
        try {screen.mouseClicked(event,false);return false;}
        catch(NullPointerException unavailableWindow) {
            for(var frame:unavailableWindow.getStackTrace())if(frame.getClassName().equals(DefibScreen.class.getName())&&frame.getMethodName().equals("onClose"))return true;
            throw unavailableWindow;
        }
    }
    static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
    static final class Graphics extends GuiGraphicsExtractor {
        int faces,edges,blits;
        private Graphics(){super(null,null,0,0);}
        static Graphics create()throws Exception {
            var g=(Graphics)UNSAFE.allocateInstance(Graphics.class);set(g,"pose",new Matrix3x2fStack(32));return g;
        }
        @Override public void fill(int x0,int y0,int x1,int y1,int color) {
            if(color==CuMedicalHand.HAND_FACE)faces++;else edges++;
        }
        @Override public void blit(RenderPipeline p,Identifier id,int x,int y,float u,float v,int w,int h,int sw,int sh,int tw,int th,int color){blits++;}
    }
    public static String exercise()throws Exception {
        var out=new StringBuilder("SDL_LMB="+SDLMouse.SDL_BUTTON_LEFT+";SDL_RMB="+SDLMouse.SDL_BUTTON_RIGHT+"\n");
        var sent=new ArrayList<DefibNetwork.Input>();var aed=screen(true,sent);
        boolean closed=closingAttempt(aed,event(0,0,SDLMouse.SDL_BUTTON_LEFT));out.append("LMB_CLOSE_ATTEMPT=").append(closed).append('\n');
        String classText;
        try(var bytes=DefibScreen.class.getResourceAsStream("DefibScreen.class")){classText=new String(bytes.readAllBytes(),StandardCharsets.ISO_8859_1);}
        boolean legacy=classText.contains("textures/gui/defib/hand_");
        out.append("HAND_RENDERER=").append(legacy?"CU_SPRITE":"SHARED_MINECRAFT_CUBOID").append('\n');
        if(closed)return out.toString();
        require(!legacy,"Legacy paw sprite is still referenced");
        require((int)get(aed,"held")==0&&sent.getLast().control()==DefibGame.PAD_0,"SDL LMB did not grab the AED electrode");
        var drag=event(-296,153,SDLMouse.SDL_BUTTON_LEFT);require(aed.mouseDragged(drag,0,0),"Dragged event not consumed");
        var advance=DefibScreen.class.getDeclaredMethod("advanceHand",float.class,float.class);advance.setAccessible(true);
        for(int i=0;i<80;i++)advance.invoke(aed,0f,1f);
        require(sent.getLast().control()==DefibGame.MOVE,"AED drag did not send MOVE");
        require(Math.hypot(sent.getLast().x()+296,sent.getLast().y()-153)<DefibGame.PAD_RADIUS,"Electrode did not reach placement target");
        require(aed.mouseReleased(drag)&&sent.getLast().control()==DefibGame.RELEASE,"SDL LMB did not release electrode");
        require((int)get(aed,"held")==-1&&!(boolean)get(aed,"pressed"),"Held state remains after release");
        out.append("AED_LMB=PAD_0,MOVE,RELEASE;window_kept=true\n");
        sent.clear();var manual=screen(false,sent);position(manual,-225,-174);
        require(manual.mouseClicked(event(-225,-174,SDLMouse.SDL_BUTTON_LEFT),false),"Manual click rejected");
        require(sent.getLast().control()==DefibGame.DIAL,"Manual dial not grabbed");
        var turn=event(-211.9764,-247.8606,SDLMouse.SDL_BUTTON_LEFT);manual.mouseDragged(turn,0,0);
        for(int i=0;i<80;i++)advance.invoke(manual,0f,1f);
        require(sent.getLast().control()==DefibGame.MOVE,"Manual dial did not move");
        require(manual.mouseReleased(turn)&&sent.getLast().control()==DefibGame.RELEASE,"Manual dial did not release");
        require(Math.abs((float)get(manual,"dialCharge")-200)<2,"Manual dial did not select 200");
        position(manual,8,-174);manual.mouseClicked(event(8,-174,SDLMouse.SDL_BUTTON_LEFT),false);
        require(sent.getLast().control()==DefibGame.CHARGE,"Charge button not clicked");manual.mouseReleased(event(8,-174,SDLMouse.SDL_BUTTON_LEFT));
        position(manual,234.3,-174);manual.mouseClicked(event(234.3,-174,SDLMouse.SDL_BUTTON_LEFT),false);
        require(sent.getLast().control()==DefibGame.SHOCK,"Shock button not clicked");
        out.append("MANUAL_LMB=DIAL,MOVE,RELEASE,CHARGE,SHOCK;window_kept=true\n");
        for(boolean automatic:new boolean[]{true,false}) {
            var aborted=screen(automatic,new ArrayList<>());set(aborted,"aborted",true);
            require(!closingAttempt(aborted,event(0,0,SDLMouse.SDL_BUTTON_LEFT)),"Aborted session closed on LMB");
            require(closingAttempt(screen(automatic,new ArrayList<>()),event(0,0,SDLMouse.SDL_BUTTON_RIGHT)),"Explicit RMB exit was lost");
        }
        out.append("ABORTED_LMB=window_kept;RMB=explicit_exit\n");
        var draw=DefibScreen.class.getDeclaredMethod("extractHand",GuiGraphicsExtractor.class,double.class,double.class,float.class,boolean.class,boolean.class);
        draw.setAccessible(true);
        for(boolean left:new boolean[]{false,true})for(boolean grasp:new boolean[]{false,true}) {
            var g=Graphics.create();draw.invoke(null,g,128d,128d,.2f,left,grasp);
            require(g.blits==0&&g.faces>0&&g.edges>0,"Hand is not the shared cuboid renderer");
            require(g.pose().equals(new org.joml.Matrix3x2f()),"Hand transform not restored");
        }
        out.append("HAND=CuMedicalHand;36x108x9;no_sprite_blits;left_and_right=true\n");
        return out.toString();
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!args[0].equals("SDL_LMB"))throw new IllegalArgumentException("Expected SDL_LMB");
        System.out.print(exercise());
    }
}
