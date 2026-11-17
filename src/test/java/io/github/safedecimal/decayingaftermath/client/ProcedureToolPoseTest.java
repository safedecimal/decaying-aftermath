package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcedureToolPoseTest {
    @Test void toolAndHandUseSameClientTickInterpolation() {
        assertEquals(10,ProcedureToolPose.interpolate(10,30,0));
        assertEquals(20,ProcedureToolPose.interpolate(10,30,.5f));
        assertEquals(30,ProcedureToolPose.interpolate(10,30,1));
        assertEquals(10,ProcedureToolPose.interpolate(10,30,-1));
        assertEquals(30,ProcedureToolPose.interpolate(10,30,2));
    }
    @Test void rotatingToolsKeepTheirOriginalLocalGrip() {
        for(double angle:new double[]{-18,0,65,137}) {
            var local=ProcedureToolPose.localGrip(128,70,116,47,11.5,123,angle);
            var world=ProcedureToolPose.grip(116,47,11.5,123,local.x(),local.y(),angle);
            assertEquals(128,world.x(),.000001);assertEquals(70,world.y(),.000001);
        }
    }
    @Test void gripTracksInterpolatedToolTranslationNotAnotherClock() {
        var local=ProcedureToolPose.localGrip(128,100,110,80,16,16,0);
        double x=ProcedureToolPose.interpolate(110,150,.25f),y=ProcedureToolPose.interpolate(80,60,.25f);
        var grip=ProcedureToolPose.grip(x,y,16,16,local.x(),local.y(),0);
        assertEquals(138,grip.x());assertEquals(95,grip.y());
    }
    @Test void contactPointFollowsTheActualRotatedBottle() {
        var grip=ProcedureToolPose.grip(100,80,16,16,0,-8,90);
        assertEquals(124,grip.x(),.000001);assertEquals(96,grip.y(),.000001);
    }
}
