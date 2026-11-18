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
                        int textureWidth, int textureHeight, float u, float v,
                        Identifier id,int x,int y,int color,Matrix3x2f transform,ScreenRectangle clip) {}

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
            blits.add(new Blit(width, height, sourceWidth, sourceHeight, textureWidth, textureHeight, u, v,
                    id,x,y,color,new Matrix3x2f(pose()),scissorStack.peek()));
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

    private BodySnapshot recovering(float progress, float blood) {
        var parts=new BodySnapshot.PartSnapshot[6];
        java.util.Arrays.fill(parts,new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false));
        return new BodySnapshot(.3f,blood,1,0,0,80,BodySnapshot.Mode.UNCONSCIOUS,progress,1,1,parts);
    }
    @Test void zeroProgressAndPausedRecoveryStillRenderACompleteVisibleRing() throws Exception {
        for(float blood:new float[]{1,.25f}) {
            var graphics=Graphics.create(640,420);var box=ConsciousnessLayout.at(640,420);
            MedicalClient.extractRecoveryRing(graphics,box,recovering(0,blood),.35);
            assertEquals(1,graphics.blits.size());var ring=graphics.blits.getFirst();
            assertTrue((ring.color()>>>24)>0);assertTrue(ring.id().toString().endsWith("unconscious_ring.png"));
            assertEquals(box.diameter(),ring.width());assertEquals(box.diameter(),ring.height());
            assertEquals(149,ring.sourceWidth());assertEquals(149,ring.sourceHeight());
        }
    }
    @Test void recoveryFillRendersAboveDimRingWithoutClippingAtCommonGuiScales() throws Exception {
        for(int height:new int[]{180,240,420,1080}) {
            int width=height*16/9;var graphics=Graphics.create(width,height);var box=ConsciousnessLayout.at(width,height);
            MedicalClient.extractRecoveryRing(graphics,box,recovering(.5f,1),1);
            assertTrue(graphics.blits.size()>1);var base=graphics.blits.getFirst();
            assertTrue(graphics.blits.getLast().color()>>>24 > base.color()>>>24);
            for(var ring:graphics.blits) {
                assertTrue(ring.x()>=0);assertTrue(ring.y()>=0);
                assertTrue(ring.x()+ring.width()<=width);assertTrue(ring.y()+ring.height()<=height);
            }
            assertTrue(box.y()+box.diameter()+6+9<height,"Countdown below the ring stays on screen");
        }
    }

    @Test void nativeScissorAppliesCanvasTransformOnlyOnce() throws Exception {
        var graphics = Graphics.create(640, 420);
        graphics.pose().translate(192, 82).scale(.5f, .5f);
        var expected = new ScreenRectangle(-10, 14, 276, 219).transformAxisAligned(graphics.pose());
        ProcedureVisuals.beginWorkArea(graphics);
        assertEquals(expected, graphics.scissorStack.peek());
    }

    @Test void monitorDrawsNativeSpritesAndFillScissorsNotTheRejectedTenRowIconAtlas() throws Exception {
        var parts = new BodySnapshot.PartSnapshot[6];
        java.util.Arrays.fill(parts,new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false));
        var body = new BodySnapshot(1,.75f,.8f,.2f,1.25f,72,BodySnapshot.Mode.CONSCIOUS,1,.25f,.4f,parts);
        for (int[] screen : new int[][] {{640,480},{320,240},{240,180}}) {
            var graphics = Graphics.create(screen[0],screen[1]);
            var box = MedicalPanelLayout.forScreen(screen[0],screen[1]);
            graphics.pose().translate(box.x(),box.y()).scale(box.scale(),box.scale());
            MedicalPanelRenderer.extractReadouts(graphics,body);
            var nativeSprites = graphics.blits.stream().filter(b->b.id().toString().contains("/native/")).toList();
            assertEquals(CuMedicalGeometry.SPRITES.stream().filter(s->CuMedicalMonitor.amount(body,s.field())>0).count(),nativeSprites.size());
            for(var sprite:nativeSprites) {
                assertEquals(0,sprite.u());assertEquals(0,sprite.v());
                assertEquals(sprite.textureWidth(),sprite.sourceWidth());
                assertEquals(sprite.textureHeight(),sprite.sourceHeight());
                var geometry=CuMedicalGeometry.SPRITES.stream().filter(s->s.box().x()==sprite.x()
                        &&s.box().y()==sprite.y()&&sprite.id().toString().endsWith("/"+s.name()+".png")).findFirst().orElseThrow();
                if(CuMedicalMonitor.amount(body,geometry.field())<1) {
                    assertNotNull(sprite.clip(),geometry.field());
                    assertTrue(sprite.clip().width()>0&&sprite.clip().height()>0,geometry.field());
                } else assertNull(sprite.clip(),"Full native sprite must not receive a rounded GUI scissor: "+geometry.field());
            }
            assertFalse(graphics.blits.stream().anyMatch(b->b.id().toString().contains("vitals_icons")||b.id().toString().contains("vitals_bar")));
            assertEquals(4,graphics.fills.size(),"Only the four degree-sign edges, no rectangular progress bars");
        }
    }

    @Test void monitorDrawsTheBloodGroupWithNativeGlyphsOnlyWhenKnown() throws Exception {
        var body=new io.github.safedecimal.decayingaftermath.medical.BodyModel().snapshot();
        var graphics=Graphics.create(266,392);
        var box=CuMedicalGeometry.BLOOD_GROUP.box();
        MedicalPanelRenderer.extractReadouts(graphics,body);
        assertFalse(graphics.blits.stream().anyMatch(b->b.y()==box.y()&&b.x()>=box.x()&&b.x()<box.x()+box.width()));

        graphics.blits.clear();
        var group=io.github.safedecimal.decayingaftermath.medical.BloodGroup.AB_POSITIVE;
        MedicalPanelRenderer.extractReadouts(graphics,body.withBloodGroup(group));
        var glyphs=graphics.blits.stream().filter(b->b.y()==box.y()&&b.id().toString().endsWith("/monitor.png")).toList();
        assertEquals(group.label().length(),glyphs.size());
        int x=box.x()+box.width()-MedicalPanelGlyphs.width(group.label());
        for(int i=0;i<glyphs.size();i++) {
            char glyph=group.label().charAt(i);
            int index=MedicalPanelGlyphs.index(glyph);
            assertEquals(x,glyphs.get(i).x());
            assertEquals(index%16*8f,glyphs.get(i).u());
            assertEquals(index/16*10f,glyphs.get(i).v());
            x+=MedicalPanelGlyphs.advance(glyph);
        }
    }

    @Test void renderRealNativeMonitorExtractionToCpuPreviewWithoutMinecraftWindow() throws Exception {
        var parts=new BodySnapshot.PartSnapshot[6];
        java.util.Arrays.fill(parts,new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false));
        var body=new BodySnapshot(1,.75f,.8f,.2f,1.25f,72,BodySnapshot.Mode.CONSCIOUS,1,.75f,.4f,parts);
        var graphics=Graphics.create(266,392);graphics.pose().scale(2,2);
        MedicalPanelRenderer.extractMonitor(graphics,body,"PLAYER",72);
        var canvas=new java.awt.image.BufferedImage(266,392,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var painter=canvas.createGraphics();
        painter.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for(var b:graphics.blits) {
            String path="assets/"+b.id().toString().replace(':','/');
            java.awt.image.BufferedImage texture;
            try(var in=getClass().getClassLoader().getResourceAsStream(path)) { assertNotNull(in,path);texture=ImageIO.read(in); }
            var crop=new java.awt.image.BufferedImage(b.sourceWidth(),b.sourceHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int x=0;x<crop.getWidth();x++) for(int y=0;y<crop.getHeight();y++) {
                int pixel=texture.getRGB((int)b.u()+x,(int)b.v()+y),tint=b.color();
                int alpha=(pixel>>>24)*(tint>>>24)/255;
                int red=((pixel>>>16)&255)*((tint>>>16)&255)/255;
                int green=((pixel>>>8)&255)*((tint>>>8)&255)/255;
                int blue=(pixel&255)*(tint&255)/255;
                crop.setRGB(x,y,(alpha<<24)|(red<<16)|(green<<8)|blue);
            }
            var m=b.transform();
            var transform=new java.awt.geom.AffineTransform(m.m00(),m.m01(),m.m10(),m.m11(),m.m20(),m.m21());
            painter.setTransform(transform);painter.setClip(null);
            if(b.clip()!=null) {
                var c=b.clip();
                painter.setClip(transform.createInverse().createTransformedShape(new java.awt.Rectangle(c.left(),c.top(),c.width(),c.height())));
            }
            painter.drawImage(crop,b.x(),b.y(),b.width(),b.height(),null);
        }
        painter.dispose();
        var path=java.nio.file.Path.of(System.getProperty("user.dir"),"verification","external-fall-0.4.6","PANEL_PREVIEW.png");
        java.nio.file.Files.createDirectories(path.getParent());ImageIO.write(canvas,"PNG",path.toFile());
        assertEquals(266,ImageIO.read(path.toFile()).getWidth());
    }

    @Test void externalBleedBadgeIsExtractedOnMonitorAndOnBodyWithContrast() throws Exception {
        var model=new io.github.safedecimal.decayingaftermath.medical.BodyModel();model.fall(40);var body=model.snapshot();
        var g=Graphics.create(266,392);g.pose().scale(2,2);MedicalPanelRenderer.extractReadouts(g,body);
        var external=g.blits.stream().filter(b->b.id().toString().contains("/status/bleeding_")).toList();
        assertEquals(1,external.size());assertEquals(0xFFFF5555,external.get(0).color());
        assertTrue(external.get(0).width()>0&&external.get(0).height()>0);
        var limb=Graphics.create(260,250);
        MedicalStatusIcons.body(limb,body.part(io.github.safedecimal.decayingaftermath.medical.BodyPart.LEFT_LEG),
                MinecraftBodyLayout.box(io.github.safedecimal.decayingaftermath.medical.BodyPart.LEFT_LEG,false));
        assertTrue(limb.fills.stream().anyMatch(f->f.col1()==0xFF210909&&f.bounds()!=null));
        assertTrue(limb.blits.stream().filter(b->b.id().toString().contains("/bleeding_")).allMatch(b->((b.color()>>>16)&255)>=192));
    }

    @Test void staminaDrawsLeftLungFirstAndBothLungsFaceForwardAtFullEnergy() throws Exception {
        var parts=new BodySnapshot.PartSnapshot[6];
        java.util.Arrays.fill(parts,new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false));
        for(int[] size:new int[][]{{240,180},{320,240},{640,480},{1920,1080}}) {
            var box=MedicalPanelLayout.forScreen(size[0],size[1]);
            for(float energy:new float[]{0,.25f,.5f,.75f,1}) {
                var g=Graphics.create(size[0],size[1]);
                g.pose().translate(box.x(),box.y()).scale(box.scale(),box.scale());
                var body=new BodySnapshot(1,1,1,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,energy,1,parts);
                MedicalPanelRenderer.extractReadouts(g,body);
                var lungs=g.blits.stream().filter(b->b.id().getPath().contains("/lungfill")).toList();
                assertEquals(energy==0?0:energy<=.5f?1:2,lungs.size());
                if(energy==0) continue;
                var left=lungs.getFirst();
                assertTrue(left.id().getPath().endsWith("/lungfill.png"));
                assertEquals(9,left.x());assertEquals(124,left.y());
                assertEquals(energy<.5f,left.clip()!=null);
                if(energy<=.5f) continue;
                var right=lungs.getLast();
                assertTrue(right.id().getPath().endsWith("/lungfill_right.png"));
                assertEquals(22,right.x());assertEquals(124,right.y());
                assertEquals(left.transform(),right.transform(),"Keep the right quad's winding for back-face culling");
                if(energy==1) assertNull(right.clip());
                else {
                    assertNotNull(right.clip());
                    assertTrue(right.clip().width()>0&&right.clip().height()>0);
                }
            }
        }
    }

    @Test void rightLungTextureMirrorsTheOriginalPixels() throws Exception {
        String path="/assets/decaying_aftermath/textures/gui/medical/native/lungfill";
        try(var leftIn=getClass().getResourceAsStream(path+".png");
            var rightIn=getClass().getResourceAsStream(path+"_right.png")) {
            assertNotNull(leftIn);assertNotNull(rightIn);
            var left=ImageIO.read(leftIn);var right=ImageIO.read(rightIn);
            assertEquals(left.getWidth(),right.getWidth());assertEquals(left.getHeight(),right.getHeight());
            for(int y=0;y<left.getHeight();y++) for(int x=0;x<left.getWidth();x++)
                assertEquals(left.getRGB(x,y),right.getRGB(right.getWidth()-1-x,y));
        }
    }

    @Test void partiallyFilledNativeOnePixelBarsRemainVisibleAtSmallGuiSizes() throws Exception {
        var parts=new BodySnapshot.PartSnapshot[6];
        java.util.Arrays.fill(parts,new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false));
        var survival=new io.github.safedecimal.decayingaftermath.medical.SurvivalSnapshot(.5f,.5f,37,.5f,0,1,1,0,0,0,0,0,1,0);
        var body=new BodySnapshot(1,.75f,.8f,.2f,1.25f,72,BodySnapshot.Mode.CONSCIOUS,1,.25f,.4f,parts,survival);
        for(int[] size:new int[][]{{240,180},{320,240},{640,480}}) {
            var g=Graphics.create(size[0],size[1]);var box=MedicalPanelLayout.forScreen(size[0],size[1]);
            g.pose().translate(box.x(),box.y()).scale(box.scale(),box.scale());
            MedicalPanelRenderer.extractReadouts(g,body);
            var bars=g.blits.stream().filter(b->b.id().toString().endsWith("/barwater.png")||b.id().toString().endsWith("/barfood.png")).toList();
            assertEquals(2,bars.size());
            for(var bar:bars) {
                assertEquals(1,bar.height());assertEquals(1,bar.sourceHeight());
                assertTrue(bar.clip().height()>0&&bar.clip().width()>0);
            }
        }
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
