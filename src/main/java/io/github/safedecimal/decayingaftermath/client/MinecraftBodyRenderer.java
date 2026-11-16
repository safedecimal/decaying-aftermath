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
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) face.u(), (float) face.v(),
                w, h, face.width(), face.height(), 64, 64, tint);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) face.outerU(), (float) face.outerV(),
                w, h, face.width(), face.height(), 64, 64, tint);
    }
    public static void outline(GuiGraphicsExtractor g, MinecraftBodyLayout.Box b, int color) {
        g.fill(b.x(), b.y(), b.x()+b.width(), b.y()+1, color);
        g.fill(b.x(), b.y()+b.height()-1, b.x()+b.width(), b.y()+b.height(), color);
        g.fill(b.x(), b.y(), b.x()+1, b.y()+b.height(), color);
        g.fill(b.x()+b.width()-1, b.y(), b.x()+b.width(), b.y()+b.height(), color);
    }
}
