package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HeartbeatFovPulseTest {
    @Test void startsAndEndsAtVanillaAndPeaksGentlyAfterTheBeat() {
        var pulse = new HeartbeatFovPulse();
        long beat = 1_000_000_000L;
        assertEquals(1, pulse.zoom(1, beat));
        pulse.onBeat(beat);
        assertEquals(1, pulse.zoom(1, beat - 1));
        assertEquals(1, pulse.zoom(1, beat));
        double first = pulse.zoom(1, beat + 16_000_000L);
        double second = pulse.zoom(1, beat + 32_000_000L);
        assertTrue(1 < first && first < second && second < 1.014);
        assertEquals(1.014, pulse.zoom(1, beat + 90_000_000L), 1e-9);
        assertEquals(1.007, pulse.zoom(1, beat + 180_000_000L), 1e-9);
        assertEquals(1, pulse.zoom(1, beat + 270_000_000L));
    }

    @Test void respectsFovEffectsAndDoesNotSnapWhenAnotherBeatArrivesEarly() {
        var pulse = new HeartbeatFovPulse();
        pulse.onBeat(0);
        assertEquals(1, pulse.zoom(0, 90_000_000L));
        assertEquals(1.007, pulse.zoom(.5, 90_000_000L), 1e-9);
        long nextBeat = 190_000_000L;
        double before = pulse.zoom(1, nextBeat);
        pulse.onBeat(nextBeat);
        assertEquals(before, pulse.zoom(1, nextBeat));
        assertTrue(pulse.zoom(1, nextBeat + 45_000_000L) > before);
        pulse.reset();
        assertEquals(1, pulse.zoom(1, nextBeat + 45_000_000L));
    }

    @Test void worldAndHandUseTheSameMagnificationWithoutReplacingTheirVanillaFov() {
        float world = HeartbeatFovPulse.applyZoom(100, 1.014);
        float hand = HeartbeatFovPulse.applyZoom(70, 1.014);
        assertTrue(world < 100 && hand < 70 && world > hand);
        assertEquals(1.014, tangent(100) / tangent(world), 1e-6);
        assertEquals(1.014, tangent(70) / tangent(hand), 1e-6);
        assertEquals(70, HeartbeatFovPulse.applyZoom(70, 1));
    }

    private static double tangent(float fov) {
        return Math.tan(Math.toRadians(fov) * .5);
    }
}
