package io.github.safedecimal.decayingaftermath.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** One Steve-proportioned arm cuboid: black faces and white edges, no fingers or player skin. */
public final class CuMedicalHand {
    public static final int WIDTH=36,HEIGHT=108,DEPTH=9;
    private static final int BLACK=0xFF000000,WHITE=0xFFEAEAEA;
    // Center of the palm/end face, including the cuboid's projected depth.
    static final int GRIP_X=(WIDTH+DEPTH)/2, GRIP_Y=DEPTH/2;
    static final int HAND_FACE=0x88000000, HAND_EDGE=0xB8EAEAEA;
    private CuMedicalHand() {}
    public static void extract(GuiGraphicsExtractor g,double x,double y,float rotation,boolean grasp) {
        g.pose().pushMatrix();g.pose().translate((float)x,(float)y);g.pose().rotate(rotation);
        // Minecraft's 4x12 arm ratio. Holding changes the tool interaction, not the arm's shape.
        cube(g,-GRIP_X,-GRIP_Y,WIDTH,HEIGHT,DEPTH,HAND_FACE,grasp?0xC8A9F0C6:HAND_EDGE);
        g.pose().popMatrix();
    }
    public static void cube(GuiGraphicsExtractor g,int x,int y,int w,int h,int depth) {
        cube(g,x,y,w,h,depth,BLACK,WHITE);
    }
    public static void cube(GuiGraphicsExtractor g,int x,int y,int w,int h,int depth,int face,int edge) {
        // Fill the six-sided silhouette, not its rectangular bounding box.
        // The old bounding rectangle left black tabs outside the two slanted corners.
        g.fill(x,y,x+w,y+h,face);
        if(depth>0) {
            if(h>depth)g.fill(x+w,y+depth,x+w+depth,y+h,face);
            for(int row=1;row<depth;row++)g.fill(x+w,y+row,x+w+row,y+row+1,face);
            for(int row=0;row<depth;row++)g.fill(x+row,y+h+row,x+w+depth,y+h+row+1,face);
        }
        line(g,x,y,x+w,y,edge);line(g,x+w,y,x+w,y+h,edge);line(g,x+w,y+h,x,y+h,edge);line(g,x,y+h,x,y,edge);
        line(g,x+w,y,x+w+depth,y+depth,edge);line(g,x+w+depth,y+depth,x+w+depth,y+h+depth,edge);
        line(g,x+w+depth,y+h+depth,x+depth,y+h+depth,edge);line(g,x+depth,y+h+depth,x,y+h,edge);
        line(g,x+w,y+h,x+w+depth,y+h+depth,edge);
    }
    public static void line(GuiGraphicsExtractor g,int x0,int y0,int x1,int y1) {
        line(g,x0,y0,x1,y1,WHITE);
    }
    private static void line(GuiGraphicsExtractor g,int x0,int y0,int x1,int y1,int color) {
        int dx=Math.abs(x1-x0),sx=x0<x1?1:-1,dy=-Math.abs(y1-y0),sy=y0<y1?1:-1,err=dx+dy;
        while(true) {g.fill(x0,y0,x0+1,y0+1,color);if(x0==x1&&y0==y1)break;int e=err*2;if(e>=dy){err+=dy;x0+=sx;}if(e<=dx){err+=dx;y0+=sy;}}
    }
}
