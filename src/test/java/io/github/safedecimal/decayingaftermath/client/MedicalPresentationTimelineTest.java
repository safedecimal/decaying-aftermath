package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalPresentationTimelineTest {
    @Test void blackoutThenOriginalSleepFillAndEcgFadeTogether() {
        var timeline = new MedicalPresentationTimeline();
        timeline.advance(Mode.UNCONSCIOUS,.5);
        assertEquals(.965,timeline.black(),1e-9); assertEquals(0,timeline.circleAlpha());
        timeline.advance(Mode.UNCONSCIOUS,.5); assertEquals(0,timeline.circleAlpha());
        timeline.advance(Mode.UNCONSCIOUS,1); assertTrue(timeline.circleAlpha()>0);
        timeline.advance(Mode.UNCONSCIOUS,3); assertEquals(1,timeline.circleAlpha());
        timeline.advance(Mode.CONSCIOUS,5); assertEquals(0,timeline.black());assertEquals(0,timeline.circleAlpha());
    }
    @Test void originalLastStandHasTwoBlackSecondsThenDelayedDroneAndEightFpsFrames() {
        var timeline = new MedicalPresentationTimeline();timeline.beginLastStand();
        timeline.advance(Mode.LAST_STANDING,2);
        assertEquals(0,timeline.lastStandFrame()); assertFalse(timeline.droneDue());
        timeline.advance(Mode.LAST_STANDING,.01);
        assertTrue(timeline.droneDue()); assertTrue(timeline.lastStandFrame()>=1);
        int frame=timeline.lastStandFrame();timeline.advance(Mode.LAST_STANDING,.125);
        assertEquals(frame%3+1,timeline.lastStandFrame());
    }
    @Test void introEndsAtSixPointSevenThenFadesForFourSeconds() {
        var timeline = new MedicalPresentationTimeline();timeline.beginLastStand();
        timeline.advance(Mode.LAST_STANDING,6.7);assertEquals(1,timeline.lastStandAlpha(),1e-9);
        timeline.advance(Mode.LAST_STANDING,2);assertEquals(.5,timeline.lastStandAlpha(),1e-9);
        assertTrue(timeline.lastStandActive());assertFalse(timeline.lastStandFlicker());
        timeline.advance(Mode.LAST_STANDING,2);assertFalse(timeline.lastStandActive());assertEquals(0,timeline.lastStandFrame());
    }
    @Test void sequenceSurvivesRecoveryAndUsesCallerUnscaledTimeButDeathCancelsIt() {
        var timeline = new MedicalPresentationTimeline();timeline.beginLastStand();
        timeline.advance(Mode.CONSCIOUS,3);assertTrue(timeline.lastStandActive());
        timeline.advance(Mode.CONSCIOUS,8);assertFalse(timeline.lastStandActive());
        timeline.beginLastStand();timeline.advance(Mode.DEAD,.1);assertFalse(timeline.lastStandActive());
    }
    @Test void invalidTimeCannotCorruptPresentationState() {
        var timeline = new MedicalPresentationTimeline();timeline.beginLastStand();
        timeline.advance(Mode.LAST_STANDING,Double.NaN);timeline.advance(Mode.LAST_STANDING,-4);
        assertEquals(0,timeline.lastStandFrame());assertEquals(1,timeline.lastStandAlpha());
    }
    private BodySnapshot dying(float grace) {
        var parts=new BodySnapshot.PartSnapshot[6];java.util.Arrays.fill(parts,new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false));
        return new BodySnapshot(.1f,.2f,.3f,.8f,0,70,Mode.DYING,grace,.2f,.35f,parts);
    }
    @Test void dyingProgressGraduallyDarkensButNeverUsesTheLastStandIntroAsADeathTimer() {
        var timeline=new MedicalPresentationTimeline();timeline.beginLastStand();timeline.advance(dying(1),.1);
        assertFalse(timeline.lastStandActive());assertTrue(timeline.black()>0);assertTrue(timeline.black()<.1);
        assertTrue(timeline.vignette()>0);assertEquals(0,timeline.circleAlpha());
        timeline.advance(dying(.1f),600);assertTrue(timeline.black()<=.55);assertTrue(timeline.vignette()<=1);
        assertFalse(timeline.lastStandActive());
    }
    @Test void medicalDyingOverlayCapKeepsTheProcedureVisible() {
        assertEquals(.35,MedicalPresentationPolicy.blackOverlay(dying(.1f),.55,true));
        assertEquals(.55,MedicalPresentationPolicy.blackOverlay(dying(.1f),.55,false));
    }
    @Test void deathFadesToNativeBlackRatherThanResettingAllVisualsInstantly() {
        var timeline=new MedicalPresentationTimeline();timeline.advance(dying(1),2);
        double before=timeline.black();timeline.advance(Mode.DEAD,.05);
        assertTrue(timeline.black()>before);assertTrue(timeline.black()<1);
        timeline.advance(Mode.DEAD,1);assertEquals(1,timeline.black());assertEquals(0,timeline.circleAlpha());
    }
}
