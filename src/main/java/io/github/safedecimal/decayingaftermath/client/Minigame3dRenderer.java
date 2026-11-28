package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Vector2f;

import java.util.EnumMap;

/** Real 3D textured player limbs and arm cursor via GuiGraphicsExtractor.skin for /trynew3dsystem. */
public final class Minigame3dRenderer {
    private static Model.Simple forearmClassic, forearmSlim;
    private static final EnumMap<BodyPart, Model.Simple> LIMBS_CLASSIC = new EnumMap<>(BodyPart.class);
    private static final EnumMap<BodyPart, Model.Simple> LIMBS_SLIM = new EnumMap<>(BodyPart.class);

    private Minigame3dRenderer() { }

    public static void draw3dHandAt(GuiGraphicsExtractor g, double x, double y, float rotationRad, boolean grasp) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        Identifier skin = player.getSkin().body().texturePath();

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().rotate(rotationRad);

        float m00 = g.pose().m00(), m01 = g.pose().m01();
        float m10 = g.pose().m10(), m11 = g.pose().m11();
        boolean mirrored = (m00 * m11 - m01 * m10) < 0f;
        float matScale = Math.max(0.15f, (float) Math.hypot(m00, m01));
        float totalAngleRad = (float) Math.atan2(-m10, m11);

        // Flip 180 degrees so fingers/palm sit at (0, 0) and shoulder extends downward; match 2D 36x108 scale (144 px/block).
        float zRot = (float) Math.PI + totalAngleRad;
        Model.Simple model = buildHandModel(slim, mirrored, zRot);

        Vector2f center = g.pose().transformPosition(-4f, 50f, new Vector2f());
        float halfExtent = 73f * matScale;
        int x0 = Math.round(center.x - halfExtent);
        int y0 = Math.round(center.y - halfExtent);
        int x1 = Math.round(center.x + halfExtent);
        int y1 = Math.round(center.y + halfExtent);

