package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.network.Try3dPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class New3dSystemTest {
    @Test
    void toggleFlipsStateCleanlyAndPayloadRoundTrips() {
        New3dSystem.set(false);
        assertFalse(New3dSystem.enabled());
        assertTrue(New3dSystem.toggle());
        assertTrue(New3dSystem.enabled());
        assertFalse(New3dSystem.toggle());
        assertFalse(New3dSystem.enabled());

        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            Try3dPayload.CODEC.encode(buf, new Try3dPayload(true));
            assertTrue(Try3dPayload.CODEC.decode(buf).enabled());
            assertEquals(buf.writerIndex(), buf.readerIndex());
        } finally {
            buf.release();
        }
    }

    @Test
    void commandAndSkinHandAnd3dMinigamesAreWired() throws Exception {
        String cmds = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/server/MedicalCommands.java"));
        assertTrue(cmds.contains("Commands.literal(\"trynew3dsystem\")"));

        String hand = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client/CuMedicalHand.java"));
        assertTrue(hand.contains("extractPlayerSkinArm2d"));
        assertTrue(hand.contains("Minigame3dRenderer.draw3dHandAt"));

        String proc = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client/MedicalProcedureScreen.java"));
        assertTrue(proc.contains("Minigame3dRenderer.draw3dProcedureLimb"));

        String defib = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client/DefibScreen.java"));
        assertTrue(defib.contains("Minigame3dRenderer.draw3dDefibChest"));

        String sh = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client/SelfHarmScreen.java"));
        assertTrue(sh.contains("Minigame3dRenderer.draw3dSelfHarmForearm"));
    }
}
