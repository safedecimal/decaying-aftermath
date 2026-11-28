package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import io.github.safedecimal.decayingaftermath.network.SelfHarmNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SelfHarmPolicyTest {
    private static BodySnapshot depressedSnapshot(DepressionType type, float moodReserve, float anxiolytic, BodySnapshot.Mode mode) {
        var base = new BodyModel().snapshot();
        var s = new SurvivalSnapshot(1, 1, 37, 0, 0, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0,
                75, 0, 0, moodReserve, type, 0, anxiolytic, 0, 0, 0, 0, false);
        return new BodySnapshot(1, 1, 1, 0, 0, 72, mode, 1, 1, 1, base.parts(), s);
    }

    @Test
    void triggersDuringSevereDepressionAndIsSuppressedByAnxiolyticOrUnconsciousness() {
        var healthy = new BodyModel().snapshot();
        assertFalse(SelfHarmPolicy.eligible(healthy));

        var melancholic = depressedSnapshot(DepressionType.MELANCHOLIC, -0.75f, 0f, BodySnapshot.Mode.CONSCIOUS);
        assertTrue(SelfHarmPolicy.eligible(melancholic));
        assertTrue(SelfHarmPolicy.requiredCuts(melancholic) >= 1);

        var lowMoodOnly = depressedSnapshot(DepressionType.SITUATIONAL, -1.0f, 0f, BodySnapshot.Mode.CONSCIOUS);
        var lowParts = new BodyModel().snapshot().parts();
        lowParts[BodyPart.TORSO.ordinal()] = new BodySnapshot.PartSnapshot(0.5f, 0f, 0.9f, false, false, false, false);
        var bleakMood = new BodySnapshot(0.5f, 1, 1, 0.9f, 0, 72, BodySnapshot.Mode.CONSCIOUS, 1, 0.05f, 1, lowParts, lowMoodOnly.survival());
        assertTrue(MedicalPhysiology.mood(bleakMood) <= -0.70f);
        assertTrue(SelfHarmPolicy.eligible(bleakMood));

        var sedated = depressedSnapshot(DepressionType.MELANCHOLIC, -0.75f, 0.6f, BodySnapshot.Mode.CONSCIOUS);
        assertFalse(SelfHarmPolicy.eligible(sedated), "Anxiolytic suppresses self-harm compulsion");

        var fainted = depressedSnapshot(DepressionType.PSYCHOTIC, -0.95f, 0f, BodySnapshot.Mode.UNCONSCIOUS);
        assertFalse(SelfHarmPolicy.eligible(fainted));
    }

    @Test
    void selfHarmInflictsPhysicalCutPainAndBleedingWhileNumbingEmotionalDistress() {
        var body = BodyModel.restore(depressedSnapshot(DepressionType.MELANCHOLIC, -0.85f, 0f, BodySnapshot.Mode.CONSCIOUS));
        body.kill();
        var afterDeath = body.respawnAfterDeath();
        float beforeMood = afterDeath.snapshot().survival().moodReserve();
        float beforeIntegrity = afterDeath.snapshot().part(BodyPart.LEFT_ARM).integrity();
        assertTrue(afterDeath.snapshot().survival().ptsdFlashback());

        afterDeath.applySelfHarm(BodyPart.LEFT_ARM, 4.8f, 0.28f);
        var snap = afterDeath.snapshot();
        var arm = snap.part(BodyPart.LEFT_ARM);

        assertTrue(arm.integrity() < beforeIntegrity);
        assertTrue(arm.pain() > 0f);
        assertTrue(arm.bleeding() > 0f);
        assertTrue(arm.openWound());
        assertTrue(snap.survival().moodReserve() > beforeMood, "Physical pain numbs emotional pain");
        assertFalse(snap.survival().ptsdFlashback());
        assertEquals(1, snap.survival().rememberedDeaths(), "Self-harm does not erase death memories");
    }

    @Test
    void recognizesWeaponsAndRejectsOrdinaryFoodOrBlocks() {
        assertTrue(SelfHarmPolicy.isWeaponId("minecraft:iron_sword"));
        assertTrue(SelfHarmPolicy.isWeaponId("minecraft:diamond_axe"));
        assertTrue(SelfHarmPolicy.isWeaponId("minecraft:shears"));
        assertTrue(SelfHarmPolicy.isWeaponId("abjection:tweezers"));
        assertTrue(SelfHarmPolicy.isHeavyBladeId("minecraft:netherite_sword"));
        assertFalse(SelfHarmPolicy.isHeavyBladeId("minecraft:shears"));
        assertFalse(SelfHarmPolicy.isWeaponId("minecraft:bread"));
        assertFalse(SelfHarmPolicy.isWeaponId("minecraft:dirt"));
    }

    @Test
    void selfHarmNetworkPayloadsRoundTrip() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var state = new SelfHarmNetwork.State(true, 42, SelfHarmNetwork.OPEN, ItemStack.EMPTY,
                    BodyPart.LEFT_ARM.ordinal(), 2, 0, 6, 6);
            SelfHarmNetwork.State.CODEC.encode(buf, state);
            var decoded = SelfHarmNetwork.State.CODEC.decode(buf);
            assertEquals(state.active(), decoded.active());
            assertEquals(state.sessionId(), decoded.sessionId());
            assertEquals(state.action(), decoded.action());
            assertEquals(state.targetPartOrdinal(), decoded.targetPartOrdinal());
            assertEquals(state.cutsLeft(), decoded.cutsLeft());
            assertEquals(state.hesitationsLeft(), decoded.hesitationsLeft());
            assertEquals(buf.writerIndex(), buf.readerIndex());

            buf.clear();
            var click = new SelfHarmNetwork.Click(42);
            SelfHarmNetwork.Click.CODEC.encode(buf, click);
            assertEquals(click, SelfHarmNetwork.Click.CODEC.decode(buf));
            assertEquals(buf.writerIndex(), buf.readerIndex());
        } finally {
            buf.release();
        }
    }

    @Test
    void selfHarmMinigameCannotBeClosedAndNamespaceIsAbjection() throws Exception {
        assertEquals("abjection", DecayingAftermath.MOD_ID);
        String screen = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client/SelfHarmScreen.java"));
        assertTrue(screen.contains("shouldCloseOnEsc() { return false; }"));
        assertTrue(screen.contains("isPauseScreen() { return false; }"));
        assertTrue(screen.contains("if (active()) return;"));
        assertTrue(screen.contains("SDLMouse.SDL_BUTTON_LEFT"));

        String client = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client/MedicalClient.java"));
        assertTrue(client.contains("SelfHarmScreen.active()"));

        String items = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/medical/ItemMassPolicy.java"));
        assertTrue(items.contains("abjection:backpack"));
        assertFalse(items.contains("decaying_aftermath:"));
    }
}
