package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import org.lwjgl.sdl.SDLMouse;
import static org.junit.jupiter.api.Assertions.*;

class DefibScreenInputTest {
    @Test void usesSdlMouseNumbersNotInventoryContainerNumbers() {
        assertEquals(1,SDLMouse.SDL_BUTTON_LEFT);assertEquals(3,SDLMouse.SDL_BUTTON_RIGHT);
    }
    @Test void actualLeftClickDragsAndReleasesAedElectrodesWithoutClosing()throws Exception {
        assertTrue(DefibScreenClickProbe.exercise().contains("AED_LMB=PAD_0,MOVE,RELEASE;window_kept=true"));
    }
    @Test void actualLeftClickTurnsDialChargesAndShocksWithoutClosing()throws Exception {
        assertTrue(DefibScreenClickProbe.exercise().contains("MANUAL_LMB=DIAL,MOVE,RELEASE,CHARGE,SHOCK;window_kept=true"));
    }
    @Test void interruptedSessionDoesNotRepurposeLeftClickAsExit()throws Exception {
        assertTrue(DefibScreenClickProbe.exercise().contains("ABORTED_LMB=window_kept;RMB=explicit_exit"));
    }
    @Test void bothHandsUseTheSameMinecraftCuboidAsOtherMinigames()throws Exception {
        assertTrue(DefibScreenClickProbe.exercise().contains("HAND=CuMedicalHand;36x108x9;no_sprite_blits;left_and_right=true"));
    }
}
