package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.PlayerModelType;

/** Front faces of the current player's actual Minecraft skin, never CU's character silhouette. */
public final class MinecraftBodyRenderer {
    private MinecraftBodyRenderer() {}

    public static boolean slim() {
        var player = Minecraft.getInstance().player;
        return player != null && player.getSkin().model() == PlayerModelType.SLIM;
    }
    public static MinecraftBodyLayout.Box box(BodyPart part) { return MinecraftBodyLayout.box(part, slim()); }

    public static void part(GuiGraphicsExtractor graphics, BodyPart part, int x, int y, int w, int h, int tint) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var face = MinecraftBodyLayout.face(part, slim());
        var texture = player.getSkin().body().texturePath();
        boolean rotated=(part==BodyPart.LEFT_ARM||part==BodyPart.RIGHT_ARM)&&w>h;
        if(rotated) {
            // Rotate native vertical skin faces; stretching the UV would distort the player's arms.
            graphics.pose().pushMatrix();
            if(part==BodyPart.LEFT_ARM) {
                graphics.pose().translate(x,y+h);graphics.pose().rotate((float)(-Math.PI/2));
            } else {
                graphics.pose().translate(x+w,y);graphics.pose().rotate((float)(Math.PI/2));
            }
            int originalWidth=h;h=w;w=originalWidth;x=y=0;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) face.u(), (float) face.v(),
                w, h, face.width(), face.height(), 64, 64, tint);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) face.outerU(), (float) face.outerV(),
                w, h, face.width(), face.height(), 64, 64, tint);
        if(rotated) graphics.pose().popMatrix();
    }
    public static void outline(GuiGraphicsExtractor g, MinecraftBodyLayout.Box b, int color) {
        g.fill(b.x(), b.y(), b.x()+b.width(), b.y()+1, color);
        g.fill(b.x(), b.y()+b.height()-1, b.x()+b.width(), b.y()+b.height(), color);
        g.fill(b.x(), b.y(), b.x()+1, b.y()+b.height(), color);
        g.fill(b.x()+b.width()-1, b.y(), b.x()+b.width(), b.y()+b.height(), color);
    }
}
