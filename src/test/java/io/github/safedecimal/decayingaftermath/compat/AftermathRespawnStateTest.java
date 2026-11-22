package io.github.safedecimal.decayingaftermath.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AftermathRespawnStateTest {
    @Test void staleSpectatorNeverBecomesNormalRespawnModeButHardcoreIsPreserved() {
        assertEquals(0,io.github.safedecimal.decayingaftermath.medical.AftermathPolicy.returnGameModeId(3,false));
        assertEquals(0,io.github.safedecimal.decayingaftermath.medical.AftermathPolicy.returnGameModeId(0,false));
        assertEquals(1,io.github.safedecimal.decayingaftermath.medical.AftermathPolicy.returnGameModeId(1,false));
        assertEquals(2,io.github.safedecimal.decayingaftermath.medical.AftermathPolicy.returnGameModeId(2,false));
        assertEquals(3,io.github.safedecimal.decayingaftermath.medical.AftermathPolicy.returnGameModeId(0,true));
    }
    private String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/"+name));
    }
    @Test void newPlayerIsBoundToItsPacketListenerAndUsesVanillaLoadReset() throws Exception {
        String code=source("server/ServerAftermathSystem.java");
        assertTrue(code.contains("player.connection.player=player"));
        assertTrue(code.contains("player.connection.resetPosition()"));
        assertTrue(code.contains("decayingAftermath$restartAfterRespawn()"));
    }
    @Test void terminalStateIsClearedBeforeReturningToLiveMode() throws Exception {
        String code=source("server/ServerAftermathSystem.java");
        int finish=code.indexOf("private static void finish");
        assertTrue(code.indexOf("SESSIONS.remove(player.getUUID())",finish)<code.indexOf("player.setGameMode",finish));
        assertTrue(code.contains("ServerMedicalSystem.restoreAfterDeath(player)"));
        assertTrue(code.contains("new ClientboundSetHealthPacket"));
    }
    @Test void copyFromDoesNotPreserveADeadMedicalBody() throws Exception {
        assertTrue(source("server/ServerMedicalSystem.java").contains("alive && get(oldPlayer).mode()!=Mode.DEAD"));
    }
    @Test void leavingSceneRestoresCameraOwnershipAndDoesNotLockOnStaleHealth() throws Exception {
        String code=source("client/AftermathClient.java");
        assertTrue(code.contains("client.setCameraEntity(client.player)"));
        assertTrue(code.contains("return active();"));
        assertFalse(code.contains("return active() || (client.player!=null && !client.player.isAlive()"));
    }
}
