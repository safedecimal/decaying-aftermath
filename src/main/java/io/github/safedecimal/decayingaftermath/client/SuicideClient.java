package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.network.SuicidePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Client-side private diegetic /suicide beep, spoken panic line, and head-only explosion. */
public final class SuicideClient {
    private static boolean registered;
    private static boolean countingDown;
    private static int flashTicks;

    private SuicideClient() { }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(SuicidePayload.TYPE, (payload, context) ->
                context.client().execute(() -> handle(payload)));
    }

    public static boolean active() { return countingDown; }

    public static void reset() {
        countingDown = false;
        flashTicks = 0;
        Minecraft.getInstance().getSoundManager().stop(Identifier.fromNamespaceAndPath("abjection", "selfdestruct1"), SoundSource.PLAYERS);
    }

    public static void tick(Minecraft client) {
        if (flashTicks > 0) flashTicks--;
        if (countingDown && (client.player == null || !client.player.isAlive() || AftermathClient.active())) {
            reset();
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
            reset();
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
                if (client.level != null) {
                    double hx = client.player.getX();
                    double hy = client.player.getEyeY();
                    double hz = client.player.getZ();
                    client.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, hx, hy, hz, 0, 0, 0);
                    for (int i = 0; i < 18; i++) {
                        double vx = (Math.random() - 0.5) * 0.35;
                        double vy = (Math.random() - 0.2) * 0.35;
                        double vz = (Math.random() - 0.5) * 0.35;
                        client.level.addParticle(ParticleTypes.EXPLOSION, hx, hy, hz, vx, vy, vz);
                        client.level.addParticle(ParticleTypes.LARGE_SMOKE, hx, hy, hz, vx * 0.7, vy * 0.7, vz * 0.7);
                        client.level.addParticle(ParticleTypes.FLAME, hx, hy, hz, vx, vy, vz);
                        client.level.addParticle(ParticleTypes.CRIT, hx, hy, hz, vx * 1.4, vy * 1.4, vz * 1.4);
                    }
                }
            }
        }
    }

    private static void playDiegetic(Minecraft client, String soundName) {
        SoundEvent event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("abjection", soundName));
        client.getSoundManager().play(new EntityBoundSoundInstance(event, SoundSource.PLAYERS, 1.0f, 1.0f, client.player, 0L));
    }
}
