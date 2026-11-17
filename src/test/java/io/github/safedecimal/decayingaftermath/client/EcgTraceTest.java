package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EcgTraceTest {
    @Test void consciousnessHasItsOwnGridAndUsesOriginalHermiteKeys() {
        var trace = new EcgTrace(139,23);
        assertEquals(139,trace.width()); assertEquals(23,trace.height());
        assertEquals(.9987809062004089,EcgTrace.waveform(.28535962104797363),1e-9);
        assertEquals(-.6998956203460693,EcgTrace.waveform(.3720250725746155),1e-9);
        for(int i=0;i<600;i++)trace.follow(.05,220,(i*.05*220/60)%1);
        for(int age=0;age<trace.width();age++)assertTrue(trace.sample(age)>=0&&trace.sample(age)<23);
        trace.reset();assertEquals(0,trace.alpha(0,11));assertEquals(11,trace.sample(0));
    }
    @Test
    void originalSweepFadesBehindTheWriteHeadAndHasOnePixelHalo() {
        EcgTrace trace = new EcgTrace();
        trace.advance(0.028, 0);
        assertEquals(251, trace.alpha(0, 12));
        assertEquals(49, trace.alpha(0, 11));
        trace.advance(0.028, 0);
        assertTrue(trace.alpha(0, 12) < trace.alpha(1, 12));
        assertEquals(0, trace.alpha(0, 0));
    }

    @Test
    void pulseCurveMovesAndStaysInsideNativePixelWindow() {
        EcgTrace trace = new EcgTrace();
        for (int tick = 0; tick < 600; tick++) {
            trace.advance(0.028, 72);
        }
        int low = 24;
        int high = 0;
        for (int age = 0; age < EcgTrace.WIDTH; age++) {
            int sample = trace.sample(age);
            assertTrue(sample >= 0 && sample < EcgTrace.HEIGHT);
            low = Math.min(low, sample);
            high = Math.max(high, sample);
        }
        assertTrue(high - low >= 8, "Trace must not be a static decorative line");
    }

    @Test
    void zeroPulseIsAFlatLineAndInvalidTimeIsIgnored() {
        EcgTrace trace = new EcgTrace();
        trace.advance(Double.NaN,72);
        trace.advance(-10,72);
        for (int i = 0; i < 200; i++) {
            trace.advance(0.1,0);
        }
        for (int age = 0; age < EcgTrace.WIDTH; age++) {
            assertEquals(12,trace.sample(age));
        }
    }
}
