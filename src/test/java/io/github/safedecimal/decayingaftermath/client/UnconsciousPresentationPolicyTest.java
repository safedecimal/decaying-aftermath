package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnconsciousPresentationPolicyTest {
    private static BodySnapshot body(BodySnapshot.Mode mode, float oxygen, float blood, float bpm) {
        var parts = new BodySnapshot.PartSnapshot[6];
        Arrays.fill(parts, new BodySnapshot.PartSnapshot(1, 0, 0, false, false, false, false));
        return new BodySnapshot(.5f, blood, oxygen, 0, 0, bpm, mode, 1, parts);
    }

    @Test void faintingSilencesTheWorldButNotTheMedicalCues() {
        var fainted = body(BodySnapshot.Mode.UNCONSCIOUS, 1, 1, 72);
        assertTrue(UnconsciousPresentationPolicy.worldSoundsMuted(fainted));
        assertTrue(UnconsciousPresentationPolicy.hintsHidden(fainted));
        assertTrue(UnconsciousPresentationPolicy.vanillaMusicMuted(fainted));
    }

    @Test void consciousPlayerKeepsWorldAudioAndMusic() {
        var conscious = body(BodySnapshot.Mode.CONSCIOUS, 1, 1, 72);
        assertFalse(UnconsciousPresentationPolicy.worldSoundsMuted(conscious));
        assertFalse(UnconsciousPresentationPolicy.hintsHidden(conscious));
        assertFalse(UnconsciousPresentationPolicy.vanillaMusicMuted(conscious));
    }

    @Test void criticalPhysiologyStopsMinecraftMusicButNotTheWorld() {
        var critical = body(BodySnapshot.Mode.CONSCIOUS, .4f, 1, 130);
        assertFalse(UnconsciousPresentationPolicy.worldSoundsMuted(critical));
        assertFalse(UnconsciousPresentationPolicy.hintsHidden(critical));
        assertTrue(UnconsciousPresentationPolicy.vanillaMusicMuted(critical));
    }

    @Test void healthThresholdBoundariesAreNotInvented() {
        assertFalse(UnconsciousPresentationPolicy.vanillaMusicMuted(body(BodySnapshot.Mode.CONSCIOUS, .5f, 1, 72)));
        assertTrue(UnconsciousPresentationPolicy.vanillaMusicMuted(body(BodySnapshot.Mode.CONSCIOUS, .499f, 1, 72)));
    }

    @Test void dyingAndDeadHaveNoVanillaSoundtrack() {
        assertTrue(UnconsciousPresentationPolicy.vanillaMusicMuted(body(BodySnapshot.Mode.DYING, .3f, .2f, 60)));
        assertTrue(UnconsciousPresentationPolicy.vanillaMusicMuted(body(BodySnapshot.Mode.DEAD, 0, 0, 0)));
    }

    @Test void missingBodyNeverMutesAnything() {
        assertFalse(UnconsciousPresentationPolicy.worldSoundsMuted(null));
        assertFalse(UnconsciousPresentationPolicy.hintsHidden(null));
        assertFalse(UnconsciousPresentationPolicy.vanillaMusicMuted(null));
    }

    @Test void worldSoundsAreDroppedOnlyWhileFainted() {
        var fainted = body(BodySnapshot.Mode.UNCONSCIOUS, 1, 1, 72);
        var conscious = body(BodySnapshot.Mode.CONSCIOUS, 1, 1, 72);
        var chicken = sound("minecraft:entity.chicken.ambient");
        var heartbeat = sound("decaying_aftermath:heartbeat");
        assertFalse(UnconsciousPresentationPolicy.keepsPlayback(fainted, chicken));
        assertTrue(UnconsciousPresentationPolicy.keepsPlayback(fainted, heartbeat));
        assertTrue(UnconsciousPresentationPolicy.keepsPlayback(conscious, chicken));
    }

    @Test void otherModsAreThirdPartySoundsToo() {
        var fainted = body(BodySnapshot.Mode.UNCONSCIOUS, 1, 1, 72);
        assertFalse(UnconsciousPresentationPolicy.keepsPlayback(fainted, sound("sodium:ui.click")));
        assertFalse(UnconsciousPresentationPolicy.keepsPlayback(fainted, null));
    }

    private static net.minecraft.client.resources.sounds.SoundInstance sound(String id) {
        return net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                        net.minecraft.resources.Identifier.parse(id)), 1, 1);
    }
}
