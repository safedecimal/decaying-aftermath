package io.github.safedecimal.decayingaftermath.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.safedecimal.decayingaftermath.compat.ClientMedicalVisuals;
import io.github.safedecimal.decayingaftermath.compat.MedicalCollapsePolicy;
import io.github.safedecimal.decayingaftermath.compat.MedicalCollapsePolicy.Posture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/** Loads injured.json Bedrock animations and drives injured walking/idle/run and collapsed ragdoll fallback poses. */
public final class InjuredAnimations {
    public enum Clip {
        IDLE("animation.r6.exported.hurtidle"),
        WALK("animation.r6.exported.hurtanim"),
        RUN("animation.r6.exported.hurtrun");

        public final String jsonName;
        Clip(String jsonName) { this.jsonName = jsonName; }
    }

    public enum Bone {
        HEAD("Head"),
        BODY("Body"),
        RIGHT_ARM("Right Arm"),
        LEFT_ARM("Left Arm"),
        RIGHT_LEG("Right Leg"),
        LEFT_LEG("Left Leg");

        public final String jsonName;
        Bone(String jsonName) { this.jsonName = jsonName; }
    }

    private static final class Track {
        final float[] times;
        final float[] x, y, z;

        Track(float[] times, float[] x, float[] y, float[] z) {
            this.times = times;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        void sample(float t, Vector3f out) {
            int n = times.length;
            if (n == 0) {
                out.set(0, 0, 0);
                return;
            }
            if (n == 1 || t <= times[0]) {
                out.set(x[0], y[0], z[0]);
                return;
            }
            if (t >= times[n - 1]) {
                out.set(x[n - 1], y[n - 1], z[n - 1]);
                return;
            }
            int lo = 0, hi = n - 1;
            while (lo + 1 < hi) {
                int mid = (lo + hi) >>> 1;
                if (times[mid] <= t) lo = mid;
                else hi = mid;
            }
            float dt = times[hi] - times[lo];
            float alpha = dt <= 1e-6f ? 0f : (t - times[lo]) / dt;
            out.set(
                    Mth.lerp(alpha, x[lo], x[hi]),
                    Mth.lerp(alpha, y[lo], y[hi]),
                    Mth.lerp(alpha, z[lo], z[hi])
            );
        }
    }

    private static final class LoadedClip {
        final float length;
        final EnumMap<Bone, Track> rotations = new EnumMap<>(Bone.class);
        final EnumMap<Bone, Track> positions = new EnumMap<>(Bone.class);

        LoadedClip(float length) { this.length = Math.max(0.05f, length); }
    }

    private static volatile EnumMap<Clip, LoadedClip> clips;

    private InjuredAnimations() { }

    public static boolean loaded() {
        return ensureLoaded() != null;
    }

    public static float length(Clip clip) {
        var map = ensureLoaded();
        LoadedClip loaded = map == null ? null : map.get(clip);
        return loaded == null ? 1f : loaded.length;
    }

    public static void sample(Clip clip, float timeSeconds, Bone bone, Vector3f outPos, Vector3f outRotRad) {
        var map = ensureLoaded();
        if (map == null) {
            outPos.set(0, 0, 0);
            outRotRad.set(0, 0, 0);
            return;
        }
        LoadedClip data = map.get(clip);
        if (data == null) {
            outPos.set(0, 0, 0);
            outRotRad.set(0, 0, 0);
            return;
        }
        float wrapped = ((timeSeconds % data.length) + data.length) % data.length;
        Vector3f tmp = new Vector3f();
        Track posTrack = data.positions.get(bone);
        if (posTrack != null) {
            posTrack.sample(wrapped, tmp);
            outPos.set(KeyframeAnimations.posVec(tmp.x, tmp.y, tmp.z));
        } else {
            outPos.set(0, 0, 0);
        }
        Track rotTrack = data.rotations.get(bone);
        if (rotTrack != null) {
            rotTrack.sample(wrapped, tmp);
            outRotRad.set(KeyframeAnimations.degreeVec(tmp.x, tmp.y, tmp.z));
        } else {
            outRotRad.set(0, 0, 0);
        }
    }

    public static void apply(HumanoidModel<?> model, AvatarRenderState state) {
        if (state.isSpectator || state.swimAmount > 0.01f || state.fallFlyingTimeInTicks > 0f) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = client.level.getEntity(state.id);
        if (!(entity instanceof Player player) || !player.isAlive() || player.isSleeping()
                || player.isPassenger() || player.isFallFlying() || player.isSwimming()) {
            return;
        }
        Posture posture = resolvePosture(player, client);
        if (posture == Posture.NORMAL) return;

        if (posture == Posture.COLLAPSED) {
            applyCollapsedRagdollPose(model, state.id);
            return;
        }

        Clip clip = selectClip(state);
        float speedScale = clip == Clip.IDLE ? 1.0f : 0.75f + 0.45f * Math.min(1f, state.walkAnimationSpeed);
        float timeSeconds = (state.ageInTicks / 20.0f) * speedScale;

        Vector3f pos = new Vector3f();
        Vector3f rot = new Vector3f();
        applyBone(model.head, clip, timeSeconds, Bone.HEAD, pos, rot);
        applyBone(model.body, clip, timeSeconds, Bone.BODY, pos, rot);
        applyBone(model.rightArm, clip, timeSeconds, Bone.RIGHT_ARM, pos, rot);
        applyBone(model.leftArm, clip, timeSeconds, Bone.LEFT_ARM, pos, rot);
        applyBone(model.rightLeg, clip, timeSeconds, Bone.RIGHT_LEG, pos, rot);
        applyBone(model.leftLeg, clip, timeSeconds, Bone.LEFT_LEG, pos, rot);

        model.head.yRot += Mth.clamp(state.yRot * Mth.DEG_TO_RAD * 0.35f, -0.55f, 0.55f);
        model.head.xRot += Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.25f, -0.35f, 0.35f);
        if (state.swingAnimation > 0f) {
            float swing = Mth.sin(state.swingAnimation * (float) Math.PI);
            model.rightArm.xRot -= swing * 1.15f;
        }
    }

