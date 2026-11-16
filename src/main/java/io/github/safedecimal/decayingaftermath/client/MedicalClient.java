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

/** Client presentation of authoritative body state, using extracted CU textures and recordings. */
public final class MedicalClient {
    private static boolean registered;
    private static float beatPhase;
    private static boolean alarmEnabled;
    private static BodySnapshot.Mode previousMode = BodySnapshot.Mode.CONSCIOUS;
    private static int modeTicks;
    private static final EcgTrace CONSCIOUSNESS_ECG = new EcgTrace();
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
            stop("heartbeat"); stop("heartbeat_monitor"); alarmEnabled = false;
            stop("unconscious"); stop("last_standing"); stop("last_standing_drone");
            ThoughtTyper.reset(); beatPhase = 0; modeTicks = 0;
            previousMode = BodySnapshot.Mode.CONSCIOUS;
            return;
        }
        if (client.isPaused()) return;
        if (ThoughtTyper.tick()) sound("thought_type", 0.45f, 1f);
        if (ThoughtTyper.hasMessage())
            client.gui.hud.setOverlayMessage(Component.literal(ThoughtTyper.visible()), false);
        BodySnapshot body = ClientMedicalState.snapshot();
        CONSCIOUSNESS_ECG.advance(1.0 / 20.0, body.bpm());
        if (body.mode() != previousMode) {
            stop("unconscious"); stop("last_standing"); stop("last_standing_drone");
            previousMode = body.mode(); modeTicks = 0;
            if (body.mode() == BodySnapshot.Mode.UNCONSCIOUS) sound("unconscious", 0.65f, 1f);
            if (body.mode() == BodySnapshot.Mode.LAST_STANDING) {
                sound("last_standing", 0.75f, 1f);
                sound("last_standing_drone", 0.55f, 1f);
            }
        }
        modeTicks++;
        boolean critical = ClientMedicalState.received() && MedicalPresentationPolicy.criticalAlarm(body);
        if (!critical) {
            if (alarmEnabled) { stop("heartbeat"); stop("heartbeat_monitor"); }
            beatPhase = 0;
        }
        alarmEnabled = critical;
        if (critical) {
            beatPhase += body.bpm() / 1200f;
            if (beatPhase >= 1) {
                beatPhase %= 1;
                sound(isInventoryOpen(client)
                        ? "heartbeat_monitor" : "heartbeat", 0.4f, 1f);
            }
        }
    }

    public static void extractHud(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !ClientMedicalState.received()) return;
        BodySnapshot body = ClientMedicalState.snapshot();
        if (body.mode() == BodySnapshot.Mode.UNCONSCIOUS) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xB0000000);
            blit(graphics, "unconscious_vignette", 0, 0, graphics.guiWidth(), graphics.guiHeight(), 256, 256, 0xA0FFFFFF);
            int cx = graphics.guiWidth()/2, cy = graphics.guiHeight()/2;
            radialFill(graphics, cx - 35, cy - 35, 70, body.consciousnessProgress());
            blit(graphics, "unconscious_ring", cx - 39, cy - 39, 78, 78, 149, 149, 0xFFFFFFFF);
            // Pulse/BPM belongs to the inventory, including during unconsciousness.
            // Keep only the blackout and original recovery circle on the world HUD.
            if (isInventoryOpen(client)) {
                for (int column=0; column<EcgTrace.WIDTH; column++) for (int row=0; row<EcgTrace.HEIGHT; row++) {
                    int alpha=CONSCIOUSNESS_ECG.alpha(column,row);
                    if (alpha!=0) {
                        int x=cx-30+column/2, y=cy-7+row/2;
                        graphics.fill(x,y,x+1,y+1,(alpha<<24)|0x002BE080);
                    }
                }
                graphics.centeredText(client.font, Integer.toString(Math.round(body.bpm())), cx, cy+11, 0xFF2BD881);
            }
        } else if (body.mode() == BodySnapshot.Mode.LAST_STANDING) {
            // Source CU intro: two black seconds, then three original frames at 8 fps.
            if (modeTicks < 40) graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
            else if (modeTicks < 174) {
                int frame = ((modeTicks-40)*8/20)%3+1;
                blit(graphics, "last_standing_"+frame, 0, 0, graphics.guiWidth(), graphics.guiHeight(), 640, 360, 0xDDFFFFFF);
            }
            graphics.centeredText(client.font, "Last Standing", graphics.guiWidth()/2, 18, 0xFFFF614A);
        }
    }

    /** Radially masks the original fillCircle pixels; no invented replacement circle sprite. */
    private static void radialFill(GuiGraphicsExtractor graphics, int x, int y, int size, float progress) {
        radialTexture(graphics,"unconscious_fill",x,y,size,256,progress,0x702BD881);
    }
    static void radialTexture(GuiGraphicsExtractor graphics,String texture,int x,int y,int size,
            int sourceSize,float progress,int color) {
        int cells = 32;
        for (int row=0; row<cells; row++) for (int col=0; col<cells; col++) {
            double nx=(col+0.5)/cells*2-1, ny=(row+0.5)/cells*2-1;
            if (nx*nx+ny*ny>1) continue;
            double angle=(Math.atan2(nx,-ny)+Math.PI*2)%(Math.PI*2);
            if (angle>progress*Math.PI*2) continue;
            int x0=x+col*size/cells,y0=y+row*size/cells;
            int x1=x+(col+1)*size/cells,y1=y+(row+1)*size/cells;
            int sx=col*sourceSize/cells,sy=row*sourceSize/cells;
            int sw=(col+1)*sourceSize/cells-sx,sh=(row+1)*sourceSize/cells-sy;
            graphics.blit(RenderPipelines.GUI_TEXTURED, id("textures/gui/procedures/"+texture+".png"),
                    x0,y0,(float)sx,(float)sy,x1-x0,y1-y0,sw,sh,sourceSize,sourceSize,color);
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
