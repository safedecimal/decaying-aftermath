package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Pulse is the only stat: no HP, brain, oxygen, burden, blood pressure or placeholder rows. */
public final class MedicalPanelRenderer {
    private static final Identifier SCREEN = id("textures/gui/medical/screen.png");
    private static final Identifier HEART = id("textures/gui/medical/heart.png");
    private static final Identifier FRAME = id("medical/frame");
    private static final Style MONITOR = Style.EMPTY.withFont(new FontDescription.Resource(id("monitor")));
    private static final EcgTrace TRACE = new EcgTrace();
    private static final int GREEN = 0xFF2BE080;
    private static final int TEXT = 0xFF18B93B;
    private static long lastDrawNanos;

    private MedicalPanelRenderer() {
    }

    public static void extract(GuiGraphicsExtractor graphics, MedicalPanelLayout.Box box) {
        long now = System.nanoTime();
        if (lastDrawNanos != 0) {
            TRACE.advance((now - lastDrawNanos) / 1_000_000_000.0, PulseState.bpmExact());
        }
        lastDrawNanos = now;
        graphics.pose().pushMatrix();
        graphics.pose().translate(box.x(), box.y());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, 0, 0, box.width(), box.height(), 0xFF274234);
        graphics.fill(5, 5, box.width() - 5, box.height() - 5, 0xFF020A05);
        if (box.compact()) {
            extractCompact(graphics, box.width());
        } else {
            extractFull(graphics);
        }
        graphics.pose().popMatrix();
    }

    private static void extractFull(GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, SCREEN, 6, 8, 0f, 0f, 128, 66, 128, 66);
        text(graphics, Component.literal("UNI-HEALTH v2.07"), 10, 9, TEXT);
        graphics.fill(10, 23, 130, 24, 0x5534A15C);
        heart(graphics, 10, 29, 0.5f);
        text(graphics, Component.translatable("decaying_aftermath.pulse"), 32, 27, GREEN);
        number(graphics, 83, 26, 2f);
        text(graphics, Component.literal("BPM"), 110, 35, TEXT);
        trace(graphics, 10, 51, EcgTrace.WIDTH, EcgTrace.HEIGHT);
    }

    private static void extractCompact(GuiGraphicsExtractor graphics, int width) {
        heart(graphics, 7, 7, 0.375f);
        text(graphics, Component.translatable("decaying_aftermath.pulse"), 23, 8, GREEN);
        number(graphics, 62, 5, 1.5f);
        text(graphics, Component.literal("BPM"), 86, 10, TEXT);
        trace(graphics, 111, 6, Math.max(20, width - 119), 14);
    }

    private static void heart(GuiGraphicsExtractor graphics, int x, int y, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, HEART, 0, 0, 0f, 0f, 32, 32, 32, 32, GREEN);
        graphics.pose().popMatrix();
    }

    private static void number(GuiGraphicsExtractor graphics, int x, int y, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        text(graphics, Component.literal(Integer.toString(PulseState.bpm())), 0, 0, GREEN);
        graphics.pose().popMatrix();
    }

    private static void text(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(Minecraft.getInstance().font, text.copy().withStyle(MONITOR), x, y, color, false);
    }

    private static void trace(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        for (int column = 0; column < width; column++) {
            int age = (width - 1 - column) * (EcgTrace.WIDTH - 1) / Math.max(1, width - 1);
            int nextAge = Math.min(EcgTrace.WIDTH - 1, age + 1);
            int first = TRACE.sample(age) * (height - 1) / (EcgTrace.HEIGHT - 1);
            int second = TRACE.sample(nextAge) * (height - 1) / (EcgTrace.HEIGHT - 1);
            int alpha = 55 + (width - age * width / EcgTrace.WIDTH) * 190 / width;
            int color = (Math.clamp(alpha, 30, 245) << 24) | (GREEN & 0xFFFFFF);
            graphics.fill(x + column, y + Math.min(first, second), x + column + 1,
                    y + Math.max(first, second) + 1, color);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(DecayingAftermath.MOD_ID, path);
    }
}
