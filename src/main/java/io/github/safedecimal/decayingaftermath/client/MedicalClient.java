package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
    private static final HeartbeatFovPulse HEARTBEAT_FOV = new HeartbeatFovPulse();
    private static String activeBeat;
    private static BodySnapshot.Mode previousMode = BodySnapshot.Mode.CONSCIOUS;
    private static LongSupplier clockNanos = System::nanoTime;
    private static long lastTickNanos;
    private static boolean playedDrone;
    private static MedicalLoopSound criticalLoop, unconsciousLoop;
    private static MedicalLoopSound painLoop, exhaustedLoop, tiredLoop, dyingLoop;
    private static MedicalMusicSound dyingMusic,deadMusic;
    private static final MedicalDeathMusicState DEATH_MUSIC=new MedicalDeathMusicState();
    private static final MedicalPainState PAIN=new MedicalPainState();
    private static Object currentPlayer;
    private static boolean waitingForLiveBody;
    private static final MedicalPresentationTimeline TIMELINE = new MedicalPresentationTimeline();
    private static final EcgTrace CONSCIOUSNESS_ECG = new EcgTrace(
            ConsciousnessLayout.ECG_WIDTH, ConsciousnessLayout.ECG_HEIGHT);
    private MedicalClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clearPresentation());
        HudElementRegistry.addLast(id("medical_condition"), (graphics, delta) -> {
            Object screen=Minecraft.getInstance().gui.screen();
            // These two screens explicitly extract after their own contents; never composite twice.
            if(!(screen instanceof MedicalInventoryScreen)&&!(screen instanceof MedicalProcedureScreen))extractHud(graphics);
        });
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            clearPresentation();
            return;
        }
        if(currentPlayer!=client.player) {
            boolean staleDeath=currentPlayer!=null&&previousMode==BodySnapshot.Mode.DEAD
                    &&ClientMedicalState.received()&&ClientMedicalState.snapshot().mode()==BodySnapshot.Mode.DEAD;
            clearPresentation();currentPlayer=client.player;waitingForLiveBody=staleDeath;
        }
        long now = clockNanos.getAsLong();
        double seconds = lastTickNanos == 0 ? .05 : Math.max(0, (now - lastTickNanos) / 1_000_000_000.0);
        lastTickNanos = now;
        if (!ClientMedicalState.received()) return;
        BodySnapshot body = ClientMedicalState.snapshot();
        if(waitingForLiveBody) {if(body.mode()==BodySnapshot.Mode.DEAD)return;waitingForLiveBody=false;}
        if(body.mode()==BodySnapshot.Mode.DEAD||!client.player.isAlive())PAIN.reset();
        else PAIN.advance(body.pain(),client.isPaused()?0:seconds);
        if (body.mode() != previousMode) {
            previousMode = body.mode();
            // A blackout cuts the player off from the world: stop what is already sounding and
            // drop queued thoughts, because nothing new may start while fainted.
            if (body.mode() == BodySnapshot.Mode.UNCONSCIOUS) {
                ClientAudioGate.silenceWorldSounds(client, body);
                ThoughtTyper.reset();
            }
            if (body.mode() == BodySnapshot.Mode.LAST_STANDING) {
                TIMELINE.beginLastStand(); playedDrone = false;
                sound("last_standing", 1f, 1f);
            } else if (body.mode() == BodySnapshot.Mode.DEAD || body.mode()==BodySnapshot.Mode.DYING) {
                stop("last_standing"); stop("last_standing_drone");
            }
        }
        // LastStandSequence and blackout fades use unscaled time, even while the game is paused.
        TIMELINE.advance(body, seconds);
        updateDeathMusic(body.mode(),body.consciousnessProgress(),seconds);
        if(body.mode()==BodySnapshot.Mode.DYING||body.mode()==BodySnapshot.Mode.DEAD)
            closeCriticalLoops();
        if (TIMELINE.lastStandActive() && TIMELINE.droneDue() && !playedDrone) {
            playedDrone = true; sound("last_standing_drone", 1f, 1f);
        }
        boolean intro = TIMELINE.lastStandActive();
        boolean audioAlive = !intro && body.mode() != BodySnapshot.Mode.DEAD;
        updateCriticalLoops(body, seconds, audioAlive && body.mode() != BodySnapshot.Mode.DYING
                && MedicalPresentationPolicy.criticalAmbience(body));
        updateSituationLoops(body, seconds, audioAlive && body.mode() != BodySnapshot.Mode.DYING
                || body.mode() == BodySnapshot.Mode.DYING && !DEATH_MUSIC.preDeathStarted());
        String beat = intro ? null : MedicalPresentationPolicy.beatSound(isInventoryOpen(client), body);
        if (!Objects.equals(activeBeat, beat)) {
            if (activeBeat != null) stop(activeBeat);
            activeBeat = beat;
        }
        if (beat == null || client.isPaused()) HEARTBEAT_FOV.reset();
        if (client.isPaused()) return;
        // Thoughts are action-bar text: a fainted player neither reads nor hears them, and
        // nothing accumulates for the moment of waking up either.
        boolean thoughtsAllowed = !UnconsciousPresentationPolicy.hintsHidden(body);
        if (thoughtsAllowed) {
            if (ThoughtTyper.tick()) sound("thought_type", 0.45f, 1f);
            if (ThoughtTyper.hasMessage())
                client.gui.hud.setOverlayMessage(Component.literal(ThoughtTyper.visible()), false);
        } else ThoughtTyper.reset();
        double previousPhase = beatPhase;
        double advance = Math.min(seconds, .1) * body.bpm() / 60;
        beatPhase = (beatPhase + advance) % 1;
        CONSCIOUSNESS_ECG.follow(seconds, body.bpm(), beatPhase);
        // Body.Update emits its one selected heartbeat at heartProg > .3, not a second alarm layer.
        if (beat != null && (previousPhase < .3 && beatPhase >= .3
                || beatPhase < previousPhase && beatPhase >= .3)) {
            HEARTBEAT_FOV.onBeat(clockNanos.getAsLong());
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
    private static void updateSituationLoops(BodySnapshot body, double seconds, boolean enabled) {
        var volumes = enabled ? MedicalSituationSoundState.volumes(body)
                : new MedicalSituationSoundState.Volumes(0, 0, 0, 0);
        painLoop = updateSituationLoop(painLoop, "pain_drone", volumes.pain(), seconds);
        exhaustedLoop = updateSituationLoop(exhaustedLoop, "exhausted_drone", volumes.exhausted(), seconds);
        tiredLoop = updateSituationLoop(tiredLoop, "tired_drone", volumes.tired(), seconds);
        dyingLoop = updateSituationLoop(dyingLoop, "dying_drone", volumes.dying(), seconds);
    }
    private static MedicalLoopSound updateSituationLoop(MedicalLoopSound sound, String name,
            float target, double seconds) {
        if (sound == null && target > .001f) sound = MedicalLoopSound.start(name, 0, 1);
        if (sound == null) return null;
        sound.fadeTo(target * .65f, seconds);
        if (target <= .001f && sound.volume() <= .001f) { sound.close(); return null; }
        return sound;
    }
    private static void closeSituationLoops() {
        if (painLoop != null) painLoop.close();
        if (exhaustedLoop != null) exhaustedLoop.close();
        if (tiredLoop != null) tiredLoop.close();
        if (dyingLoop != null) dyingLoop.close();
        painLoop = exhaustedLoop = tiredLoop = dyingLoop = null;
    }
    private static void updateDeathMusic(BodySnapshot.Mode mode,float progress,double seconds) {
        var action=DEATH_MUSIC.advance(mode,progress);
        if(action.stopDying()&&dyingMusic!=null) {dyingMusic.close();dyingMusic=null;}
        if(action.stopDead()&&deadMusic!=null) {deadMusic.close();deadMusic=null;}
        // DrawYourLastBreath1 is a one-shot CU clip, not a loop. It is armed only
        // near the end of the DYING grace window by MedicalDeathMusicState.
        if(action.startDying())dyingMusic=MedicalMusicSound.start("brain_dying_music",false);
        if(action.playDead()) {
            if(dyingMusic!=null) {dyingMusic.close();dyingMusic=null;}
            deadMusic=MedicalMusicSound.start("death_music",false);
        }
        if(dyingMusic!=null)dyingMusic.fadeTo(.8f,seconds);
    }
    private static void clearPresentation() {
        stop("heartbeat");stop("heartbeat_heavy");stop("heartbeat_monitor");activeBeat=null;
        stop("unconscious");stop("last_standing");stop("last_standing_drone");
        closeCriticalLoops(); closeSituationLoops();
        if(dyingMusic!=null)dyingMusic.close();if(deadMusic!=null)deadMusic.close();
        dyingMusic=deadMusic=null;DEATH_MUSIC.reset();
        HEARTBEAT_FOV.reset();
        ThoughtTyper.reset();beatPhase=0;lastTickNanos=0;playedDrone=false;
        TIMELINE.reset();CONSCIOUSNESS_ECG.reset();PAIN.reset();previousMode=BodySnapshot.Mode.CONSCIOUS;
        VanillaMusicGate.reset();
        currentPlayer=null;waitingForLiveBody=false;
    }
    public static double beatPhase() { return beatPhase; }

    public static float heartbeatFov(Minecraft client, float vanillaFov) {
        if (client.level == null || client.player == null || client.player != currentPlayer
                || !client.player.isAlive() || client.isPaused() || !ClientMedicalState.received()) {
            return vanillaFov;
        }
        BodySnapshot body = ClientMedicalState.snapshot();
        if (body.mode() == BodySnapshot.Mode.DEAD || body.bpm() <= 0) return vanillaFov;
        return HEARTBEAT_FOV.apply(vanillaFov, client.options.fovEffectScale().get(), clockNanos.getAsLong());
    }

    public static void extractHud(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !ClientMedicalState.received()) return;
        BodySnapshot body = ClientMedicalState.snapshot();
        extractPain(graphics);
        if (TIMELINE.black() > 0 || TIMELINE.vignette() > 0) {
            double black=MedicalPresentationPolicy.blackOverlay(body,TIMELINE.black(),isMedicalPanelOpen(client));
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color(black, 0));
            blit(graphics, "consciousness_vignette", 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    256, 256, color(TIMELINE.vignette(), 0xFFFFFF));
        }
        double alpha = body.mode() == BodySnapshot.Mode.UNCONSCIOUS ? Math.max(.35, TIMELINE.circleAlpha()) : TIMELINE.circleAlpha();
        if (alpha > 0) {
            var box = ConsciousnessLayout.at(graphics.guiWidth(), graphics.guiHeight());
            extractRecoveryRing(graphics, box, body, alpha);
            if (body.mode() == BodySnapshot.Mode.UNCONSCIOUS)
                graphics.centeredText(client.font, net.minecraft.network.chat.Component.translatable(
                        io.github.safedecimal.decayingaftermath.medical.WakeRecovery.labelKey(body),
                        io.github.safedecimal.decayingaftermath.medical.WakeRecovery.remainingSeconds(body)),
                        box.textX(), box.y() + box.diameter() + 6, color(alpha, 0xFFFFFF));
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
        // Keep the medical readouts legible above the edge-darkening during the rescue window.
        if(body.mode()==BodySnapshot.Mode.DYING&&isMedicalPanelOpen(client))
            MedicalPanelRenderer.extract(graphics,MedicalPanelLayout.forScreen(graphics.guiWidth(),graphics.guiHeight()));
    }

    static void extractRecoveryRing(GuiGraphicsExtractor graphics, ConsciousnessLayout box, BodySnapshot body, double alpha) {
        // A full dim track remains visible even when progress is zero or recovery is paused.
        blit(graphics, "unconscious_ring", box.x(), box.y(), box.diameter(), box.diameter(),
                149, 149, color(alpha * .35, 0xFFFFFF));
        radialTexture(graphics, "unconscious_ring", box.x(), box.y(), box.diameter(),
                149, body.consciousnessProgress(), color(alpha, 0xFFFFFF));
    }

    private static int color(double alpha, int rgb) {
        return ((int) Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | rgb;
    }

    /** CU's shader painimage/paingrain red mix, rendered before black/fatal screens. */
    public static void extractPain(GuiGraphicsExtractor graphics) {
        Minecraft client=Minecraft.getInstance();
        if(client.player==null||!client.player.isAlive()||!ClientMedicalState.received()
                ||ClientMedicalState.snapshot().mode()==BodySnapshot.Mode.DEAD)return;
        int index=PAIN.maskIndex()+(PAIN.maskBlend()>=.5?1:0);
        // One shader-derived mask per frame: 96 fine levels avoid double-alpha crossfade artifacts.
        if(index>0)blit(graphics,"pain_mask_"+index,0,0,graphics.guiWidth(),graphics.guiHeight(),256,256,0xFFFFFFFF);
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
    public static boolean isMedicalPanelOpen(Minecraft client) {
        return isInventoryOpen(client)||client.player!=null&&client.gui.screen() instanceof MedicalProcedureScreen;
    }
    private static void stop(String name) { Minecraft.getInstance().getSoundManager().stop(id(name), null); }
    static Identifier id(String path) { return Identifier.fromNamespaceAndPath(DecayingAftermath.MOD_ID,path); }
    static void blit(GuiGraphicsExtractor graphics,String texture,int x,int y,int w,int h,int sw,int sh,int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED,id("textures/gui/procedures/"+texture+".png"),x,y,
                0f,0f,w,h,sw,sh,sw,sh,color);
    }
}