    private static Posture resolvePosture(Player player, Minecraft client) {
        if (player == client.player && ClientMedicalState.received()) {
            return MedicalCollapsePolicy.posture(ClientMedicalState.snapshot(), player.isAlive(),
                    player.isSleeping(), player.isCreative() || player.isSpectator());
        }
        var visual = ClientMedicalVisuals.forLiving(player);
        if (visual == null) return Posture.NORMAL;
        if (visual.collapsed()) return Posture.COLLAPSED;
        return visual.posture() == Posture.INJURED.ordinal() ? Posture.INJURED : Posture.NORMAL;
    }

    private static Clip selectClip(AvatarRenderState state) {
        if (state.walkAnimationSpeed > 0.42f) return Clip.RUN;
        if (state.walkAnimationSpeed > 0.04f) return Clip.WALK;
        return Clip.IDLE;
    }

    private static void applyBone(ModelPart part, Clip clip, float timeSeconds, Bone bone, Vector3f pos, Vector3f rot) {
        part.resetPose();
        sample(clip, timeSeconds, bone, pos, rot);
        part.offsetPos(pos);
        part.offsetRotation(rot);
    }

    private static void applyCollapsedRagdollPose(HumanoidModel<?> model, int seed) {
        model.head.resetPose();
        model.body.resetPose();
        model.rightArm.resetPose();
        model.leftArm.resetPose();
        model.rightLeg.resetPose();
        model.leftLeg.resetPose();

        float tilt = ((seed & 3) - 1.5f) * 0.08f;
        float flatX = -(float) (Math.PI * 0.5);

        model.body.setPos(0f, 21.5f, 4.0f);
        model.body.xRot = flatX;
        model.body.yRot = tilt;
        model.body.zRot = tilt * 0.6f;

        model.head.setPos(0f, 21.5f, 4.0f);
        model.head.xRot = flatX + 0.12f;
        model.head.yRot = tilt * 1.6f + 0.25f;
        model.head.zRot = -0.22f;

        model.rightArm.setPos(-5.5f, 21.5f, 2.0f);
        model.rightArm.xRot = flatX + 0.08f;
        model.rightArm.yRot = 0.25f;
        model.rightArm.zRot = 0.58f;

        model.leftArm.setPos(5.5f, 21.5f, 2.0f);
        model.leftArm.xRot = flatX + 0.05f;
        model.leftArm.yRot = -0.22f;
        model.leftArm.zRot = -0.48f;

        model.rightLeg.setPos(-2.2f, 21.5f, -8.0f);
        model.rightLeg.xRot = flatX - 0.04f;
        model.rightLeg.yRot = 0.12f;
        model.rightLeg.zRot = 0.18f;

        model.leftLeg.setPos(2.2f, 21.5f, -8.0f);
        model.leftLeg.xRot = flatX - 0.06f;
        model.leftLeg.yRot = -0.15f;
        model.leftLeg.zRot = -0.22f;
    }

    private static EnumMap<Clip, LoadedClip> ensureLoaded() {
        EnumMap<Clip, LoadedClip> existing = clips;
        if (existing != null) return existing;
        synchronized (InjuredAnimations.class) {
            if (clips != null) return clips;
            try (InputStream stream = InjuredAnimations.class.getClassLoader()
                    .getResourceAsStream("assets/abjection/animations/injured.json")) {
                if (stream == null) return null;
                JsonObject root = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                JsonObject anims = root.getAsJsonObject("animations");
                EnumMap<Clip, LoadedClip> parsed = new EnumMap<>(Clip.class);
                for (Clip clip : Clip.values()) {
                    JsonObject obj = anims.getAsJsonObject(clip.jsonName);
                    if (obj == null) continue;
                    LoadedClip loaded = new LoadedClip(obj.get("animation_length").getAsFloat());
                    JsonObject bones = obj.getAsJsonObject("bones");
                    for (Bone bone : Bone.values()) {
                        JsonObject b = bones.getAsJsonObject(bone.jsonName);
                        if (b == null) continue;
                        if (b.has("rotation")) loaded.rotations.put(bone, parseTrack(b.getAsJsonObject("rotation")));
                        if (b.has("position")) loaded.positions.put(bone, parseTrack(b.getAsJsonObject("position")));
                    }
                    parsed.put(clip, loaded);
                }
                clips = parsed;
                return parsed;
            } catch (IOException | RuntimeException ex) {
                return null;
            }
        }
    }

    private static Track parseTrack(JsonObject obj) {
        int size = obj.size();
        record Key(float t, float x, float y, float z) {}
        Key[] list = new Key[size];
        int idx = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
            float t = Float.parseFloat(entry.getKey());
            JsonArray arr = entry.getValue().getAsJsonArray();
            list[idx++] = new Key(t, arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
        }
        java.util.Arrays.sort(list, (a, b) -> Float.compare(a.t, b.t));
        float[] times = new float[size];
        float[] xs = new float[size], ys = new float[size], zs = new float[size];
        for (int i = 0; i < size; i++) {
            times[i] = list[i].t;
            xs[i] = list[i].x;
            ys[i] = list[i].y;
            zs[i] = list[i].z;
        }
        return new Track(times, xs, ys, zs);
    }
}
