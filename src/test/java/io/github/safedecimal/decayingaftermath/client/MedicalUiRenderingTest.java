package io.github.safedecimal.decayingaftermath.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.awt.geom.Path2D;

import static org.junit.jupiter.api.Assertions.*;

/** Native clipping/bounds/blit semantics without creating Minecraft, a window or a GPU. */
class MedicalUiRenderingTest {
    private record Blit(int width, int height, int sourceWidth, int sourceHeight,
                        int textureWidth, int textureHeight, float u, float v) {}

    private static class Graphics extends GuiGraphicsExtractor {
        List<ColoredRectangleRenderState> fills;
        List<Blit> blits;

        private Graphics() { super(null, null, 0, 0); } // Never invoked by this TEST-ONLY fixture.

        static Graphics create(int width, int height) throws Exception {
            // Bypass only the game-dependent constructor; production code never uses Unsafe.
            var access = Unsafe.class.getDeclaredField("theUnsafe");
            access.setAccessible(true);
            var graphics = (Graphics) ((Unsafe) access.get(null)).allocateInstance(Graphics.class);
            set(graphics, "pose", new Matrix3x2fStack(32));
            var constructor = ScissorStack.class.getDeclaredConstructor(ScreenRectangle.class);
            constructor.setAccessible(true);
            set(graphics, "scissorStack", constructor.newInstance(new ScreenRectangle(0, 0, width, height)));
            graphics.fills = new ArrayList<>();
            graphics.blits = new ArrayList<>();
            return graphics;
        }

        private static void set(Graphics graphics, String name, Object value) throws Exception {
            var field = GuiGraphicsExtractor.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(graphics, value);
        }

        @Override public void fill(int x0, int y0, int x1, int y1, int color) {
            // This native constructor computes transformed bounds AND intersects the scissor.
            fills.add(new ColoredRectangleRenderState(null, null, new Matrix3x2f(pose()),
                    x0, y0, x1, y1, color, color, scissorStack.peek()));
        }

        @Override public void blit(RenderPipeline pipeline, Identifier id, int x, int y, float u, float v,
                                   int width, int height, int sourceWidth, int sourceHeight,
                                   int textureWidth, int textureHeight, int color) {
            // Shorter native overloads forward here with a destination-sized source region.
            blits.add(new Blit(width, height, sourceWidth, sourceHeight, textureWidth, textureHeight, u, v));
        }

        long visible(int color) {
            return fills.stream().filter(r -> r.col1() == color && r.bounds() != null
                    && r.bounds().width() > 0 && r.bounds().height() > 0).count();
        }
    }

    private List<MedicalStatusIcons.Icon> icons() {
        var result = new ArrayList<MedicalStatusIcons.Icon>();
        for (boolean covered : new boolean[]{false, true}) {
            for (float bleeding : new float[]{.01f, .05f, .12f, .3f}) {
                result.add(MedicalStatusIcons.bleeding(
                        new BodySnapshot.PartSnapshot(1, bleeding, 0, false, covered, false, false)));
            }
        }
        result.add(new MedicalStatusIcons.Icon("fracture", 21, 32));
        return result;
    }

    @Test void nativeScissorAppliesCanvasTransformOnlyOnce() throws Exception {
        var graphics = Graphics.create(640, 420);
        graphics.pose().translate(192, 82).scale(.5f, .5f);
        var expected = new ScreenRectangle(-10, 14, 276, 219).transformAxisAligned(graphics.pose());
        ProcedureVisuals.beginWorkArea(graphics);
        assertEquals(expected, graphics.scissorStack.peek());
    }

    @Test void armAndWhiteEdgesRemainVisibleAcrossGuiScalesAndPositions() throws Exception {
        int[][] screens = {{640, 420}, {854, 480}, {320, 240}, {1280, 720}};
        int[][] points = {{128, 128}, {128, 32}, {32, 128}, {224, 128}, {128, 210}};
        for (var screen : screens) for (var point : points) for (boolean syringe : new boolean[]{false, true}) {
            var graphics = Graphics.create(screen[0], screen[1]);
            float scale = Math.max(.25f, Math.min(1f, Math.min((screen[0] - 32f) / 280, (screen[1] - 64f) / 286)));
            int ox = (int) ((screen[0] - 256 * scale) / 2), oy = (int) ((screen[1] - 256 * scale) / 2);
            graphics.pose().translate(ox, oy).scale(scale, scale);
            ProcedureVisuals.beginWorkArea(graphics);
            CuMedicalHand.extract(graphics, point[0], point[1], syringe ? (float) Math.toRadians(65) : 0, false);
            String context = screen[0] + "x" + screen[1] + " at " + point[0] + "," + point[1] + ", syringe=" + syringe;
            assertTrue(graphics.visible(0xFF000000) > 0, context);
            assertTrue(graphics.visible(0xFFEAEAEA) > 0, context);
        }
    }

