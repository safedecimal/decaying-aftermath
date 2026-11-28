package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.compat.ClientMedicalRagdolls;
import io.github.safedecimal.decayingaftermath.network.HeadBleedPayload;
import io.github.safedecimal.decayingaftermath.network.SuicidePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Client-side private diegetic /suicide beep, spoken panic line, HUD flash, and headless neck blood gush. */
public final class SuicideClient {
    private static final DustParticleOptions BLOOD_DUST = new DustParticleOptions(0xFF9E0B14, 1.35f);
    private static final BlockParticleOption BLOOD_DROP = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());

    public static final class NeckBleed {
        public final int entityId;
        public final UUID playerUuid;
        public final double fallbackX, fallbackY, fallbackZ;
        public int remainingTicks;
        public boolean initialBursted;
        public boolean cruorBursted;

        NeckBleed(int entityId, UUID playerUuid, double x, double y, double z, int ticks) {
            this.entityId = entityId;
            this.playerUuid = playerUuid;
            this.fallbackX = x;
            this.fallbackY = y;
            this.fallbackZ = z;
            this.remainingTicks = ticks;
        }

        public Vec3 origin() {
            Vec3 ragdollNeck = ClientMedicalRagdolls.neckOrigin(entityId, playerUuid);
            return ragdollNeck != null ? ragdollNeck : new Vec3(fallbackX, fallbackY - 0.35, fallbackZ);
        }

        public Vec3 direction() {
            return ClientMedicalRagdolls.neckDirection(entityId, playerUuid);
        }
    }

    private static final List<NeckBleed> NECK_BLEEDS = new ArrayList<>();
    private static boolean registered;
    private static boolean countingDown;
    private static int flashTicks;
    private static Object currentLevel;

    private SuicideClient() { }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(SuicidePayload.TYPE, (payload, context) ->
                context.client().execute(() -> handle(payload)));
        ClientPlayNetworking.registerGlobalReceiver(HeadBleedPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    NECK_BLEEDS.removeIf(b -> b.playerUuid.equals(payload.playerUuid()));
                    NECK_BLEEDS.add(new NeckBleed(payload.entityId(), payload.playerUuid(),
                            payload.x(), payload.y(), payload.z(), payload.durationTicks()));
                }));
    }

    public static boolean active() { return countingDown; }

    public static List<NeckBleed> activeNeckBleeds() { return List.copyOf(NECK_BLEEDS); }

    public static void reset() {
        countingDown = false;
        flashTicks = 0;
        NECK_BLEEDS.clear();
        currentLevel = null;
        Minecraft.getInstance().getSoundManager().stop(Identifier.fromNamespaceAndPath("abjection", "selfdestruct1"), SoundSource.PLAYERS);
    }

    public static void tick(Minecraft client) {
        if (client.level != currentLevel) {
            NECK_BLEEDS.clear();
            currentLevel = client.level;
        }
        if (flashTicks > 0) flashTicks--;
        if (countingDown && (client.player == null || !client.player.isAlive() || AftermathClient.active())) {
            countingDown = false;
            Minecraft.getInstance().getSoundManager().stop(Identifier.fromNamespaceAndPath("abjection", "selfdestruct1"), SoundSource.PLAYERS);
        }
        if (client.level == null || client.isPaused() || NECK_BLEEDS.isEmpty()) return;

        Iterator<NeckBleed> it = NECK_BLEEDS.iterator();
        while (it.hasNext()) {
            NeckBleed bleed = it.next();
            if (--bleed.remainingTicks <= 0) {
                it.remove();
                continue;
            }
            Vec3 pos = bleed.origin();
            Vec3 dir = bleed.direction();
            if (!bleed.initialBursted) {
                bleed.initialBursted = true;
                for (int i = 0; i < 45; i++) {
                    double vx = (Math.random() - 0.5) * 1.4;
                    double vy = 0.25 + Math.random() * 0.95;
                    double vz = (Math.random() - 0.5) * 1.4;
                    client.level.addParticle(BLOOD_DROP, pos.x, pos.y + 0.1, pos.z, vx, vy, vz);
                }
            }
            int count = bleed.remainingTicks > 300 ? 6 : 3;
            for (int i = 0; i < count; i++) {
                double jet = 0.35 + Math.random() * 0.45;
                double vx = dir.x * jet + (Math.random() - 0.5) * 0.32;
                double vy = dir.y * jet + 0.15 + Math.random() * 0.25;
                double vz = dir.z * jet + (Math.random() - 0.5) * 0.32;
                client.level.addParticle(BLOOD_DROP, pos.x, pos.y, pos.z, vx, vy, vz);
                if (i % 2 == 0) {
                    client.level.addParticle(BLOOD_DUST, pos.x + (Math.random() - 0.5) * 0.12,
                            pos.y + Math.random() * 0.1, pos.z + (Math.random() - 0.5) * 0.12, 0, 0, 0);
                }
            }
        }
    }

    public static void extractOverlay(GuiGraphicsExtractor graphics) {
        if (flashTicks <= 0) return;
        double alpha = Math.clamp(flashTicks / 12.0, 0.0, 1.0) * 0.85;
        int color = ((int) Math.round(alpha * 255) << 24) | 0xD81414;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
    }

    private static void handle(SuicidePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (payload.stage() == SuicidePayload.ABORT) {
            countingDown = false;
            flashTicks = 0;
            client.getSoundManager().stop(Identifier.fromNamespaceAndPath("abjection", "selfdestruct1"), SoundSource.PLAYERS);
            return;
        }
        if (payload.stage() == SuicidePayload.START) {
            countingDown = true;
            flashTicks = 0;
            if (client.player != null) {
                playDiegetic(client, "selfdestruct1");
            }
            ThoughtTyper.reset();
            ThoughtTyper.enqueue(Component.translatable("abjection.thought.suicide").getString());
            return;
        }
        if (payload.stage() == SuicidePayload.DETONATE) {
            countingDown = false;
            flashTicks = 12;
            client.getSoundManager().stop(Identifier.fromNamespaceAndPath("abjection", "selfdestruct1"), SoundSource.PLAYERS);
            ThoughtTyper.reset();
            if (client.player != null) {
                playDiegetic(client, "selfdestruct2");
            }
        }
    }

    private static void playDiegetic(Minecraft client, String soundName) {
        SoundEvent event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("abjection", soundName));
        client.getSoundManager().play(new EntityBoundSoundInstance(event, SoundSource.PLAYERS, 1.0f, 1.0f, client.player, 0L));
    }
}
