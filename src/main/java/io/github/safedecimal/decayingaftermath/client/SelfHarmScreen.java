package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import io.github.safedecimal.decayingaftermath.network.SelfHarmNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.sdl.SDLMouse;

/** Unclosable severe-depression minigame: Steve-proportioned human hand holding a weapon above the skin. */
public final class SelfHarmScreen extends Screen {
    private static SelfHarmNetwork.State activeState;
    private static SelfHarmScreen currentScreen;
    private static boolean registered;

    private SelfHarmNetwork.State state;
    private float scale = 1f;
    private int ox, oy;
    private int tickCount;
    private int recoilTicks;
    private int cutAnimTicks;
    private int flashTicks;
    private boolean closingAfterCut;

    private SelfHarmScreen(SelfHarmNetwork.State state) {
        super(Component.translatable("abjection.self_harm.title"));
        this.state = state;
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(SelfHarmNetwork.State.TYPE, (payload, context) ->
                context.client().execute(() -> receive(payload)));
    }

    public static boolean active() {
        return activeState != null && (activeState.active() || (currentScreen != null && currentScreen.closingAfterCut));
    }

    public static void ensureOpen(Minecraft client) {
        if (!active() || client.player == null || !client.player.isAlive() || AftermathClient.active()) {
            if (activeState != null && (client.player == null || !client.player.isAlive() || AftermathClient.active())) {
                reset();
            }
            return;
        }
        if (ClientMedicalState.received()) {
            var mode = ClientMedicalState.snapshot().mode();
            if (mode == BodySnapshot.Mode.DEAD || mode == BodySnapshot.Mode.UNCONSCIOUS) {
                reset();
                return;
            }
        }
        if (currentScreen != null && client.gui.screen() != currentScreen) {
            client.gui.setScreen(currentScreen);
        }
    }

    public static void reset() {
        activeState = null;
        if (currentScreen != null) {
            SelfHarmScreen screen = currentScreen;
            currentScreen = null;
            Minecraft client = Minecraft.getInstance();
            if (client.gui.screen() == screen) {
                client.gui.setScreen(null);
            }
        }
    }

    private static void receive(SelfHarmNetwork.State payload) {
        Minecraft client = Minecraft.getInstance();
        if (payload.action() == SelfHarmNetwork.ABORT) {
            reset();
            return;
        }
        if (payload.active()) {
            activeState = payload;
            if (currentScreen == null || currentScreen.state.sessionId() != payload.sessionId()) {
                currentScreen = new SelfHarmScreen(payload);
                client.gui.setScreen(currentScreen);
                MedicalClient.sound("heartbeat_heavy", 0.55f, 0.9f);
            } else {
                currentScreen.applyUpdate(payload);
                if (client.gui.screen() != currentScreen) {
                    client.gui.setScreen(currentScreen);
                }
            }
        } else if (payload.action() == SelfHarmNetwork.DONE) {
            activeState = payload;
            if (currentScreen != null && currentScreen.state.sessionId() == payload.sessionId()) {
                currentScreen.applyUpdate(payload);
                currentScreen.closingAfterCut = true;
            } else {
                reset();
            }
        } else {
            reset();
        }
    }