    @Test void handHasOneBlackBlockAndNoExtraAnatomicalPartsOrSkin() throws Exception {
        var graphics = Graphics.create(640, 420);
        ProcedureVisuals.beginWorkArea(graphics);
        CuMedicalHand.extract(graphics, 128, 128, 0, true);
        // Tessellated faces need multiple rectangles; draw-call count is not anatomy.
        assertTrue(graphics.fills.stream().anyMatch(r -> r.col1() == 0xFF000000));
        assertTrue(graphics.fills.stream().allMatch(r -> r.col1() == 0xFF000000 || r.col1() == 0xFFEAEAEA));
        assertNoBlackOverflow(graphics, -CuMedicalHand.WIDTH / 2, -CuMedicalHand.WIDTH / 2,
                CuMedicalHand.WIDTH, CuMedicalHand.HEIGHT, CuMedicalHand.DEPTH);
        assertTrue(graphics.blits.isEmpty());
    }

    private void assertNoBlackOverflow(Graphics graphics, int x, int y, int width, int height, int depth) {
        var pixels = new HashMap<Long, Integer>();
        for (var fill : graphics.fills) {
            for (int py = fill.y0(); py < fill.y1(); py++) for (int px = fill.x0(); px < fill.x1(); px++)
                pixels.put(((long) px << 32) | (py & 0xffffffffL), fill.col1());
        }
        var silhouette = new Path2D.Double();
        silhouette.moveTo(x, y); silhouette.lineTo(x + width, y);
        silhouette.lineTo(x + width + depth, y + depth);
        silhouette.lineTo(x + width + depth, y + height + depth);
        silhouette.lineTo(x + depth, y + height + depth); silhouette.lineTo(x, y + height); silhouette.closePath();
        long outside = pixels.entrySet().stream().filter(p -> p.getValue() == 0xFF000000)
                .filter(p -> !silhouette.contains((int) (p.getKey() >> 32) + .5, (int) (long) p.getKey() + .5)).count();
        assertEquals(0, outside, "Black fill must not protrude past the slanted cuboid silhouette");
    }

    @Test void cuboidFacesDoNotLeaveFlatBlackCornerTabs() throws Exception {
        var graphics = Graphics.create(640, 420);
        CuMedicalHand.cube(graphics, 0, 0, 24, 72, 6);
        assertNoBlackOverflow(graphics, 0, 0, 24, 72, 6);
    }

    @Test void largerHandRetainsSteveFourByTwelveProportions() {
        assertEquals(36, CuMedicalHand.WIDTH); assertEquals(108, CuMedicalHand.HEIGHT);
        assertEquals(9, CuMedicalHand.DEPTH); assertEquals(3 * CuMedicalHand.WIDTH, CuMedicalHand.HEIGHT);
    }

    private void assertWholeSprites(int size) throws Exception {
        var graphics = Graphics.create(640, 420);
        for (var icon : icons()) {
            graphics.blits.clear();
            MedicalStatusIcons.draw(graphics, icon, 128, 128, size, 0xFFF08888);
            assertEquals(1, graphics.blits.size());
            var blit = graphics.blits.getFirst();
            assertEquals(icon.width(), blit.sourceWidth(), icon.name());
            assertEquals(icon.height(), blit.sourceHeight(), icon.name());
            assertEquals(blit.textureWidth(), blit.sourceWidth(), icon.name());
            assertEquals(blit.textureHeight(), blit.sourceHeight(), icon.name());
            assertEquals(0, blit.u()); assertEquals(0, blit.v());
            assertEquals(size, Math.max(blit.width(), blit.height()), icon.name());
        }
    }

    @Test void inventoryIconsDownscaleWholeTextureNotTopLeftFragment() throws Exception { assertWholeSprites(13); }
    @Test void procedureIconsScaleWholeTextureAt32Pixels() throws Exception { assertWholeSprites(32); }

    @Test void sourceDimensionsMatchAllNineUnmodifiedCuPngs() throws Exception {
        for (var icon : icons()) {
            try (var input = getClass().getResourceAsStream("/assets/decaying_aftermath/textures/gui/status/" + icon.name() + ".png")) {
                assertNotNull(input, icon.name());
                var image = ImageIO.read(input);
                assertEquals(icon.width(), image.getWidth(), icon.name());
                assertEquals(icon.height(), image.getHeight(), icon.name());
            }
        }
    }

    @Test void leavingHandPassRestoresPreviousScissor() throws Exception {
        var graphics = Graphics.create(640, 420);
        var previous = graphics.scissorStack.peek();
        ProcedureVisuals.beginWorkArea(graphics);
        CuMedicalHand.extract(graphics, 128, 128, 0, false);
        graphics.disableScissor();
        assertEquals(previous, graphics.scissorStack.peek());
    }
}
