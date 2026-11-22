package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalScoreStateTest {
    private static BodySnapshot body(float pain,float energy,float oxygen) {
        return new BodySnapshot(1,1,oxygen,pain,0,72,BodySnapshot.Mode.CONSCIOUS,1,energy,1,new BodyModel().snapshot().parts());
    }
    @Test void criticalTrackSuppressesBothExhaustionAndPain() {
        var s=new MedicalScoreState();var a=s.advance(body(.8f,.1f,.4f),false);
        assertEquals("dying_drone",a.cue());assertTrue(a.start());assertEquals(.7f,a.volume());
        var v=MedicalSituationSoundState.volumes(body(.8f,.1f,.4f));assertEquals(0,v.exhausted());assertEquals(0,v.tired());assertEquals(0,v.pain());
    }
    @Test void normalOxygenBlackoutStillGetsTheMainScoreNotExhausted() {
        var b=new BodySnapshot(.2f,1,1,0,0,72,BodySnapshot.Mode.UNCONSCIOUS,0,.4f,.3f,new BodyModel().snapshot().parts());
        var a=new MedicalScoreState().advance(b,false);assertEquals("dying_drone",a.cue());assertTrue(a.start());
        var v=MedicalSituationSoundState.volumes(b);assertEquals(0,v.exhausted());assertEquals(0,v.tired());assertEquals(1,v.dying());
    }
    @Test void exhaustionCueStartsOnceForAnUnchangedEpisodeNotEveryClipEnd() {
        var s=new MedicalScoreState();var b=body(0,.4f,1);assertTrue(s.advance(b,false).start());
        for(int i=0;i<4000;i++)assertFalse(s.advance(b,false).start());
    }
    @Test void exhaustionIsQuietFromItsFirstSample() {
        var a=new MedicalScoreState().advance(body(0,.4f,1),false);
        assertEquals("exhausted_drone",a.cue());assertEquals(.12f,a.volume());
    }
    @Test void higherPriorityScoreStopsTheLowerTrack() {
        var s=new MedicalScoreState();s.advance(body(0,.4f,1),false);
        var a=s.advance(body(0,.4f,.4f),false);assertEquals("dying_drone",a.cue());assertTrue(a.start());assertTrue(a.stop());
    }
    @Test void thresholdFlappingDoesNotConstantlyRestartExhaustion() {
        var s=new MedicalScoreState();assertTrue(s.advance(body(0,.49f,1),false).start());
        for(int i=0;i<500;i++) {s.advance(body(0,.51f,1),false);assertFalse(s.advance(body(0,.49f,1),false).start());}
    }
    @Test void GenuineQuietRecoveryRearmsTheNextEpisode() {
        var s=new MedicalScoreState();s.advance(body(0,.4f,1),false);
        for(int i=0;i<40;i++)s.advance(body(0,1,1),false);
        assertTrue(s.advance(body(0,.4f,1),false).start());
    }
    @Test void suppressedIntroDoesNotStartACompetingScore() {
        assertFalse(new MedicalScoreState().advance(body(.8f,.1f,.4f),true).start());
    }
    @Test void productionDoesNotReplayFinishedPredeathOrCreateExhaustionLoops() throws Exception {
        var root=Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client");
        var c=Files.readString(root.resolve("MedicalClient.java"));var sound=Files.readString(root.resolve("MedicalMusicSound.java"));
        assertFalse(c.contains("ensurePlaying"));assertFalse(sound.contains("ensurePlaying"));
        assertFalse(c.contains("MedicalLoopSound.start(name"));assertFalse(c.contains("exhaustedLoop"));
        assertTrue(c.contains("MedicalMusicSound.start(action.cue(),false,action.volume())"));
    }
    @Test void medicalForegroundHasOneGuiTailHookNotAHudRegistryOrScreenDuplicate() throws Exception {
        var root=Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client");
        var c=Files.readString(root.resolve("MedicalClient.java"));assertFalse(c.contains("HudElementRegistry"));
        var hook=Files.readString(root.resolve("mixin/GuiMedicalOverlayMixin.java"));
        assertTrue(hook.contains("extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V"));
        assertTrue(hook.contains("at=@At(\"TAIL\")"));assertTrue(hook.contains("MedicalClient.extractFinalOverlay(graphics)"));
        for(String screen:new String[]{"MedicalInventoryScreen.java","MedicalProcedureScreen.java"})
            assertFalse(Files.readString(root.resolve(screen)).contains("MedicalClient.extractHud(graphics)"));
    }
}