    private void applyUpdate(SelfHarmNetwork.State next) {
        if (next.action() == SelfHarmNetwork.HESITATE) {
            recoilTicks = 5;
            float tension = 1f - next.hesitationsLeft() / (float) Math.max(1, next.maxHesitations());
            MedicalClient.sound("thought_type", 0.35f + 0.35f * tension, 0.75f + 0.5f * tension);
        } else if (next.action() == SelfHarmNetwork.CUT || next.action() == SelfHarmNetwork.DONE) {
            cutAnimTicks = 8;
            flashTicks = 8;
            recoilTicks = 0;
            MedicalClient.sound("heartbeat_heavy", 0.75f, 1.05f);
            if (minecraft != null && minecraft.player != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_HURT, 1.0f, 0.85f));
            }
        }
        this.state = next;
    }

    @Override protected void init() {
        scale = Math.max(.25f, Math.min(1f, Math.min((width - 32f) / 280f, (height - 64f) / 286f)));
        ox = (int) ((width - 256 * scale) / 2);
        oy = (int) ((height - 256 * scale) / 2);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void onClose() {
        if (active()) return;
        super.onClose();
    }

    @Override public boolean keyPressed(KeyEvent event) {
        // Cannot be escaped or bypassed via keyboard shortcuts while the compulsion is active.
        return true;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != SDLMouse.SDL_BUTTON_LEFT) return true;
        if (!closingAfterCut && cutAnimTicks <= 0 && state.active()
                && ClientPlayNetworking.canSend(SelfHarmNetwork.Click.TYPE)) {
            ClientPlayNetworking.send(new SelfHarmNetwork.Click(state.sessionId()));
            recoilTicks = Math.max(recoilTicks, 4);
        }
        return true;
    }

    @Override public void tick() {
        tickCount++;
        if (recoilTicks > 0) recoilTicks--;
        if (flashTicks > 0) flashTicks--;
        if (cutAnimTicks > 0) cutAnimTicks--;
        if (minecraft == null || minecraft.player == null || !minecraft.player.isAlive() || AftermathClient.active()) {
            reset();
            return;
        }
        if (closingAfterCut && cutAnimTicks <= 0) {
            reset();
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int bgAlpha = flashTicks > 0 ? 0xD81E0407 : 0xCC060508;
        g.fill(0, 0, width, height, bgAlpha);
        if (flashTicks > 0) {
            int flash = Math.round(255 * Math.min(0.45f, flashTicks / 16f));
            g.fill(0, 0, width, height, (flash << 24) | 0xA80B12);
        }

        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(scale, scale);

        // Human Steve-proportioned forearm silhouette in schematic black/white CU style (no claws).
        int armX = 36, armY = 148, armW = 184, armH = 44, armDepth = 12;
        CuMedicalHand.cube(g, armX, armY, armW, armH, armDepth, 0xFF141115, 0xFFDADADA);

        // Render completed slash marks and active cut progress on the forearm.
        int shownCuts = state.cutsDone();
        for (int idx = 0; idx < shownCuts; idx++) {
            float progress = (idx == shownCuts - 1 && cutAnimTicks > 0)
                    ? Math.clamp(1f - (cutAnimTicks - partialTick) / 8f, 0.1f, 1f)
                    : 1f;
            drawCutWound(g, 76 + idx * 38, armY + 10, progress);
        }

        // Compute hand + held weapon position: lowers & trembles as hesitations approach 0, then plunges on cut.
        float tension = 1f - state.hesitationsLeft() / (float) Math.max(1, state.maxHesitations());
        float shakeAmp = 1.2f + tension * 4.5f + (recoilTicks > 0 ? 3.0f : 0f);
        double time = tickCount + partialTick;
        double jitterX = Math.sin(time * 2.3) * shakeAmp + Math.cos(time * 4.1) * (shakeAmp * 0.45);
        double jitterY = Math.cos(time * 2.9) * (shakeAmp * 0.65);

        double stabProgress = cutAnimTicks > 0
                ? Math.sin(Math.clamp(1f - (cutAnimTicks - partialTick) / 8f, 0f, 1f) * Math.PI)
                : 0.0;
        double nudge = recoilTicks > 0 ? Math.sin((recoilTicks - partialTick) / 5.0 * Math.PI) * 10.0 : 0.0;

        int activeCutIndex = Math.min(2, Math.max(0, state.cutsDone() - (cutAnimTicks > 0 ? 1 : 0)));
        double targetX = 86 + activeCutIndex * 38;
        double handX = targetX + 18 + jitterX + stabProgress * 18;
        double handY = 58 + tension * 34 + nudge + jitterY + stabProgress * 64;
        float handRot = (float) (-0.32f - tension * 0.14f + stabProgress * 0.42f);

        // Draw the actual Minecraft weapon held inside the Steve hand cuboid.
        ItemStack weapon = state.weapon();
        if (!weapon.isEmpty()) {
            g.pose().pushMatrix();
            g.pose().translate((float) (handX - 18 - stabProgress * 6), (float) (handY + 14 + stabProgress * 10));
            g.pose().rotate((float) Math.toRadians(135 + stabProgress * 22));
            g.pose().scale(2.2f, 2.2f);
            g.item(weapon, -8, -8);
            g.pose().popMatrix();
        }

        CuMedicalHand.extract(g, handX, handY, handRot, true);

        // Minimalist LMB prompt below the forearm.
        String prompt = Component.translatable("abjection.self_harm.prompt").getString();
        int promptAlpha = Math.round(135 + 95 * (0.5f + 0.5f * (float) Math.sin(time * 0.25)));
        g.centeredText(font, prompt, 128, 224, (promptAlpha << 24) | 0xE6D8DC);

        g.pose().popMatrix();
    }

    private static void drawCutWound(GuiGraphicsExtractor g, int startX, int startY, float progress) {
        int len = Math.max(2, Math.round(28 * progress));
        for (int step = 0; step < len; step++) {
            int px = startX + step / 2;
            int py = startY + step;
            g.fill(px - 1, py, px + 2, py + 2, 0xFF8C0D19);
            g.fill(px, py, px + 1, py + 1, 0xFFE02836);
        }
        if (progress > 0.6f) {
            g.fill(startX + len / 3, startY + len / 2, startX + len / 3 + 2, startY + len / 2 + 10, 0xCC7A0812);
        }
    }
}
