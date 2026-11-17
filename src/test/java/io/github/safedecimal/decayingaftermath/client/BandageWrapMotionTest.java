package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BandageWrapMotionTest {
    private static double advance(BandageWrapMotion wrap,double degrees) {
        double r=Math.toRadians(degrees);return wrap.advance(128+Math.sin(r)*96,128-Math.cos(r)*96);
    }
    @Test void fullCoverageDoesNotStopFurtherWraps() {
        var wrap=new BandageWrapMotion();wrap.grab(128,32);double turns=0;for(int i=1;i<=144;i++)turns+=advance(wrap,i*5);
        assertEquals(2,turns,.000001);assertEquals(1,wrap.coverage());assertTrue(wrap.angle()>700);
    }
    @Test void backtrackingCannotFarmCareOrRefill() {
        var wrap=new BandageWrapMotion();wrap.grab(128,32);advance(wrap,20);advance(wrap,40);assertEquals(0,advance(wrap,20));assertEquals(0,advance(wrap,40));
    }
    @Test void teleportingAcrossTheRingEarnsNothing() {
        var wrap=new BandageWrapMotion();wrap.grab(128,32);assertEquals(0,advance(wrap,180));assertEquals(0,wrap.angle());
    }
    @Test void paymentsUseSourceTwentyDegreeSteps() {
        var wrap=new BandageWrapMotion();wrap.grab(128,32);assertEquals(0,advance(wrap,10));assertEquals(1.0/18,advance(wrap,20),.000001);
    }
}
