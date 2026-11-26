package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import net.minecraft.util.FormattedCharSequence;

/** CU's enabled ComputerBack monitor: original gray/green frame, live name and pulse. */
public final class MedicalPanelRenderer {
    private static final Identifier PANEL = id("textures/gui/medical/native/panel.png");
    private static final Identifier COMPACT = id("textures/gui/medical/compact.png");
    private static final Identifier FONT = id("textures/font/monitor.png");
    private static final EcgTrace TRACE = new EcgTrace();
    private static final int ECG_COLOR = 0x002BE080;
    private static final int TEXT_COLOR = 0xFF18B93B;
    private static long lastDrawNanos;

    private MedicalPanelRenderer() {
    }

    public static void extract(GuiGraphicsExtractor graphics, MedicalPanelLayout.Box box) {
        if (!MedicalClient.isMedicalPanelOpen(Minecraft.getInstance())) {
            lastDrawNanos = 0;
            return;
        }
        long now = System.nanoTime();
        if (lastDrawNanos != 0) {
            TRACE.follow((now - lastDrawNanos) / 1_000_000_000.0,
                    PulseState.bpmExact(), MedicalClient.beatPhase());
        }
        lastDrawNanos = now;
        graphics.pose().pushMatrix();
        // Uniformly enlarge the original CU artwork, glyphs, icons and ECG together.
        graphics.pose().translate(box.x(), box.y());
        graphics.pose().scale(box.scale(), box.scale());
        if (box.compact()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, COMPACT, 0, 0, 0f, 0f, 133, 23, 133, 23);
            text(graphics, Minecraft.getInstance().getUser().getName(), 6, 4);
            text(graphics, Integer.toString(PulseState.bpm()), 16, 12);
        } else {
            extractMonitor(graphics, ClientMedicalState.snapshot(), Minecraft.getInstance().getUser().getName(), PulseState.bpm());
            trace(graphics, CuMedicalGeometry.ECG.x(), CuMedicalGeometry.ECG.y());
        }
        graphics.pose().popMatrix();
    }

    static void extractMonitor(GuiGraphicsExtractor graphics,
            io.github.safedecimal.decayingaftermath.medical.BodySnapshot body, String name, int bpm) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, 0, 0, 0f, 0f, 133, 196, 133, 196);
        text(graphics, name, 6, 4);
        String firmware = "UNI-HEALTH v2.08";
        text(graphics, firmware, 3 + (128 - MedicalPanelGlyphs.width(firmware)) / 2, 24);
        extractReadouts(graphics, body, bpm);
        if(body.racingHeart()) text(graphics,"RHYTHM ALERT",6,14,0xFFFFA763);
    }

    static void extractReadouts(GuiGraphicsExtractor graphics,
            io.github.safedecimal.decayingaftermath.medical.BodySnapshot body) {
        extractReadouts(graphics, body, Math.round(body.bpm()));
    }

    private static void extractReadouts(GuiGraphicsExtractor graphics,
            io.github.safedecimal.decayingaftermath.medical.BodySnapshot body, int bpm) {
        for (var sprite : CuMedicalGeometry.SPRITES) {
            float amount = CuMedicalMonitor.amount(body, sprite.field());
            if (amount <= 0) continue;
            var r = sprite.box();
            int visible = Math.round((sprite.vertical() ? r.height() : r.width()) * amount);
            if (visible <= 0) continue;
            boolean clipped = amount < 1;
            if (clipped) {
                // Leave an empty margin on the non-fill axis: integer GUI scissors otherwise
                // collapse CU's one-source-pixel bars at small GUI sizes. The sprite itself
                // still supplies exact native bounds; no extra pixels or enlarged fill are drawn.
                if (sprite.vertical()) graphics.enableScissor(r.x()-1, r.y()+r.height()-visible, r.x()+r.width()+1, r.y()+r.height()+1);
                else graphics.enableScissor(r.x(), r.y()-1, r.x()+visible, r.y()+r.height()+1);
            }
            // Full native Sprite rectangle; the scissor only clips its fill, never stretches it.
            graphics.blit(RenderPipelines.GUI_TEXTURED, id("textures/gui/medical/native/"+sprite.name()+".png"),
                    r.x(), r.y(), 0f, 0f, r.width(), r.height(), r.width(), r.height());
            if (clipped) graphics.disableScissor();
        }
        var external=CuMedicalMonitor.externalBleedSource(body);
        if(external!=null)MedicalStatusIcons.drawBleeding(graphics,external,18,65,8,0xFFFF5555);
        double seconds = System.nanoTime() / 1_000_000_000.0;
        for (var readout : CuMedicalGeometry.READOUTS) {
            String value = CuMedicalMonitor.value(body, readout.field(), bpm);
            var r = readout.box();
            int x = readout.alignment() == 4 ? r.x()+r.width()-MedicalPanelGlyphs.width(value) : r.x();
            // Native WoundView.FlashText: green normally, red/blinking for critical readouts.
            int color = CuMedicalMonitor.critical(body,readout.field())
                    ? (Math.sin(seconds*20)>0 ? 0xFFFF0008 : 0x00FF0008) : TEXT_COLOR;
            text(graphics, value, x, r.y()+(readout.field().equals("TempText")?-2:0), color);
        }
    }

    public static void extractTooltip(GuiGraphicsExtractor graphics, MedicalPanelLayout.Box box,
            int mouseX, int mouseY) {
        if (box.compact() || !MedicalClient.isMedicalPanelOpen(Minecraft.getInstance())) return;
        var body = ClientMedicalState.snapshot();
        String field = CuMedicalMonitor.hover(body, box.localX(mouseX), box.localY(mouseY));
        if (field == null) return;
        String title = CuMedicalMonitor.label(field);
        String value = CuMedicalMonitor.tooltipValue(body,field,PulseState.bpm());
        // Explicit Russian tooltips, including when the game's language is English.
        // Use the native Unicode font, never CU's ASCII-only monitor atlas.
        var font = Minecraft.getInstance().font;
        var lines = new ArrayList<FormattedCharSequence>();
        lines.add(Component.literal(title).withColor(0x2BE080).getVisualOrderText());
        lines.add(Component.literal(value).getVisualOrderText());
        if(field.equals("BleedText"))for(String flow:CuMedicalMonitor.bleedingDetails(body))
            lines.add(Component.literal(flow).getVisualOrderText());
        graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    public static void text(GuiGraphicsExtractor graphics, String value, int x, int y) {
        text(graphics,value,x,y,TEXT_COLOR);
    }

    private static void text(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
        // CU's bitmap atlas is ASCII-only. Native Minecraft font renders Cyrillic without '?' glyphs.
        if(value.chars().anyMatch(c->c<32||(c>=127&&c!='°'))) {
            graphics.text(Minecraft.getInstance().font,value,x,y,color,false);return;
        }
        // Keep original I-pixel-u pixels and advances for supported ASCII text.
        for (int index = 0; index < value.length(); index++) {
            if(value.charAt(index)=='°') {
                graphics.fill(x,y+1,x+3,y+2,color);graphics.fill(x,y+3,x+3,y+4,color);
                graphics.fill(x,y+2,x+1,y+3,color);graphics.fill(x+2,y+2,x+3,y+3,color);
                x+=MedicalPanelGlyphs.advance('°');continue;
            }
            int glyph = MedicalPanelGlyphs.index(value.charAt(index));
            graphics.blit(RenderPipelines.GUI_TEXTURED, FONT, x, y,
                    (glyph % 16) * 8f, (glyph / 16) * 10f, 8, 10, 128, 60, color);
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
