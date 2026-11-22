package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodyModel;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SleepCirclePolicyTest {
    private static BodySnapshot body(BodySnapshot.Mode mode,float progress) {
        return new BodySnapshot(.2f,.3f,.4f,0,0,72,mode,progress,1,.2f,new BodyModel().snapshot().parts());
    }
    @Test void dyingCircleIsVisibleFromFirstPacketThroughTheEntireCountdown() {
        for(float progress:new float[]{1,.9f,.5f,.05f,0})
            assertEquals(1,MedicalPresentationPolicy.sleepCircleAlpha(body(BodySnapshot.Mode.DYING,progress),false));
    }
    @Test void unconsciousCircleDoesNotWaitForFadeOrNonzeroRecoveryProgress() {
        var b=body(BodySnapshot.Mode.UNCONSCIOUS,0);var timeline=new MedicalPresentationTimeline();
        assertEquals(0,timeline.circleAlpha());assertEquals(1,MedicalPresentationPolicy.sleepCircleAlpha(b,false));
        assertEquals(.965,MedicalPresentationPolicy.sleepBlackout(b,timeline.black(),0));
    }
    @Test void pausedRecoveryAndArrestKeepTheCompleteDimTrack() {
        var b=body(BodySnapshot.Mode.UNCONSCIOUS,0);
        assertEquals(1,MedicalPresentationPolicy.sleepCircleAlpha(b,false));
        assertEquals(1,MedicalPresentationPolicy.sleepCircleAlpha(b.withCardiacTicks(-600),false));
    }
    @Test void wakingAndDeathRemoveTheCircleImmediatelyWithoutFadeGhost() {
        assertEquals(0,MedicalPresentationPolicy.sleepCircleAlpha(body(BodySnapshot.Mode.CONSCIOUS,1),false));
        assertEquals(0,MedicalPresentationPolicy.sleepCircleAlpha(body(BodySnapshot.Mode.DEAD,0),false));
        assertEquals(0,MedicalPresentationPolicy.sleepCircleAlpha(null,false));
    }
    @Test void lastStandingIntroAndModeAreNotOverlaidBySleepCircle() {
        assertEquals(0,MedicalPresentationPolicy.sleepCircleAlpha(body(BodySnapshot.Mode.UNCONSCIOUS,0),true));
        assertEquals(0,MedicalPresentationPolicy.sleepCircleAlpha(body(BodySnapshot.Mode.LAST_STANDING,1),false));
    }
    @Test void briefGlimpsesDoNotEraseTheCircleOrTheBlackoutBackground() {
        var b=body(BodySnapshot.Mode.UNCONSCIOUS,.5f);
        assertEquals(.835,MedicalPresentationPolicy.sleepBlackout(b,0,.13),1e-9);
        assertEquals(1,MedicalPresentationPolicy.sleepCircleAlpha(b,false));
        assertEquals(.965,MedicalPresentationPolicy.sleepBlackout(b,Double.NaN,Double.NaN));
        assertEquals(.965,MedicalPresentationPolicy.sleepBlackout(b,0,-10));
    }
    @Test void dyingRemainsTreatableAndDoesNotGetTheUnconsciousWorldBlackout() {
        var b=body(BodySnapshot.Mode.DYING,.9f);
        assertEquals(.2,MedicalPresentationPolicy.sleepBlackout(b,.2,.13),1e-9);
        assertEquals(.35,MedicalPresentationPolicy.blackOverlay(b,.8,true));
    }
    @Test void actualHudUsesStateVisibilityRatherThanWaitingForTimelineCircleAlpha() throws Exception {
        var source=Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client/MedicalClient.java"));
        assertTrue(source.contains("double alpha=MedicalPresentationPolicy.sleepCircleAlpha(body,TIMELINE.lastStandActive());"));
        assertTrue(source.contains("double sleepBlack=MedicalPresentationPolicy.sleepBlackout(body,TIMELINE.black(),blackoutRelief);"));
        assertFalse(source.contains("Math.max(.35, TIMELINE.circleAlpha())"));
    }
}
