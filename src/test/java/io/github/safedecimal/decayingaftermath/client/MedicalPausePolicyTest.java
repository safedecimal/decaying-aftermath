package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalPausePolicyTest {
    private static BodySnapshot mode(BodySnapshot.Mode mode) {
        var b=new BodyModel().snapshot();return new BodySnapshot(1,1,1,0,0,72,mode,1,1,1,b.parts());
    }
    @Test void unconsciousDyingAndLastStandingCannotPause() {
        for(var m:new BodySnapshot.Mode[]{BodySnapshot.Mode.UNCONSCIOUS,BodySnapshot.Mode.DYING,BodySnapshot.Mode.LAST_STANDING})
            assertTrue(MedicalPausePolicy.blocked(mode(m)));
    }
    @Test void healthyAndActuallyDeadPlayersHaveNormalMenus() {
        assertFalse(MedicalPausePolicy.blocked(new BodyModel().snapshot()));assertFalse(MedicalPausePolicy.blocked(mode(BodySnapshot.Mode.DEAD)));assertFalse(MedicalPausePolicy.blocked(null));
    }
    @Test void arrestAndDangerousReservesCannotPauseWhileConscious() {
        var b=new BodyModel().snapshot();assertTrue(MedicalPausePolicy.blocked(b.withCardiacTicks(-600)));
        assertTrue(MedicalPausePolicy.blocked(new BodySnapshot(1,.2f,1,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,b.parts())));
        assertTrue(MedicalPausePolicy.blocked(new BodySnapshot(1,1,.4f,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,b.parts())));
    }
    @Test void ordinaryExhaustionDoesNotLockPause() {
        var b=new BodyModel().snapshot();assertFalse(MedicalPausePolicy.blocked(new BodySnapshot(1,1,1,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,0,1,b.parts())));
    }
    @Test void realPauseEntryPointsAreGuardedAndChatDoesNotHideTheOverlay() throws Exception {
        var root=Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client");
        var c=Files.readString(root.resolve("MedicalClient.java"));assertFalse(c.contains("if(screen!=null&&!(screen instanceof MedicalInventoryScreen)"));
        assertTrue(c.contains("abjection.pause.denied"));assertTrue(c.contains("color(.65*Math.min(1,pauseHintTicks/20d)"));
        assertTrue(Files.readString(root.resolve("mixin/MedicalPauseMixin.java")).contains("pauseGame(Z)V"));
        assertTrue(Files.readString(root.resolve("mixin/MedicalPauseScreenMixin.java")).contains("setPauseScreen(ZZ)V"));
    }
}
