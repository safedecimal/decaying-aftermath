package io.github.safedecimal.decayingaftermath.medical;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminHealTest {
    @Test void aFreshBodyIsCleanIncludingAllSavedMedicalTimers() {
        var b=new BodyModel();b.damage(BodyPart.LEFT_ARM,12,DamageKind.BURN);b.treat(BodyPart.LEFT_ARM,TreatmentKind.FENTANYL,1);b.poison(1);b.irradiate(2);b.reset();
        var s=b.snapshot();assertEquals(1,s.blood());assertEquals(1,s.oxygen());assertEquals(1,s.energy());assertEquals(1,s.brainHealth());assertEquals(0,s.drug());assertEquals(0,s.pain());assertEquals(0,s.cardiacTicks());assertEquals(BodySnapshot.Mode.CONSCIOUS,s.mode());
        for(var p:s.parts()){assertEquals(1,p.integrity());assertEquals(0,p.bleeding());assertEquals(0,p.infection());assertEquals(0,p.burn());assertFalse(p.fractured());assertFalse(p.tourniquet());assertFalse(p.foreignBody());}
        for(int t:b.saveState().careTicks())assertEquals(0,t);assertEquals(0,b.saveState().lastStandingTicks());assertFalse(b.saveState().lastStandingSpent());
    }
    @Test void healCommandHasOperatorPermissionBothAtRegistrationAndExecution() throws Exception {
        var s=Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/server/MedicalCommands.java"));
        assertTrue(s.contains("Commands.literal(\"heal\")"));assertTrue(s.contains(".requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))"));
        assertTrue(s.contains("Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)"));assertTrue(s.contains("source.getPlayerOrException()"));
    }
    @Test void fullHealCancelsProceduresAndResetsTheWholeBodyNotJustVanillaHp() throws Exception {
        var s=Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/server/ServerMedicalSystem.java"));
        assertTrue(s.contains("cancelProcedures(player)"));assertTrue(s.contains("DefibNetwork.cancelFor(player)"));
        assertTrue(s.contains("player.removeAllEffects();player.clearFire();player.setTicksFrozen(0)"));assertTrue(s.contains("decayingAftermath$setBody(new BodyModel())"));
        assertTrue(s.contains("MedicalNetwork.thought(player,\"abjection.heal.done\")"));
    }
}
