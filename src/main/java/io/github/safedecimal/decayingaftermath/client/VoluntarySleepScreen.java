package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLMouse;

/** Shown ONLY during voluntary self-initiated sleep; automatically vanishes if unconscious/fainted. */
public final class VoluntarySleepScreen extends Screen {
    public VoluntarySleepScreen() {
        super(Component.translatable("abjection.sleep.title"));
    }

    public static void sync(net.minecraft.client.Minecraft client) {
        if (!ClientMedicalState.received() || client.player == null || !client.player.isAlive() || AftermathClient.active()) {
            if (client.gui.screen() instanceof VoluntarySleepScreen) client.gui.setScreen(null);
            return;
        }
        BodySnapshot body = ClientMedicalState.snapshot();
        if (body.voluntarySleeping() && body.mode() == BodySnapshot.Mode.CONSCIOUS && !SelfHarmScreen.active()) {
            if (client.gui.screen() == null) client.gui.setScreen(new VoluntarySleepScreen());
        } else if (client.gui.screen() instanceof VoluntarySleepScreen) {
            client.gui.setScreen(null);
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void tick() {
        if (minecraft == null || minecraft.player == null || !minecraft.player.isAlive()
                || AftermathClient.active() || !ClientMedicalState.received()) {
            onClose();
            return;
        }
        BodySnapshot body = ClientMedicalState.snapshot();
        if (!body.voluntarySleeping() || body.mode() != BodySnapshot.Mode.CONSCIOUS) {
            onClose();
        }
    }

    private int[] buttonRect() {
        var box = ConsciousnessLayout.at(width, height);
        int bw = 96;
        int bh = 20;
        int bx = width / 2 - bw / 2;
        int by = Math.min(height - bh - 12, box.y() + box.diameter() + 10);
        return new int[]{bx, by, bw, bh};
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != SDLMouse.SDL_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        if (!ClientMedicalState.received()) return false;
        BodySnapshot body = ClientMedicalState.snapshot();
        if (!body.voluntarySleeping() || body.mode() != BodySnapshot.Mode.CONSCIOUS) {
            onClose();
            return true;
        }
        int[] r = buttonRect();
        if (event.x() >= r[0] && event.x() < r[0] + r[2] && event.y() >= r[1] && event.y() < r[1] + r[3]) {
            ClientMedicalState.wakeFromSleep();
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!ClientMedicalState.received()) return;
        BodySnapshot body = ClientMedicalState.snapshot();
        if (!body.voluntarySleeping() || body.mode() != BodySnapshot.Mode.CONSCIOUS) return;
        int[] r = buttonRect();
        boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover ? 0xE02D3E48 : 0xD0192228);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + 1, 0xFF9AC9E0);
        g.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], 0xFF9AC9E0);
        g.fill(r[0], r[1], r[0] + 1, r[1] + r[3], 0xFF9AC9E0);
        g.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], 0xFF9AC9E0);
        g.centeredText(font, Component.translatable("abjection.sleep.awake").getString(),
                r[0] + r[2] / 2, r[1] + 6, hover ? 0xFFFFFFFF : 0xFFD9EEF8);
    }
}
