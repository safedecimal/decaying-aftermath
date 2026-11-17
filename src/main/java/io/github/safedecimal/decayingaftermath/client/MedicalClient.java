package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import java.util.Objects;
import java.util.function.LongSupplier;

/** CU gameplay branches and serialized UI bindings, adapted to the authoritative Minecraft body. */
public final class MedicalClient {
    private static boolean registered;
    private static double beatPhase;
    private static String activeBeat;
    private static BodySnapshot.Mode previousMode = BodySnapshot.Mode.CONSCIOUS;
    private static LongSupplier clockNanos = System::nanoTime;
    private static long lastTickNanos;
    private static boolean playedDrone;
    private static MedicalLoopSound criticalLoop, unconsciousLoop;
    private static final MedicalPresentationTimeline TIMELINE = new MedicalPresentationTimeline();
    private static final EcgTrace CONSCIOUSNESS_ECG = new EcgTrace(
            ConsciousnessLayout.ECG_WIDTH, ConsciousnessLayout.ECG_HEIGHT);
    private MedicalClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen.getClass() == InventoryScreen.class && client.player != null
                    && !client.player.hasInfiniteMaterials()) {
                client.execute(() -> {
                    if (client.gui.screen() == screen && client.player != null)
                        client.gui.setScreen(new MedicalInventoryScreen(client.player));
                });
            }
        });
        HudElementRegistry.addLast(id("medical_condition"), (graphics, delta) -> extractHud(graphics));
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            stop("heartbeat"); stop("heartbeat_heavy"); stop("heartbeat_monitor"); activeBeat = null;
            stop("unconscious"); stop("last_standing"); stop("last_standing_drone");
            closeCriticalLoops();
            ThoughtTyper.reset(); beatPhase = 0; lastTickNanos = 0; playedDrone = false;
            TIMELINE.reset(); CONSCIOUSNESS_ECG.reset();
            previousMode = BodySnapshot.Mode.CONSCIOUS;
            return;
        }
        long now = clockNanos.getAsLong();
        double seconds = lastTickNanos == 0 ? .05 : Math.max(0, (now - lastTickNanos) / 1_000_000_000.0);
        lastTickNanos = now;
        if (!ClientMedicalState.received()) return;
        BodySnapshot body = ClientMedicalState.snapshot();
        if (body.mode() != previousMode) {
            previousMode = body.mode();
            if (body.mode() == BodySnapshot.Mode.LAST_STANDING) {
                TIMELINE.beginLastStand(); playedDrone = false;
                sound("last_standing", 1f, 1f);
            } else if (body.mode() == BodySnapshot.Mode.DEAD) {
                stop("last_standing"); stop("last_standing_drone");
            }
        }
        // LastStandSequence and blackout fades use unscaled time, even while the game is paused.
        TIMELINE.advance(body.mode(), seconds);
        if (TIMELINE.lastStandActive() && TIMELINE.droneDue() && !playedDrone) {
            playedDrone = true; sound("last_standing_drone", 1f, 1f);
        }
        boolean intro = TIMELINE.lastStandActive();
        updateCriticalLoops(body, seconds, !intro && MedicalPresentationPolicy.criticalAmbience(body));
        String beat = intro ? null : MedicalPresentationPolicy.beatSound(isInventoryOpen(client), body);
        if (!Objects.equals(activeBeat, beat)) {
            if (activeBeat != null) stop(activeBeat);
            activeBeat = beat;
        }
        if (client.isPaused()) return;
        if (ThoughtTyper.tick()) sound("thought_type", 0.45f, 1f);
        if (ThoughtTyper.hasMessage())
            client.gui.hud.setOverlayMessage(Component.literal(ThoughtTyper.visible()), false);
        double previousPhase = beatPhase;
        double advance = Math.min(seconds, .1) * body.bpm() / 60;
        beatPhase = (beatPhase + advance) % 1;
        CONSCIOUSNESS_ECG.follow(seconds, body.bpm(), beatPhase);
        // Body.Update emits its one selected heartbeat at heartProg > .3, not a second alarm layer.
        if (beat != null && (previousPhase < .3 && beatPhase >= .3
                || beatPhase < previousPhase && beatPhase >= .3)) {
            sound(beat, beat.equals("heartbeat_monitor") ? .4f : .55f, 1f);
        }
    }

    private static void updateCriticalLoops(BodySnapshot body, double seconds, boolean enabled) {
        if (enabled && criticalLoop == null) {
            criticalLoop = MedicalLoopSound.start("critical", 0, 1);
            unconsciousLoop = MedicalLoopSound.start("unconscious", 0, 1);
        }
        if (criticalLoop == null) return;
        boolean unconscious = body.mode() == BodySnapshot.Mode.UNCONSCIOUS;
        criticalLoop.fadeTo(enabled && !unconscious ? .65f : 0, seconds);
        unconsciousLoop.fadeTo(enabled && unconscious ? .65f : 0, seconds);
        if (!enabled && criticalLoop.volume() == 0 && unconsciousLoop.volume() == 0) closeCriticalLoops();
    }
    private static void closeCriticalLoops() {
        if (criticalLoop != null) criticalLoop.close();
        if (unconsciousLoop != null) unconsciousLoop.close();
        criticalLoop = unconsciousLoop = null;
    }
    public static double beatPhase() { return beatPhase; }

    public static void extractHud(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !ClientMedicalState.received()) return;
        BodySnapshot body = ClientMedicalState.snapshot();
        if (TIMELINE.black() > 0 || TIMELINE.vignette() > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color(TIMELINE.black(), 0));
            blit(graphics, "unconscious_vignette", 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    256, 256, color(TIMELINE.vignette(), 0xFFFFFF));
        }
        double alpha = TIMELINE.circleAlpha();
        if (alpha > 0) {
            var box = ConsciousnessLayout.at(graphics.guiWidth(), graphics.guiHeight());
            // sleepFill references sleepcircle (149x149), Image.Type.Filled, Radial360, Top, clockwise.
            // There is no separate solid green Circle image beneath it in the CU scene.
            radialTexture(graphics, "unconscious_ring", box.x(), box.y(), box.diameter(),
                    149, body.consciousnessProgress(), color(alpha, 0xFFFFFF));
            // Preserve the user's inventory-only pulse requirement; blackout/recovery ring remains on HUD.
            if (isInventoryOpen(client) && body.mode() == BodySnapshot.Mode.UNCONSCIOUS) {
                for (int column = 0; column < CONSCIOUSNESS_ECG.width(); column++) {
                    for (int row = 0; row < CONSCIOUSNESS_ECG.height(); row++) {
                        int pixelAlpha = CONSCIOUSNESS_ECG.alpha(column, row);
                        if (pixelAlpha == 0) continue;
                        int x0 = box.ecgX() + column * box.ecgWidth() / CONSCIOUSNESS_ECG.width();
                        int x1 = box.ecgX() + (column + 1) * box.ecgWidth() / CONSCIOUSNESS_ECG.width();
                        int y0 = box.ecgY() + row * box.ecgHeight() / CONSCIOUSNESS_ECG.height();
                        int y1 = box.ecgY() + (row + 1) * box.ecgHeight() / CONSCIOUSNESS_ECG.height();
                        if (x1 > x0 && y1 > y0) graphics.fill(x0, y0, x1, y1,
                                color(alpha * pixelAlpha / 255, 0xFFFFFF));
                    }
                }
                // BPM below the graph is a Minecraft readout adaptation, not CU's static "Zzz..." text.
                graphics.centeredText(client.font, Integer.toString(Math.round(body.bpm())),
                        box.textX(), box.textY(), color(alpha, 0xFFFFFF));
            }
        }
        if (TIMELINE.lastStandActive()) {
            int frame = TIMELINE.lastStandFrame();
            if (frame == 0) graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
            else {
                double brightness = TIMELINE.lastStandBrightness();
                double flicker = TIMELINE.lastStandFlicker() ? .75 + Math.random() * .25 : 1;
                int shade = (int) Math.round(Math.clamp(brightness * flicker, 0, 1) * 255);
                blit(graphics, "last_standing_" + frame, 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                        640, 360, color(TIMELINE.lastStandAlpha(), shade * 0x010101));
            }
            // level1/LastStand has one Image and zero text children. Never draw a red title here.
        }
    }

    private static int color(double alpha, int rgb) {
        return ((int) Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | rgb;
    }

    /** Pixel-accurate top/clockwise radial mask; contiguous source-pixel rows, no coarse 32x32 mosaic. */
    static void radialTexture(GuiGraphicsExtractor graphics,String texture,int x,int y,int size,
            int sourceSize,float progress,int color) {
        if (!Float.isFinite(progress) || progress <= 0 || size <= 0 || sourceSize <= 0) return;
        if (progress >= 1) { blit(graphics, texture, x, y, size, size, sourceSize, sourceSize, color); return; }
        double limit = progress * Math.PI * 2, center = sourceSize / 2.0;
        var textureId = id("textures/gui/procedures/" + texture + ".png");
        for (int row = 0; row < sourceSize; row++) {
            int y0 = y + row * size / sourceSize, y1 = y + (row + 1) * size / sourceSize;
            if (y1 <= y0) continue;
            int start = -1;
            for (int column = 0; column <= sourceSize; column++) {
                double angle = column == sourceSize ? Double.POSITIVE_INFINITY
                        : (Math.atan2(column + .5 - center, center - row - .5) + Math.PI * 2) % (Math.PI * 2);
                boolean included = angle <= limit;
                if (included && start < 0) start = column;
                if (!included && start >= 0) {
                    int x0 = x + start * size / sourceSize, x1 = x + column * size / sourceSize;
                    if (x1 > x0) graphics.blit(RenderPipelines.GUI_TEXTURED, textureId,
                            x0, y0, (float) start, (float) row, x1 - x0, y1 - y0,
                            column - start, 1, sourceSize, sourceSize, color);
                    start = -1;
                }
            }
        }
    }

    public static void sound(String name, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvent.createVariableRangeEvent(id(name)), pitch, volume));
    }
    public static boolean isInventoryOpen(Minecraft client) {
        return client.player != null && (client.gui.screen() instanceof MedicalInventoryScreen
                || client.gui.screen() instanceof InventoryScreen);
    }
    private static void stop(String name) { Minecraft.getInstance().getSoundManager().stop(id(name), null); }
    static Identifier id(String path) { return Identifier.fromNamespaceAndPath(DecayingAftermath.MOD_ID,path); }
    static void blit(GuiGraphicsExtractor graphics,String texture,int x,int y,int w,int h,int sw,int sh,int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED,id("textures/gui/procedures/"+texture+".png"),x,y,
                0f,0f,w,h,sw,sh,sw,sh,color);
    }
}
