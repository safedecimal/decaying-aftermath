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
    private static Model.Simple armClassic, armSlim;
    private static Model.Simple forearmClassic, forearmSlim;
    private static final EnumMap<BodyPart, Model.Simple> LIMBS_CLASSIC = new EnumMap<>(BodyPart.class);
    private static final EnumMap<BodyPart, Model.Simple> LIMBS_SLIM = new EnumMap<>(BodyPart.class);

    private Minigame3dRenderer() { }

    public static void draw3dHandAt(GuiGraphicsExtractor g, double x, double y, float rotationRad, boolean grasp) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        Identifier skin = player.getSkin().body().texturePath();
        Model.Simple model = getArmModel(slim);

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().rotate(rotationRad);

        float rx = -14f + (grasp ? 10f : 0f);
        float ry = 26f + (float) Math.toDegrees(rotationRad) * 0.35f;
        renderSkinBox(g, model, skin, -28f, -14f, 28f, 108f, 68f, rx, ry, -1.125f);
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
        float scale = part == BodyPart.HEAD ? 78f : part == BodyPart.TORSO ? 68f : 72f;
        renderSkinBox(g, model, skin, 62f, 54f, 194f, 206f, scale, rx, ry, -1.125f);
    }

    public static void draw3dDefibChest(GuiGraphicsExtractor g, float pointerX, float pointerY) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        Identifier skin = player.getSkin().body().texturePath();
        Model.Simple model = getLimbModel(BodyPart.TORSO, slim);

        float rx = -18f + Mth.clamp((pointerY - 128f) * 0.05f, -12f, 12f);
        float ry = Mth.clamp((pointerX - 128f) * 0.08f, -18f, 18f);
        renderSkinBox(g, model, skin, 42f, 44f, 214f, 216f, 82f, rx, ry, -1.125f);
    }

    public static void draw3dSelfHarmForearm(GuiGraphicsExtractor g, float tension, double stabProgress) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        Identifier skin = player.getSkin().body().texturePath();
        Model.Simple model = getForearmModel(slim);

        float rx = -22f - tension * 6f + (float) (stabProgress * 8.0);
        float ry = 14f + (float) (stabProgress * 6.0);
        renderSkinBox(g, model, skin, 26f, 122f, 230f, 214f, 95f, rx, ry, -1.125f);
    }

    private static void renderSkinBox(GuiGraphicsExtractor g, Model.Simple model, Identifier texture,
            float lx0, float ly0, float lx1, float ly1, float baseScale,
            float rotX, float rotY, float pivotY) {
        Vector2f p0 = g.pose().transformPosition(lx0, ly0, new Vector2f());
        Vector2f p1 = g.pose().transformPosition(lx1, ly1, new Vector2f());
        int x0 = Math.round(Math.min(p0.x, p1.x));
        int y0 = Math.round(Math.min(p0.y, p1.y));
        int x1 = Math.round(Math.max(p0.x, p1.x));
        int y1 = Math.round(Math.max(p0.y, p1.y));
        if (x1 <= x0 || y1 <= y0) return;
        float matScale = Math.max(0.15f, (float) Math.hypot(g.pose().m00(), g.pose().m01()));
        g.skin(model, texture, baseScale * matScale, rotX, rotY, pivotY, x0, y0, x1, y1);
    }

    private static Model.Simple getArmModel(boolean slim) {
        if (slim) {
            if (armSlim == null) armSlim = buildSingleBoxModel(40, 16, 40, 32, 3, 12, 4, 0f);
            return armSlim;
        }
        if (armClassic == null) armClassic = buildSingleBoxModel(40, 16, 40, 32, 4, 12, 4, 0f);
        return armClassic;
    }

    private static Model.Simple getForearmModel(boolean slim) {
        float zRot = (float) (Math.PI * 0.5);
        if (slim) {
            if (forearmSlim == null) forearmSlim = buildSingleBoxModel(32, 48, 48, 48, 3, 12, 4, zRot);
            return forearmSlim;
        }
        if (forearmClassic == null) forearmClassic = buildSingleBoxModel(32, 48, 48, 48, 4, 12, 4, zRot);
        return forearmClassic;
    }

    private static Model.Simple getLimbModel(BodyPart part, boolean slim) {
        var map = slim ? LIMBS_SLIM : LIMBS_CLASSIC;
        return map.computeIfAbsent(part, p -> createLimbModel(p, slim));
    }

    private static Model.Simple createLimbModel(BodyPart part, boolean slim) {
        int w = slim && (part == BodyPart.LEFT_ARM || part == BodyPart.RIGHT_ARM) ? 3 : 4;
        return switch (part) {
            case HEAD -> buildSingleBoxModel(0, 0, 32, 0, 8, 8, 8, 0f);
            case TORSO -> buildSingleBoxModel(16, 16, 16, 32, 8, 12, 4, 0f);
            case RIGHT_ARM -> buildSingleBoxModel(40, 16, 40, 32, w, 12, 4, 0f);
            case LEFT_ARM -> buildSingleBoxModel(32, 48, 48, 48, w, 12, 4, 0f);
            case RIGHT_LEG -> buildSingleBoxModel(0, 16, 0, 32, 4, 12, 4, 0f);
            case LEFT_LEG -> buildSingleBoxModel(16, 48, 0, 48, 4, 12, 4, 0f);
        };
    }

    private static Model.Simple buildSingleBoxModel(int u, int v, int outerU, int outerV,
            int width, int height, int depth, float zRot) {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        float ox = -width / 2.0f, oy = -height / 2.0f, oz = -depth / 2.0f;
        var part = root.addOrReplaceChild("limb",
                CubeListBuilder.create().texOffs(u, v).addBox(ox, oy, oz, width, height, depth),
                PartPose.offsetAndRotation(0f, 18f, 0f, 0f, 0f, zRot));
        part.addOrReplaceChild("overlay",
                CubeListBuilder.create().texOffs(outerU, outerV)
                        .addBox(ox, oy, oz, width, height, depth, new CubeDeformation(0.25f)),
                PartPose.ZERO);
        var baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        return new Model.Simple(baked, RenderTypes::entityTranslucent);
    }
}
