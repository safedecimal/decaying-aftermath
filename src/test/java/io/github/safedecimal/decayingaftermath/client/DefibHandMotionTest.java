package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefibHandMotionTest {
    @Test void handUsesCuInertiaRatherThanTeleportingToMouse() {
        var hand=new DefibHandMotion();hand.beginTick();hand.step(300,200,0,1,false);
        assertTrue(hand.x()>0&&hand.x()<300);assertTrue(hand.y()>0&&hand.y()<200);
        for(int i=0;i<600;i++)hand.step(300,200,0,1,false);
        assertEquals(300,hand.x(),.01);assertEquals(200,hand.y(),.01);
    }
    @Test void sourceDialRadiusConstrainsPhysicsAndInterpolatedVisualPositions() {
        var hand=new DefibHandMotion();hand.beginTick();
        for(int i=0;i<200;i++)hand.step(524,524,0,1,true);
        assertTrue(Math.hypot(hand.x()+225,hand.y()+174)<=75.000001);
        for(float partial:new float[]{0,.25f,.5f,1})
            assertTrue(Math.hypot(hand.visualX(partial,true)+225,hand.visualY(partial,true)+174)<=75.000001);
    }
    @Test void handCannotActWithUnconsciousOrNonFiniteConsciousness() {
        var hand=new DefibHandMotion();hand.step(300,300,0,0,false);
        assertEquals(0,hand.x());assertEquals(0,hand.y());
        hand.step(300,300,0,Float.NaN,false);assertEquals(0,hand.x());
    }
}
