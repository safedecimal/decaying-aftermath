package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MedicalOnlyHealthTest {
    @Test
    void adminSetMoodMinus100ReachesFullMinus100MoodOnMonitor() {
        var body = new BodyModel();
        body.survival().mental().adminSetMood(-1.0f);
        assertEquals(-1.0f, MedicalPhysiology.mood(body.snapshot()), 0.001f);
        assertEquals(DepressionType.MELANCHOLIC, body.snapshot().survival().depression());

        body.survival().mental().adminSetMood(0.8f);
        assertTrue(MedicalPhysiology.mood(body.snapshot()) > 0.7f);
        assertEquals(DepressionType.NONE, body.snapshot().survival().depression());
    }

    @Test
    void adminSetPartFracturePtsdDeathsAndModeWorkWithoutCorruption() {
        var body = new BodyModel();
        body.survival().mental().adminSetPtsd(0.75f);
        body.survival().mental().adminSetDeaths(4);
        body.adminSetFracture(BodyPart.LEFT_LEG, true);
        body.adminSetPart(BodyPart.LEFT_LEG, "integrity", 0.30f);
        var s = body.snapshot();
        assertTrue(s.survival().ptsd());
        assertEquals(4, s.survival().rememberedDeaths());
        assertTrue(s.part(BodyPart.LEFT_LEG).fractured());
        assertEquals(0.30f, s.part(BodyPart.LEFT_LEG).integrity(), 0.01f);

        body.adminSetMode(BodySnapshot.Mode.LAST_STANDING);
        assertEquals(BodySnapshot.Mode.LAST_STANDING, body.mode());
        body.adminSetMode(BodySnapshot.Mode.CONSCIOUS);
        assertEquals(BodySnapshot.Mode.CONSCIOUS, body.mode());
    }

    @Test
    void biteContaminationInfectsUnprotectedOpenWoundButAntisepticProtects() {
        var unprotected = new BodyModel();
        unprotected.damage(BodyPart.LEFT_ARM, 4f, DamageKind.CUT);
        unprotected.contaminateWound(BodyPart.LEFT_ARM, 0.14f);
        assertTrue(unprotected.snapshot().part(BodyPart.LEFT_ARM).infection() >= 0.14f);

        var disinfected = new BodyModel();
        disinfected.damage(BodyPart.LEFT_ARM, 4f, DamageKind.CUT);
        disinfected.treat(BodyPart.LEFT_ARM, TreatmentKind.ANTISEPTIC, 1f);
        disinfected.contaminateWound(BodyPart.LEFT_ARM, 0.14f);
        assertEquals(0f, disinfected.snapshot().part(BodyPart.LEFT_ARM).infection());
    }

    @Test
    void vanillaHpHeartsAndDamageCommandAreExcisedAndAbjectionCommandExists() throws Exception {
        String playerBody = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/mixin/PlayerBodyMixin.java"));
        assertTrue(playerBody.contains("abjection$removeVanillaHealthWrite"));
        assertTrue(playerBody.contains("abjection$medicalDeathCheckOnly"));
        assertTrue(playerBody.contains("abjection$medicalKill"));
        assertTrue(playerBody.contains("ServerMedicalSystem.applyIncomingHit"));
        assertFalse(playerBody.contains("original.call(level, source, unmitigatedAmount);\n                scope.complete()"));

        String hearts = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client/mixin/ConditionHeartsMixin.java"));
        assertTrue(hearts.contains("ci.cancel()"));
        assertFalse(hearts.contains("DECAYING_FULL"));

        String damageCmd = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/mixin/DamageCommandMixin.java"));
        assertTrue(damageCmd.contains("DamageCommand.class"));
        assertTrue(damageCmd.contains("ci.cancel()"));

        String killCmd = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/mixin/KillCommandMixin.java"));
        assertTrue(killCmd.contains("ServerMedicalSystem.killMedically"));

        String serverSys = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/server/ServerMedicalSystem.java"));
        assertTrue(serverSys.contains("decayingAftermath$isVanillaDead()"));
        assertFalse(serverSys.contains("if (player.isAlive()) {\n            player.die(source);"));

        String cmds = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/server/MedicalCommands.java"));
        assertTrue(cmds.contains("buildRoot(\"abjection\")"));
        assertTrue(cmds.contains("Commands.literal(\"mood\")"));
        assertTrue(cmds.contains("Commands.literal(\"depression\")"));
        assertTrue(cmds.contains("Commands.literal(\"ptsd\")"));
        int reviveIdx = cmds.indexOf("Commands.literal(\"revivenow\")");
        assertTrue(reviveIdx > 0);
        String reviveSnippet = cmds.substring(reviveIdx, Math.min(cmds.length(), reviveIdx + 360));
        assertTrue(reviveSnippet.contains(".requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))"));
        assertTrue(reviveSnippet.contains("EntityArgument.players()"));
        assertTrue(cmds.contains("ServerMedicalSystem.reviveNow(target)"));
    }
}
