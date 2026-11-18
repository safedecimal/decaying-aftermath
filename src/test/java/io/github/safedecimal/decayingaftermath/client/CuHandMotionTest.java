package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CuHandMotionTest {
    @Test void painAddsCoherentBoundedMotionInsteadOfTeleporting() {
        var quiet=new CuHandMotion();var painful=new CuHandMotion();var replay=new CuHandMotion();
        boolean moved=false;
        for(int i=0;i<600;i++) {
            quiet.step(128,128,false,0);painful.step(128,128,false,1);replay.step(128,128,false,1);
            moved|=Math.abs(painful.x()-128)>.1;
            assertTrue(Math.abs(painful.x()-128)<20&&Math.abs(painful.y()-128)<20);
            assertEquals(painful.x(),replay.x(),.000001);
        }
        assertTrue(moved);assertEquals(128,quiet.x());assertEquals(128,quiet.y());
    }
    @Test void handDoesNotTeleportToCursor() {
        var hand=new CuHandMotion();hand.step(228,128,false);assertTrue(hand.x()>128);assertTrue(hand.x()<140);
    }
    @Test void sourceVelocityAndWindowLimitsStayFinite() {
        var hand=new CuHandMotion();for(int i=0;i<600;i++)hand.step(100000,-100000,false);
        assertTrue(Double.isFinite(hand.x()));assertTrue(Double.isFinite(hand.y()));assertTrue(hand.x()<=259.1);assertTrue(hand.y()>=-3.1);
    }
    @Test void threeFixedStepsEqualOneClientTick() {
        var first=new CuHandMotion();var second=new CuHandMotion();
        for(int tick=0;tick<20;tick++){first.beginTick();for(int i=0;i<3;i++)first.step(200,80,false);}
        for(int i=0;i<60;i++)second.step(200,80,false);
        assertEquals(first.x(),second.x(),.000001);assertEquals(first.y(),second.y(),.000001);
    }
    @Test void visualsInterpolateAcrossTheWholeClientTick() {
        var hand=new CuHandMotion();hand.beginTick();double old=hand.x();for(int i=0;i<3;i++)hand.step(220,128,false);
        assertEquals(old,hand.visualX(0));assertEquals(hand.x(),hand.visualX(1));assertTrue(hand.visualX(.5f)>old&&hand.visualX(.5f)<hand.x());
    }
    private static int reach80(float consciousness,float energy) {
        var hand=new CuHandMotion();double goal=Math.hypot(92,92)*.8;
        for(int i=1;i<=1000;i++) {
            hand.step(220,220,false,0,consciousness,energy);
            if(Math.hypot(hand.x()-128,hand.y()-128)>=goal)return i;
        }
        return 1001;
    }
    @Test void lowEnergyDelaysArrivalRatherThanOnlyChangingOvershootPhase() {
        int normal=reach80(1,1),slow=reach80(1,.05f);
        assertTrue(slow>normal*2);assertTrue(slow<1000);
    }
    @Test void lessEnergyIsMonotonicallySlowerButDoesNotFreezeRescueInput() {
        int prior=0;
        for(float energy:new float[]{1,.75f,.5f,.25f,.05f,0}) {
            int now=reach80(1,energy);assertTrue(now>=prior);assertTrue(now<1000);prior=now;
        }
        assertTrue(reach80(.2f,1)>reach80(1,1));
    }
    @Test void exactCenterDoesNotBypassTheBandageDisk() {
        var hand=new CuHandMotion();hand.outsideRadius(250);
        assertEquals(62.5,Math.hypot(hand.x()-128,hand.y()-128),1e-6);
        for(float partial:new float[]{0,.25f,.5f,.75f,1})
            assertTrue(Math.hypot(hand.visualX(partial)-128,hand.visualY(partial)-128)>=62.5-1e-6);
    }
    @Test void frameInterpolationNeverCutsThroughTheBandageDisk() throws Exception {
        var hand=new CuHandMotion();hand.outsideRadius(250);
        for(String name:new String[]{"x","previousX","y","previousY"}) {
            var field=CuHandMotion.class.getDeclaredField(name);field.setAccessible(true);
            field.setDouble(hand,name.equals("previousX")||name.equals("y")?250:0);
        }
        for(int i=0;i<=100;i++)
            assertTrue(Math.hypot(hand.visualX(i/100f)-128,hand.visualY(i/100f)-128)>=62.5-1e-6);
    }
    @Test void collisionRetainsTangentialMovementAndAllowsReachingTheRoll() {
        var hand=new CuHandMotion();hand.outsideRadius(250);
        for(int i=0;i<300;i++) {hand.beginTick();hand.step(158,32,false);hand.outsideRadius(250);}
        assertEquals(158,hand.x(),.001);assertEquals(32,hand.y(),.001);
        for(int i=0;i<600;i++) {
            double theta=i*Math.PI/300;hand.beginTick();
            hand.step(128+Math.sin(theta)*64,128-Math.cos(theta)*64,true);hand.outsideRadius(250);
            for(float partial:new float[]{0,.25f,.5f,.75f,1})
                assertTrue(Math.hypot(hand.visualX(partial)-128,hand.visualY(partial)-128)>=62.5-1e-6);
        }
    }
    @Test void heldPainTremorIsVisibleAndQuietHandStaysQuiet() {
        double painful=heldRange(1),quiet=heldRange(0);
        assertTrue(painful>5&&painful<25);assertEquals(0,quiet,1e-6);
    }
    private static double heldRange(float pain) {
        var hand=new CuHandMotion();double min=1000,max=-1000;
        for(int i=0;i<1200;i++) {
            hand.step(128,32,true,pain,1,1);hand.outsideRadius(250);
            if(i>600) {min=Math.min(min,hand.x());max=Math.max(max,hand.x());}
        }
        return max-min;
    }
    @Test void anOutsideEndpointCannotSweepThroughToTheOtherSide() throws Exception {
        var hand=new CuHandMotion();
        for(String name:new String[]{"x","stepX","y","stepY"}) {
            var field=CuHandMotion.class.getDeclaredField(name);field.setAccessible(true);
            field.setDouble(hand,name.equals("x")?-500:name.equals("stepX")?500:0);
        }
        hand.outsideRadius(250);
        assertEquals(190.5,hand.x(),1e-6);assertEquals(128,hand.y(),1e-6);
    }
}
