package io.github.safedecimal.decayingaftermath.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.PlayerModelType;

/** Player skin arm in 2D/3D for minigames and cursor; keeps schematic cuboid fallback in headless unit tests. */
public final class CuMedicalHand {
    public static final int WIDTH=36,HEIGHT=108,DEPTH=9;
    private static final int BLACK=0xFF000000,WHITE=0xFFEAEAEA;
    // Center of the palm/end face, including the cuboid's projected depth.
    static final int GRIP_X=(WIDTH+DEPTH)/2, GRIP_Y=DEPTH/2;
    static final int HAND_FACE=0x88000000, HAND_EDGE=0xB8EAEAEA;
    private CuMedicalHand() {}
    public static void extract(GuiGraphicsExtractor g,double x,double y,float rotation,boolean grasp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            if (New3dSystem.enabled()) {
                Minigame3dRenderer.draw3dHandAt(g, x, y, rotation, grasp);
                return;
            }
            extractPlayerSkinArm2d(g, mc.player, x, y, rotation, grasp);
            return;
        }
        g.pose().pushMatrix();g.pose().translate((float)x,(float)y);g.pose().rotate(rotation);
        // Minecraft's 4x12 arm ratio. Holding changes the tool interaction, not the arm's shape.
        cube(g,-GRIP_X,-GRIP_Y,WIDTH,HEIGHT,DEPTH,HAND_FACE,grasp?0xC8A9F0C6:HAND_EDGE);
        g.pose().popMatrix();
    }

    private static void extractPlayerSkinArm2d(GuiGraphicsExtractor g, net.minecraft.client.player.LocalPlayer player,
            double x, double y, float rotation, boolean grasp) {
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        var tex = player.getSkin().body().texturePath();
        int u = 44, v = 20, outerU = 44, outerV = 36, sideU = 40;
        int w = slim ? 3 : 4, h = 12;
        int tint = grasp ? 0xFFE6FFF0 : 0xFFFFFFFF;
        int sideTint = grasp ? 0xFFAED0BE : 0xFFC0C0C0;
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().rotate(rotation);
        int bx = -GRIP_X, by = -GRIP_Y;
        // Rotate 180 degrees around the arm's center so fingers/palm sit at the cursor/grip (0, 0) and shoulder extends down.
        g.pose().pushMatrix();
        g.pose().translate(bx + WIDTH / 2.0f, by + HEIGHT / 2.0f);
        g.pose().rotate((float) Math.PI);
        g.pose().translate(-(bx + WIDTH / 2.0f), -(by + HEIGHT / 2.0f));
        g.blit(RenderPipelines.GUI_TEXTURED, tex, bx, by, (float) u, (float) v, WIDTH, HEIGHT, w, h, 64, 64, tint);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, bx, by, (float) outerU, (float) outerV, WIDTH, HEIGHT, w, h, 64, 64, tint);
        g.pose().popMatrix();

        g.pose().pushMatrix();
        g.pose().translate(bx + WIDTH + DEPTH / 2.0f, by + DEPTH / 2.0f + HEIGHT / 2.0f);
        g.pose().rotate((float) Math.PI);
        g.pose().translate(-(bx + WIDTH + DEPTH / 2.0f), -(by + DEPTH / 2.0f + HEIGHT / 2.0f));
        g.blit(RenderPipelines.GUI_TEXTURED, tex, bx + WIDTH, by + DEPTH / 2, (float) sideU, (float) v, DEPTH, HEIGHT, 4, h, 64, 64, sideTint);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, bx + WIDTH, by + DEPTH / 2, (float) sideU, (float) outerV, DEPTH, HEIGHT, 4, h, 64, 64, sideTint);
        g.pose().popMatrix();

        drawHandHitbox(g, grasp);
        g.pose().popMatrix();
    }

    static void drawHandHitbox(GuiGraphicsExtractor g, boolean grasp) {
        int bx = -GRIP_X, by = -GRIP_Y;
        float matScale = Math.max(0.15f, (float) Math.hypot(g.pose().m00(), g.pose().m01()));
        int b = Math.clamp(Math.round(1.0f / matScale), 1, 4);
        int edge = grasp ? 0xC8A9F0C6 : 0x90EAEAEA;
        g.fill(bx, by, bx + WIDTH, by + b, edge);
        g.fill(bx, by + HEIGHT - b, bx + WIDTH, by + HEIGHT, edge);
        g.fill(bx, by, bx + b, by + HEIGHT, edge);
        g.fill(bx + WIDTH - b, by, bx + WIDTH, by + HEIGHT, edge);
        int dot = grasp ? 0xFF90F4B0 : 0xFFFFD88A;
        g.fill(-b, -b, b + 1, b + 1, dot);
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
