package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** CU's enabled ComputerBack monitor: original gray/green frame, live name and pulse. */
public final class MedicalPanelRenderer {
    private static final Identifier PANEL = id("textures/gui/medical/panel.png");
    private static final Identifier COMPACT = id("textures/gui/medical/compact.png");
    private static final Identifier FONT = id("textures/font/monitor.png");
    private static final EcgTrace TRACE = new EcgTrace();
    private static final int ECG_COLOR = 0x002BE080;
    private static final int TEXT_COLOR = 0xFF18B93B;
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
        // Never stretch the source bitmap to fill the available container.
        graphics.pose().translate(box.x() + (box.width() - 133) / 2, box.y());
        if (box.compact()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, COMPACT, 0, 0, 0f, 0f, 133, 23, 133, 23);
            text(graphics, Minecraft.getInstance().getUser().getName(), 6, 4);
            text(graphics, Integer.toString(PulseState.bpm()), 16, 12);
        } else {
            graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, 0, 0, 0f, 0f, 133, 196, 133, 196);
            text(graphics, Minecraft.getInstance().getUser().getName(), 6, 4);
            text(graphics, Integer.toString(PulseState.bpm()), 16, 154);
            trace(graphics, 7, 165);
        }
        graphics.pose().popMatrix();
    }

    private static void text(GuiGraphicsExtractor graphics, String value, int x, int y) {
        // Original I-pixel-u glyph pixels AND variable source advances, no vanilla font.
        for (int index = 0; index < value.length(); index++) {
            int glyph = MedicalPanelGlyphs.index(value.charAt(index));
            graphics.blit(RenderPipelines.GUI_TEXTURED, FONT, x, y,
                    (glyph % 16) * 8f, (glyph / 16) * 10f, 8, 10, 128, 60, TEXT_COLOR);
            x += MedicalPanelGlyphs.advance(value.charAt(index));
        }
    }

    private static void trace(GuiGraphicsExtractor graphics, int x, int y) {
        // The original ECGVisualizer's 120x25 circular sweep and fading pixel halo.
        for (int column = 0; column < EcgTrace.WIDTH; column++) {
            for (int row = 0; row < EcgTrace.HEIGHT; row++) {
                int alpha = TRACE.alpha(column, row);
                if (alpha != 0) {
                    graphics.fill(x + column, y + row, x + column + 1, y + row + 1,
                            (alpha << 24) | ECG_COLOR);
                }
            }
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(DecayingAftermath.MOD_ID, path);
    }
}
