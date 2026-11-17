package io.github.safedecimal.decayingaftermath.server;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import io.github.safedecimal.decayingaftermath.compat.MedicalDamageContext;
import io.github.safedecimal.decayingaftermath.medical.BodyModel;
import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.PartSnapshot;
import io.github.safedecimal.decayingaftermath.medical.TreatmentKind;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server authority and a guarded compatibility mirror; Minecraft HP never stores a second life. */
public final class ServerMedicalSystem {
    private static final String SAVE_KEY = "decaying_aftermath_body";
    private static final ThreadLocal<Integer> BRIDGE = ThreadLocal.withInitial(() -> 0);
    private static final Map<ServerPlayer, DamageSource> LAST_SOURCE = new WeakHashMap<>();
    private static BiConsumer<ServerPlayer, BodySnapshot> syncHandler = (player, snapshot) -> {};
    private static boolean registered;
    private static final Identifier LEG_SPEED = Identifier.fromNamespaceAndPath("decaying_aftermath", "injured_leg_speed");
    private static final Identifier LEG_JUMP = Identifier.fromNamespaceAndPath("decaying_aftermath", "injured_leg_jump");
    private static final Identifier ARM_SPEED = Identifier.fromNamespaceAndPath("decaying_aftermath", "injured_arm_speed");
    private static final Identifier ARM_DAMAGE = Identifier.fromNamespaceAndPath("decaying_aftermath", "injured_arm_damage");
    private ServerMedicalSystem() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) tick(player);
        });
        ServerPlayerEvents.JOIN.register(player -> { get(player); mirrorHealth(player); syncBody(player, get(player).snapshot()); });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            owner(newPlayer).decayingAftermath$setBody(alive
                    ? BodyModel.restoreState(get(oldPlayer).saveState()) : new BodyModel());
            LAST_SOURCE.remove(newPlayer); mirrorHealth(newPlayer);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> syncBody(player, get(player).snapshot()));
        ServerPlayerEvents.LEAVE.register(player -> {
            LAST_SOURCE.remove(player);
            io.github.safedecimal.decayingaftermath.compat.MedicalRagdollCompatibility.release(player);
        });
    }

    public static BodyModel get(ServerPlayer player) {
        ServerBodyOwner owner = owner(player);
        BodyModel model = owner.decayingAftermath$getBody();
        owner.decayingAftermath$setBodyReady(true);
        return model;
    }
    public static void setSyncHandler(BiConsumer<ServerPlayer, BodySnapshot> handler) {
        syncHandler = java.util.Objects.requireNonNull(handler);
    }
    private static void syncBody(ServerPlayer player, BodySnapshot snapshot) {
        io.github.safedecimal.decayingaftermath.compat.MedicalRagdollCompatibility.update(player, snapshot);
        syncHandler.accept(player, snapshot);
        io.github.safedecimal.decayingaftermath.compat.MedicalVisualNetwork.sync(player, snapshot);
    }
    public static boolean canAct(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) return true;
        Mode mode = get(player).snapshot().mode();
        return player.isAlive() && (mode == Mode.CONSCIOUS || mode == Mode.LAST_STANDING || mode == Mode.DYING);
    }
    public static boolean isReady(ServerPlayer player) { return owner(player).decayingAftermath$isBodyReady(); }
    public static boolean isHealthBridge() { return BRIDGE.get() > 0; }
    private static ServerBodyOwner owner(ServerPlayer player) { return (ServerBodyOwner) (Object) player; }
    private static void bridge(Runnable action) {
        BRIDGE.set(BRIDGE.get() + 1);
        try { action.run(); } finally { BRIDGE.set(BRIDGE.get() - 1); }
    }

    /** Called only at Player.actuallyHurt's one post-mitigation HP write. */
    public static void applyDamage(ServerPlayer player, DamageSource source, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return;
        BodyModel model = get(player);
        LAST_SOURCE.put(player, source);
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) model.kill();
        else if (source.is(DamageTypeTags.IS_FALL)) model.fall(amount);
        else if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL)) model.reduceOxygen(Math.min(1, amount * .08f));
        else if (source.is(DamageTypes.STARVE)) model.reduceOxygen(Math.min(1, amount * .1f));
        else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            model.damage(BodyPart.TORSO, amount * .3f);
            model.damage(BodyPart.HEAD, amount * .18f);
            model.damage(BodyPart.LEFT_ARM, amount * .11f); model.damage(BodyPart.RIGHT_ARM, amount * .11f);
            model.damage(BodyPart.LEFT_LEG, amount * .15f); model.damage(BodyPart.RIGHT_LEG, amount * .15f);
        } else if (source.is(DamageTypeTags.IS_FIRE)) {
            for (BodyPart part : BodyPart.values()) model.damage(part, amount / BodyPart.values().length);
        } else model.damage(resolvePart(player, source), amount);
        if (model.snapshot().mode() == Mode.DEAD) tryBodyTotem(player, source);
        mirrorHealth(player);
        syncBody(player, get(player).snapshot());
    }

    /** A mod's direct HP delta has no reliable hit position; route it to the torso once. */
    public static void directHealth(ServerPlayer player, float requested) {
        if (!Float.isFinite(requested)) { mirrorHealth(player); return; }
        float delta = requested - player.getHealth();
        if (delta < 0) {
            DamageSource source = MedicalDamageContext.activeDamageSource(player);
            if (source != null) applyDamage(player, source, -delta);
            else get(player).damage(BodyPart.TORSO, -delta);
        }
        else if (delta > 0) adaptHealing(player, delta);
        mirrorHealth(player);
    }
    public static void adaptHealing(ServerPlayer player, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return;
        get(player).treat(BodyPart.TORSO, TreatmentKind.HEALING_POTION, Math.clamp(amount / 8, 0, 1));
        mirrorHealth(player);
    }
    public static void mirrorHealth(ServerPlayer player) {
        BodySnapshot snapshot = get(player).snapshot();
        float value = snapshot.mode() == Mode.DEAD ? 0
                : Math.max(Math.min(1, player.getMaxHealth()), player.getMaxHealth() * snapshot.condition());
        if (Math.abs(player.getHealth() - value) > .001f) bridge(() -> player.setHealth(value));
    }

    private static void tick(ServerPlayer player) {
        BodyModel model = get(player);
        if (player.isCreative() || player.isSpectator()) {
            if (player.tickCount % 5 == 0) applyInjuryModifiers(player, model.snapshot(), false);
            if (player.tickCount % 5 == 0) syncBody(player, model.snapshot());
            return;
        }
        boolean wasAlive = player.isAlive();
        if (!wasAlive) return;
        model.tick(player.isSprinting(), player.getDeltaMovement().horizontalDistance() > .01);
        BodySnapshot snapshot = model.snapshot();
        if (player.tickCount % 5 == 0) applyInjuryModifiers(player, snapshot, true);
        if (snapshot.mode() == Mode.UNCONSCIOUS) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(0, Math.min(0, motion.y), 0);
            player.setSprinting(false); player.stopUsingItem();
        }
        if (snapshot.mode() == Mode.DEAD) {
            DamageSource source = LAST_SOURCE.getOrDefault(player, player.damageSources().generic());
            if (!tryBodyTotem(player, source)) {
                mirrorHealth(player); player.die(source);
            }
        }
        mirrorHealth(player);
        if (player.tickCount % 5 == 0 || snapshot.mode() == Mode.DEAD) syncBody(player, get(player).snapshot());
    }

    private static void applyInjuryModifiers(ServerPlayer player, BodySnapshot snapshot, boolean active) {
        double legs = active ? (limbPenalty(snapshot.part(BodyPart.LEFT_LEG))
                + limbPenalty(snapshot.part(BodyPart.RIGHT_LEG))) * .5 : 0;
        double arms = active ? (limbPenalty(snapshot.part(BodyPart.LEFT_ARM))
                + limbPenalty(snapshot.part(BodyPart.RIGHT_ARM))) * .5 : 0;
        transientPenalty(player, Attributes.MOVEMENT_SPEED, LEG_SPEED, Math.min(.85, legs));
        transientPenalty(player, Attributes.JUMP_STRENGTH, LEG_JUMP, Math.min(.95, legs * 1.2));
        transientPenalty(player, Attributes.ATTACK_SPEED, ARM_SPEED, Math.min(.7, arms));
        transientPenalty(player, Attributes.ATTACK_DAMAGE, ARM_DAMAGE, Math.min(.65, arms * .75));
    }
    private static double limbPenalty(PartSnapshot limb) {
        return (1 - limb.integrity()) * .5 + (limb.fractured() ? limb.splinted() ? .12 : .45 : 0)
                + (limb.anesthetized() ? .8 : 0);
    }
    private static void transientPenalty(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double penalty) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        if (penalty <= .0001) { instance.removeModifier(id); return; }
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null && Math.abs(existing.amount() + penalty) <= .00001) return;
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, -penalty,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static boolean tryBodyTotem(ServerPlayer player, DamageSource source) {
        final boolean[] saved = {false};
        bridge(() -> saved[0] = owner(player).decayingAftermath$tryTotem(source));
        if (!saved[0]) return false;
        BodySnapshot old = get(player).snapshot(); PartSnapshot[] parts = old.parts();
        for (BodyPart part : BodyPart.values()) {
            PartSnapshot p = parts[part.ordinal()];
            parts[part.ordinal()] = new PartSnapshot(part.isVital() ? Math.max(.35f, p.integrity()) : p.integrity(),
                    p.bleeding() * .25f, p.pain() * .5f, p.fractured(), p.bandaged(), p.splinted(), p.tourniquet(),
                    p.infection(), p.disinfected(), p.anesthetized());
        }
        owner(player).decayingAftermath$setBody(BodyModel.restore(new BodySnapshot(.25f,
                Math.max(.5f, old.blood()), Math.max(.6f, old.oxygen()), old.pain() * .5f,
                Math.min(.4f, old.drug()), 85, Mode.UNCONSCIOUS, 0,
                Math.max(.5f, old.energy()), .4f, parts)));
        return true;
    }

    private static BodyPart resolvePart(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.DAMAGES_HELMET)) return BodyPart.HEAD;
        Entity direct = source.getDirectEntity(); AABB box = player.getBoundingBox(); Vec3 hit = null;
        if (source.is(DamageTypeTags.IS_PROJECTILE) && direct != null)
            hit = box.inflate(.15).clip(direct.oldPosition(), direct.position()).orElse(direct.position());
        Entity attacker = source.getEntity();
        if (hit == null && attacker != null) {
            Vec3 eye = attacker.getEyePosition();
            hit = box.clip(eye, eye.add(attacker.getLookAngle().scale(6))).orElse(null);
            if (hit == null) hit = new Vec3(attacker.getX(), Math.clamp(eye.y, box.minY, box.maxY), attacker.getZ());
        }
        if (hit == null) return BodyPart.TORSO;
        double relativeHeight = Math.clamp((hit.y - box.minY) / Math.max(.01, box.maxY - box.minY), 0, 1);
        double yaw = Math.toRadians(player.getYRot());
        double side = (hit.x - player.getX()) * Math.cos(yaw) + (hit.z - player.getZ()) * Math.sin(yaw);
        if (relativeHeight > .82) return BodyPart.HEAD;
        if (relativeHeight < .42) return side >= 0 ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
        if (Math.abs(side) > (box.maxX - box.minX) * .35)
            return side >= 0 ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM;
        return BodyPart.TORSO;
    }

    public static void save(ServerPlayer player, ValueOutput output) {
        BodyModel.SavedState saved = get(player).saveState(); BodySnapshot body = saved.snapshot();
        ValueOutput node = output.child(SAVE_KEY);
        node.putInt("version", 2); node.putFloat("blood", body.blood()); node.putFloat("oxygen", body.oxygen());
        node.putFloat("energy", body.energy()); node.putFloat("consciousness", body.consciousness());
        node.putFloat("drug", body.drug()); node.putFloat("bpm", body.bpm()); node.putInt("mode", body.mode().ordinal());
        node.putFloat("progress", body.consciousnessProgress());
        node.putIntArray("care", saved.careTicks()); node.putIntArray("regeneration", saved.regenerationTicks());
        node.putIntArray("tourniquet_time", saved.tourniquetTicks());
        node.putBoolean("last_standing_spent", saved.lastStandingSpent()); node.putInt("stable_ticks", saved.stableTicks());
        node.putInt("last_standing_intro", saved.lastStandingTicks());
        node.putInt("last_standing_boost", saved.lastStandingBoostTicks()); node.putInt("dying_ticks", saved.dyingTicks());
        node.putIntArray("disinfection_time", saved.disinfectionTicks());
        node.putIntArray("anesthetic_time", saved.anestheticTicks()); node.putIntArray("infection_age", saved.infectionAgeTicks());
        float[] skinCare = saved.skinHealAmount(), bleedCare = saved.bandageSlowAmount(), rawPain = saved.rawLocalPain();
        for (BodyPart part : BodyPart.values()) {
            PartSnapshot p = body.part(part); ValueOutput limb = node.child("part" + part.ordinal());
            limb.putFloat("integrity", p.integrity()); limb.putFloat("bleeding", p.bleeding()); limb.putFloat("pain", p.pain());
            limb.putBoolean("fractured", p.fractured()); limb.putBoolean("bandaged", p.bandaged());
            limb.putBoolean("splinted", p.splinted()); limb.putBoolean("tourniquet", p.tourniquet());
            limb.putFloat("infection", p.infection());
            limb.putFloat("skin_care", skinCare[part.ordinal()]); limb.putFloat("bleed_care", bleedCare[part.ordinal()]);
            limb.putFloat("raw_pain", rawPain[part.ordinal()]);
        }
    }
    public static void load(ServerPlayer player, ValueInput input) {
        var optional = input.child(SAVE_KEY);
        BodyModel model = new BodyModel();
        if (optional.isPresent()) {
            ValueInput node = optional.get(); PartSnapshot[] parts = new PartSnapshot[BodyPart.values().length];
            float[] skinCare = new float[parts.length], bleedCare = new float[parts.length], rawPain = new float[parts.length];
            for (BodyPart part : BodyPart.values()) {
                ValueInput p = node.childOrEmpty("part" + part.ordinal());
                parts[part.ordinal()] = new PartSnapshot(number(p, "integrity", 1, 1), number(p, "bleeding", 0, 1),
                        number(p, "pain", 0, 1), p.getBooleanOr("fractured", false), p.getBooleanOr("bandaged", false),
                        p.getBooleanOr("splinted", false), p.getBooleanOr("tourniquet", false),
                        number(p, "infection", 0, 1), false, false);
                skinCare[part.ordinal()] = number(p, "skin_care", 0, 10);
                bleedCare[part.ordinal()] = number(p, "bleed_care", 0, 10);
                rawPain[part.ordinal()] = number(p, "raw_pain", parts[part.ordinal()].pain(), 1);
            }
            Mode mode = Mode.values()[Math.clamp(node.getIntOr("mode", 0), 0, Mode.values().length - 1)];
            BodySnapshot body = new BodySnapshot(1, number(node, "blood", 1, 1), number(node, "oxygen", 1, 1), 0,
                    number(node, "drug", 0, 3), number(node, "bpm", 72, 220), mode, number(node, "progress", 1, 1),
                    number(node, "energy", 1, 1), number(node, "consciousness", 1, 1), parts);
            model = BodyModel.restoreState(new BodyModel.SavedState(body, timers(node, "care"), timers(node, "regeneration"),
                    timers(node, "tourniquet_time"), node.getBooleanOr("last_standing_spent", mode != Mode.CONSCIOUS),
                    Math.clamp(node.getIntOr("stable_ticks", 0), 0, 1200),
                    Math.clamp(node.getIntOr("last_standing_intro", mode == Mode.LAST_STANDING
                            ? Math.round(body.consciousnessProgress() * BodyModel.LAST_STANDING_TICKS) : 0), 0, BodyModel.LAST_STANDING_TICKS),
                    Math.clamp(node.getIntOr("last_standing_boost", node.getBooleanOr("last_standing_spent", mode != Mode.CONSCIOUS)
                            ? BodyModel.LAST_STANDING_BOOST_TICKS : 0), 0, BodyModel.LAST_STANDING_BOOST_TICKS),
                    Math.clamp(node.getIntOr("dying_ticks", mode == Mode.DYING
                            ? Math.round(body.consciousnessProgress() * BodyModel.DYING_TICKS) : -1), -1, BodyModel.DYING_TICKS),
                    timers(node, "disinfection_time"), timers(node, "anesthetic_time"), timers(node, "infection_age"), skinCare, bleedCare, rawPain));
        }
        owner(player).decayingAftermath$setBody(model); mirrorHealth(player);
    }
    private static float number(ValueInput node, String key, float fallback, float maximum) {
        float value = node.getFloatOr(key, fallback);
        return Float.isFinite(value) ? Math.clamp(value, 0, maximum) : fallback;
    }
    private static int[] timers(ValueInput node, String key) {
        int[] values = node.getIntArray(key).orElse(new int[BodyPart.values().length]);
        if (values.length != BodyPart.values().length) return new int[BodyPart.values().length];
        for (int i = 0; i < values.length; i++) values[i] = Math.clamp(values[i], 0, 1_000_000);
        return values;
    }
}
