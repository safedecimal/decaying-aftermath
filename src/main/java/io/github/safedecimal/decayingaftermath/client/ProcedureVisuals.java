package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** CU native-pixel frame, black work surface and white procedural wire geometry. */
public final class ProcedureVisuals {
    private ProcedureVisuals() {}
    public static void frame(GuiGraphicsExtractor g) {
        MedicalClient.blit(g,"procedure_frame",-12,-14,280,286,280,286,0xFFFFFFFF);
        g.fill(7,12,249,13,0xFF454B47);g.fill(7,234,249,235,0xFF454B47);
    }
    public static void beginWorkArea(GuiGraphicsExtractor g) {
        // 26.3 enableScissor applies the current pose itself: these must stay canvas-local.
        g.enableScissor(-10,14,266,233);
    }
    public static void part(GuiGraphicsExtractor g,BodyPart part,int x,int y,int w,int h) {
        var b=CuProcedureGeometry.part(part,x,y,w,h);
        CuMedicalHand.cube(g,b.x(),b.y(),b.width(),b.height(),5,0xFF171B18,0xFF9CA89E);
    }
    public static void rail(GuiGraphicsExtractor g,int y,float threaded,float tension,boolean active) {
        g.fill(92,y-6,155,y+7,0xFF050505);
        g.fill(94,y-2,151,y+3,active?0xFF8B9490:0xFF454B47);
        CuMedicalHand.cube(g,145,y-6,9,12,2);
        int end=96+Math.round(threaded*50);
        g.fill(96,y-1,end,y+2,0xFFE6E6E6);
        for(int i=0;i<5;i++)if(i/5f<tension)g.fill(103+i*7,y+6,106+i*7,y+8,0xFFBDD1C0);
    }
    public static List<String> lines(String value,int width,ToIntFunction<String> measure) {
        List<String> result=new ArrayList<>();String line="";
        for(String word:value.split(" ")) {
            String next=line.isEmpty()?word:line+" "+word;
            if(!line.isEmpty()&&measure.applyAsInt(next)>width) {result.add(line);line=word;}else line=next;
        }
        if(!line.isEmpty())result.add(line);return result;
    }
}
