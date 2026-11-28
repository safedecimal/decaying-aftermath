package io.github.safedecimal.decayingaftermath.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** Fresh identity-matched medical players only; native wound/stain/corpse records remain intact. */
public final class CruorVisualCompatibility {
    private static volatile boolean broken;
    private record NativeFields(Field bodies, Field bleed, Field healedSince, Field droplets, Field blood,
            Method bodyFor, Method bloodOf, Method emitting, Method spawn, Method burst, Method color, Method dried) {}
    private static final ClassValue<NativeFields> FIELDS = new ClassValue<>() {
        @Override protected NativeFields computeValue(Class<?> type) {
            try {
                if (!type.getName().equals("com.raiiiden.cruor.client.body.BodyBlood"))
                    throw new NoSuchFieldException("Unexpected Cruor visual owner");
                Field bodies = type.getDeclaredField("bodies"); bodies.setAccessible(true);
                if (!Map.class.isAssignableFrom(bodies.getType())) throw new NoSuchFieldException("Non-map bodies API");
                Class<?> body = Class.forName(type.getName() + "$Body", false, type.getClassLoader());
                Field bleed = body.getDeclaredField("bleed"); bleed.setAccessible(true);
                if (bleed.getType() != float.class) throw new NoSuchFieldException("Non-float bleed API");
                Field healedSince = body.getDeclaredField("healedSince"); healedSince.setAccessible(true);
                Field blood = body.getDeclaredField("blood"); blood.setAccessible(true);
                Field droplets = type.getDeclaredField("droplets"); droplets.setAccessible(true);
                ClassLoader loader = type.getClassLoader();
                Class<?> bloodType = Class.forName("com.raiiiden.cruor.client.blood.BloodType", false, loader);
                Class<?> bloodTypes = Class.forName("com.raiiiden.cruor.client.blood.BloodTypes", false, loader);
                Method bodyFor = type.getDeclaredMethod("bodyFor", LivingEntity.class, bloodType); bodyFor.setAccessible(true);
                Class<?> drops = droplets.getType();
                return new NativeFields(bodies, bleed, healedSince, droplets, blood, bodyFor,
                        bloodTypes.getMethod("of", LivingEntity.class), drops.getMethod("emitting", int.class),
                        drops.getMethod("spawn", double.class, double.class, double.class, float.class, float.class,
                                float.class, float.class, int.class, int.class, int.class, float.class, int.class),
                        drops.getMethod("burst", double.class, double.class, double.class, boolean.class, boolean.class, int.class),
                        bloodType.getMethod("color"), bloodType.getMethod("dried"));
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
    };
    private CruorVisualCompatibility() {}
    public static void syncVisualBleeding(Object nativeBodyBlood) {
        var client = Minecraft.getInstance();
        if (broken || client.level == null) return;
        try {
            NativeFields fields = FIELDS.get(nativeBodyBlood.getClass());
            Object droplets = fields.droplets().get(nativeBodyBlood);
            for (var neck : io.github.safedecimal.decayingaftermath.client.SuicideClient.activeNeckBleeds()) {
                var pos = neck.origin();
                var dir = neck.direction();
                var random = client.level.getRandom();
                Object blood = fields.bloodOf().invoke(null, client.player != null ? client.player : client.level.players().stream().findFirst().orElse(null));
                if (blood != null) {
                    int color = (int) fields.color().invoke(blood);
                    int dried = (int) fields.dried().invoke(blood);
                    fields.emitting().invoke(droplets, neck.entityId);
                    try {
                        if (!neck.cruorBursted) {
                            neck.cruorBursted = true;
                            fields.burst().invoke(droplets, pos.x, pos.y + 0.12, pos.z, true, true, 52);
                            for (int i = 0; i < 48; i++) {
                                float speed = 0.25f + random.nextFloat() * 0.55f;
                                float vx = (random.nextFloat() - 0.5f) * 2.0f * speed;
                                float vy = 0.18f + random.nextFloat() * 0.52f;
                                float vz = (random.nextFloat() - 0.5f) * 2.0f * speed;
                                fields.spawn().invoke(droplets, pos.x, pos.y + 0.08, pos.z,
                                        vx, vy, vz, 1.5f + random.nextFloat() * 0.8f, color, dried, 3,
                                        1.1f + random.nextFloat() * 0.3f, 160);
                            }
                        }
                        int burst = neck.remainingTicks > 300 ? 6 : 3;
                        for (int i = 0; i < burst; i++) {
                            float jet = 0.22f + random.nextFloat() * 0.28f;
                            float vx = (float) (dir.x * jet + (random.nextFloat() - 0.5f) * 0.18f);
                            float vy = (float) (dir.y * jet + 0.08f + random.nextFloat() * 0.16f);
                            float vz = (float) (dir.z * jet + (random.nextFloat() - 0.5f) * 0.18f);
                            fields.spawn().invoke(droplets,
                                    pos.x + (random.nextFloat() - .5) * .08,
                                    pos.y,
                                    pos.z + (random.nextFloat() - .5) * .08,
                                    vx, vy, vz,
                                    1.25f + random.nextFloat() * 0.45f, color, dried, 3,
                                    1.0f + random.nextFloat() * .25f, 140);
                        }
                    } finally {
                        fields.emitting().invoke(droplets, -1);
                    }
                }
            }
            // Injury can bleed at high cosmetic HP or after reconnect, without a fresh native hit packet.
            for (var player : client.level.players()) {
                var visual = ClientMedicalVisuals.forLiving(player);
                if (visual != null && visual.bleeding() > .0001f)
                    fields.bodyFor().invoke(nativeBodyBlood, player, fields.bloodOf().invoke(null, player));
            }
            for (var entry : ((Map<?, ?>)fields.bodies().get(nativeBodyBlood)).entrySet()) {
                if (!(entry.getKey() instanceof Integer id) || entry.getValue() == null) continue;
                var entity = client.level.getEntity(id);
                if (!(entity instanceof LivingEntity living)) continue;
                var visual = ClientMedicalVisuals.forLiving(living);
                // No matching fresh summary, mob, wrong UUID/id/level, or corpse: retain native state.
                if (visual != null) {
                    fields.bleed().setFloat(entry.getValue(), CruorVisualPolicy.nativeBleed(visual.bleeding()));
                    if (visual.bleeding() > .0001f) fields.healedSince().setLong(entry.getValue(), -1);
                }
            }
        } catch (ReflectiveOperationException | IllegalStateException | LinkageError error) {
            broken = true;
            System.getLogger("abjection.compat").log(System.Logger.Level.WARNING,
                    "Cruor visual bridge disabled after incompatible native API: " + error);
        }
    }
    /** Keep Cruor's droplets, colors, collision and decals; replace only the drip origin. */
    public static boolean emitMedicalDrip(Object nativeBodyBlood, LivingEntity living, Object body, float size) {
        if (broken) return false;
        var visual = ClientMedicalVisuals.forLiving(living);
        if (visual == null || visual.bleeding() <= .0001f) return false;
        var part = MedicalBleedOrigins.select(visual.partBleeding(), living.getRandom().nextDouble());
        if (part == null) return false;
        try {
            NativeFields fields = FIELDS.get(nativeBodyBlood.getClass());
            var from = ClientMedicalRagdolls.origin(living, part);
            if (from == null) {
                double[] offset = MedicalBleedOrigins.restOffset(part);
                from = living.position().add(new net.minecraft.world.phys.Vec3(offset[0], offset[1], offset[2])
                        .yRot((float)Math.toRadians(180 - living.yBodyRot)));
            }
            Object droplets = fields.droplets().get(nativeBodyBlood), blood = fields.blood().get(body);
            var motion = living.getDeltaMovement(); var random = living.getRandom();
            fields.emitting().invoke(droplets, living.getId());
            try {
                fields.spawn().invoke(droplets, from.x + (random.nextFloat() - .5) * .06, from.y - .05,
                        from.z + (random.nextFloat() - .5) * .06, (float)motion.x * .6f, -.02f, (float)motion.z * .6f,
                        size, (int)fields.color().invoke(blood), (int)fields.dried().invoke(blood), 3,
                        .85f + random.nextFloat() * .2f, 120);
            } finally { fields.emitting().invoke(droplets, -1); }
            return true;
        } catch (ReflectiveOperationException | IllegalStateException | LinkageError error) {
            broken = true;
            System.getLogger("abjection.compat").log(System.Logger.Level.WARNING, "Cruor wound-origin bridge disabled: " + error);
            return false;
        }
    }
    public static float healthForBleedFallback(LivingEntity entity, float actual) {
        var visual = ClientMedicalVisuals.forLiving(entity);
        return visual == null ? actual : CruorVisualPolicy.fallbackHealth(visual.bleeding(), visual.dead(), actual, entity.getMaxHealth());
    }

}
