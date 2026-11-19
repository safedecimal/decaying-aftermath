package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HeartbeatFovPulseTest {
    @Test
    void contractsOnBeatThenRecoversBetweenClientTicks() {
        var pulse = new HeartbeatFovPulse();
        long beat = 1_000_000_000L;
        assertEquals(90f, pulse.apply(90f, 1, beat));

        pulse.onBeat(beat);
        assertEquals(90f, pulse.apply(90f, 1, beat - 1));
        assertEquals(87.3f, pulse.apply(90f, 1, beat), .0001f);
        float firstFrame = pulse.apply(90f, 1, beat + 16_000_000L);
        float secondFrame = pulse.apply(90f, 1, beat + 32_000_000L);
        assertTrue(87.3f < firstFrame && firstFrame < secondFrame && secondFrame < 90f);
        assertEquals(90f, pulse.apply(90f, 1, beat + 220_000_000L));
        assertEquals(90f, pulse.apply(90f, 1, beat + 900_000_000L));
    }

    @Test
    void respectsVanillaFovScaleAndRestartsOnlyAtAnotherBeat() {
        var pulse = new HeartbeatFovPulse();
        pulse.onBeat(0);
        assertEquals(100f, pulse.apply(100f, 0, 0));
        assertEquals(98.5f, pulse.apply(100f, .5, 0), .0001f);
        assertEquals(97f, pulse.apply(100f, 1, 0), .0001f);
        pulse.onBeat(100_000_000L);
        assertEquals(97f, pulse.apply(100f, 1, 100_000_000L), .0001f);
        pulse.reset();
        assertEquals(100f, pulse.apply(100f, 1, 100_000_000L));
    }
}