        float rx = -14f + (grasp ? 10f : 0f);
        float ry = mirrored ? -24f : 24f;
        if (x1 > x0 && y1 > y0) {
            g.skin(model, skin, 144f * matScale, rx, ry, -0.507f, x0, y0, x1, y1);
            g.nextStratum();
        }
        CuMedicalHand.drawHandHitbox(g, grasp);
        g.pose().popMatrix();
    }

    public static void draw3dProcedureLimb(GuiGraphicsExtractor g, BodyPart part, float pointerX, float pointerY) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        Identifier skin = player.getSkin().body().texturePath();
        Model.Simple model = getLimbModel(part, slim);

        float rx = -12f + Mth.clamp((pointerY - 128f) * 0.08f, -16f, 16f);
        float ry = 22f + Mth.clamp((pointerX - 128f) * 0.14f, -24f, 24f);
        float scale = part == BodyPart.HEAD ? 116f : part == BodyPart.TORSO ? 112f : 120f;
        renderSkinBox(g, model, skin, 62f, 54f, 194f, 198f, scale, rx, ry, -0.58f);
    }

    public static void draw3dDefibChest(GuiGraphicsExtractor g, float pointerX, float pointerY) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        Identifier skin = player.getSkin().body().texturePath();
        Model.Simple model = getLimbModel(BodyPart.TORSO, slim);

        float rx = -18f + Mth.clamp((pointerY - 128f) * 0.05f, -12f, 12f);
        float ry = Mth.clamp((pointerX - 128f) * 0.08f, -18f, 18f);
        renderSkinBox(g, model, skin, 42f, 40f, 214f, 200f, 148f, rx, ry, -0.58f);
    }

    public static void draw3dSelfHarmForearm(GuiGraphicsExtractor g, float tension, double stabProgress) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        Identifier skin = player.getSkin().body().texturePath();
        Model.Simple model = getForearmModel(slim);

        float rx = -22f - tension * 6f + (float) (stabProgress * 8.0);
        float ry = 14f + (float) (stabProgress * 6.0);
        renderSkinBox(g, model, skin, 26f, 120f, 230f, 254f, 180f, rx, ry, -0.52f);
    }

    private static void renderSkinBox(GuiGraphicsExtractor g, Model.Simple model, Identifier texture,
            float lx0, float ly0, float lx1, float ly1, float baseScale,
            float rotX, float rotY, float pivotY) {
        Vector2f p0 = g.pose().transformPosition(lx0, ly0, new Vector2f());
        Vector2f p1 = g.pose().transformPosition(lx1, ly0, new Vector2f());
        Vector2f p2 = g.pose().transformPosition(lx0, ly1, new Vector2f());
        Vector2f p3 = g.pose().transformPosition(lx1, ly1, new Vector2f());
        int x0 = Math.round(Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x)));
        int y0 = Math.round(Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y)));
        int x1 = Math.round(Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x)));
        int y1 = Math.round(Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y)));
        if (x1 <= x0 || y1 <= y0) return;
        float matScale = Math.max(0.15f, (float) Math.hypot(g.pose().m00(), g.pose().m01()));
        g.skin(model, texture, baseScale * matScale, rotX, rotY, pivotY, x0, y0, x1, y1);
    }

    private static Model.Simple buildHandModel(boolean slim, boolean leftArm, float zRot) {
        int w = slim ? 3 : 4;
        int u = leftArm ? 32 : 40;
        int v = leftArm ? 48 : 16;
        int outerU = leftArm ? 48 : 40;
        int outerV = leftArm ? 48 : 32;
        return buildSingleBoxModel(u, v, outerU, outerV, w, 12, 4, 17.5f, zRot);
    }

    private static Model.Simple getForearmModel(boolean slim) {
        float zRot = (float) (-Math.PI * 0.5);
        if (slim) {
            if (forearmSlim == null) forearmSlim = buildSingleBoxModel(32, 48, 48, 48, 3, 12, 4, 18f, zRot);
            return forearmSlim;
        }
        if (forearmClassic == null) forearmClassic = buildSingleBoxModel(32, 48, 48, 48, 4, 12, 4, 18f, zRot);
        return forearmClassic;
    }

    private static Model.Simple getLimbModel(BodyPart part, boolean slim) {
        var map = slim ? LIMBS_SLIM : LIMBS_CLASSIC;
        return map.computeIfAbsent(part, p -> createLimbModel(p, slim));
    }

    private static Model.Simple createLimbModel(BodyPart part, boolean slim) {
        int w = slim && (part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM) ? 3 : 4;
        return switch (part) {
            case HEAD -> buildSingleBoxModel(0, 0, 32, 0, 8, 8, 8, 18f, 0f);
            case TORSO -> buildSingleBoxModel(16, 16, 16, 32, 8, 12, 4, 18f, 0f);
            case RIGHT_ARM -> buildSingleBoxModel(40, 16, 40, 32, w, 12, 4, 18f, 0f);
            case LEFT_ARM -> buildSingleBoxModel(32, 48, 48, 48, w, 12, 4, 18f, 0f);
            case RIGHT_LEG -> buildSingleBoxModel(0, 16, 0, 32, 4, 12, 4, 18f, 0f);
            case LEFT_LEG -> buildSingleBoxModel(16, 48, 0, 48, 4, 12, 4, 18f, 0f);
        };
    }

    private static Model.Simple buildSingleBoxModel(int u, int v, int outerU, int outerV,
            int width, int height, int depth, float yOffset, float zRot) {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        float ox = -width / 2.0f, oy = -height / 2.0f, oz = -depth / 2.0f;
        var part = root.addOrReplaceChild("limb",
                CubeListBuilder.create().texOffs(u, v).addBox(ox, oy, oz, width, height, depth),
                PartPose.offsetAndRotation(0f, yOffset, 0f, 0f, 0f, zRot));
        part.addOrReplaceChild("overlay",
                CubeListBuilder.create().texOffs(outerU, outerV)
                        .addBox(ox, oy, oz, width, height, depth, new CubeDeformation(0.25f)),
                PartPose.ZERO);
        var baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        return new Model.Simple(baked, RenderTypes::entityTranslucent);
    }
}
