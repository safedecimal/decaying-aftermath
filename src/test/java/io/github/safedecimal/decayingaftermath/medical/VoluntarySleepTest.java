package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.client.MedicalInventoryLayout;
import io.github.safedecimal.decayingaftermath.client.MedicalPresentationPolicy;
import io.github.safedecimal.decayingaftermath.network.BodyPayload;
import io.github.safedecimal.decayingaftermath.network.SleepPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VoluntarySleepTest {
    @Test
    void alertAndFullConsciousnessBlocksSleepWhileTiredBodySleepsAndAutoWakes() {
        var fresh = new BodyModel();
        assertFalse(fresh.canStartSleep(), "Cannot sleep when fully rested and 100% conscious");
        assertFalse(fresh.snapshot().canSleep());
        assertFalse(fresh.startVoluntarySleep());

        var body = new BodyModel();
        body.adminSetVitals(Float.NaN, Float.NaN, 0.70f, Float.NaN);
        assertTrue(body.canStartSleep());
        assertTrue(body.startVoluntarySleep());
        assertTrue(body.voluntarySleeping());
        assertTrue(body.snapshot().voluntarySleeping());
        assertEquals(0f, body.snapshot().sleepProgress(), 0.001f);

        // Advances progress each tick
        for (int i = 0; i < 60; i++) {
            body.tick(BodyEnvironment.temperate(false, false));
        }
        assertTrue(body.voluntarySleeping());
        assertEquals(60f / BodyModel.FULL_SLEEP_TICKS, body.snapshot().sleepProgress(), 0.001f);

        // Recovers to 100% consciousness & energy -> wakes automatically
        for (int i = 0; i < BodyModel.FULL_SLEEP_TICKS; i++) {
            body.tick(BodyEnvironment.temperate(false, false));
        }
        assertFalse(body.voluntarySleeping());
        assertFalse(body.snapshot().voluntarySleeping());
    }

    @Test
    void manualAwakeWorksOnlyWhenVoluntarilyAsleepNeverWhenUnconscious() {
        var body = new BodyModel();
        assertFalse(body.wakeVoluntarily(), "Cannot wake if not sleeping");

        body.adminSetVitals(Float.NaN, Float.NaN, 0.50f, Float.NaN);
        assertTrue(body.startVoluntarySleep());
        for (int i = 0; i < 20; i++) body.tick(BodyEnvironment.temperate(false, false));
        assertTrue(body.wakeVoluntarily(), "Manual Awake wakes from voluntary sleep");
        assertFalse(body.voluntarySleeping());

        // Fainting into UNCONSCIOUS clears voluntary sleep and rejects manual Awake
        body.adminSetVitals(Float.NaN, Float.NaN, 0.50f, Float.NaN);
        assertTrue(body.startVoluntarySleep());
        body.damage(BodyPart.HEAD, 28f, DamageKind.BLUNT);
        assertEquals(BodySnapshot.Mode.UNCONSCIOUS, body.mode());
        assertFalse(body.voluntarySleeping());
        assertFalse(body.wakeVoluntarily(), "Cannot use Awake while fainted/unconscious");
    }

    @Test
    void voluntarySleepActivatesSleepCircleAndSyncsOverNetwork() throws Exception {
        var body = new BodyModel();
        body.adminSetVitals(Float.NaN, Float.NaN, 0.50f, Float.NaN);
        assertTrue(body.startVoluntarySleep());
        for (int i = 0; i < 40; i++) body.tick(BodyEnvironment.temperate(false, false));
        var snap = body.snapshot();
        assertEquals(1.0, MedicalPresentationPolicy.sleepCircleAlpha(snap, false));
        assertTrue(MedicalPresentationPolicy.sleepBlackout(snap, 0, 0) >= 0.9);
        assertEquals(io.github.safedecimal.decayingaftermath.compat.MedicalCollapsePolicy.Posture.SLEEPING,
                io.github.safedecimal.decayingaftermath.compat.MedicalCollapsePolicy.posture(snap, true, false, false));
        assertTrue(io.github.safedecimal.decayingaftermath.compat.MedicalCollapsePolicy.collapsed(snap, true, false, false));

        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            BodyPayload.CODEC.encode(buf, new BodyPayload(snap.withBloodGroup(BloodGroup.O_POSITIVE)));
            var decoded = BodyPayload.CODEC.decode(buf).snapshot();
            assertTrue(decoded.voluntarySleeping());
            assertEquals(snap.sleepProgress(), decoded.sleepProgress(), 0.0001f);
            assertEquals(buf.writerIndex(), buf.readerIndex());

            buf.clear();
            SleepPayload.CODEC.encode(buf, new SleepPayload(true));
            assertTrue(SleepPayload.CODEC.decode(buf).wake());
        } finally {
            buf.release();
        }
    }

    @Test
    void cuSleepButtonAssetsAndAwakeScreenGuardExist() throws Exception {
        byte[] back = getClass().getClassLoader().getResourceAsStream("assets/abjection/textures/gui/medical/sleep_button_back.png").readAllBytes();
        byte[] icon = getClass().getClassLoader().getResourceAsStream("assets/abjection/textures/gui/medical/sleep_button.png").readAllBytes();
        assertEquals(32, ImageIO.read(new ByteArrayInputStream(back)).getWidth());
        assertEquals(26, ImageIO.read(new ByteArrayInputStream(icon)).getWidth());

        assertTrue(MedicalInventoryLayout.SLEEP_X >= MedicalInventoryLayout.HOTBAR_X + 182);
        String screen = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client/VoluntarySleepScreen.java"));
        assertTrue(screen.contains("if (!body.voluntarySleeping() || body.mode() != BodySnapshot.Mode.CONSCIOUS)"));

        String bedMixin = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/mixin/BedSleepMixin.java"));
        assertTrue(bedMixin.contains("startSleepInBed"));
        assertTrue(bedMixin.contains("ServerMedicalSystem.startSleep"));

        String sleepStatus = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/mixin/SleepStatusMixin.java"));
        assertTrue(sleepStatus.contains("cir.setReturnValue(false)"));
    }
}
