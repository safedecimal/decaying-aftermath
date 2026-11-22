package io.github.safedecimal.decayingaftermath.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AftermathRagdollCompatibilityTest {
    @Test void nativeDeathLifetimeCoversSceneAndHasBoundedOverflowSafeMargin() {
        assertEquals(8400,AftermathRagdollCompatibility.lifetime(7200));
        assertEquals(1200,AftermathRagdollCompatibility.lifetime(-1));
        assertEquals(73200,AftermathRagdollCompatibility.lifetime(Integer.MAX_VALUE));
    }
    @Test void corpseRegistrationUsesDeadPacketNotLiveRagdollAndKeepsItsOriginalAppearance() throws Exception {
        String code=Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/compat/AftermathRagdollCompatibility.java"));
        assertTrue(code.contains("getMethod(\"copy\")"));
        assertTrue(code.contains("getMethod(\"register\",LivingEntity.class,packet,int.class)"));
        assertFalse(code.contains("getMethod(\"registerLive\""));
    }
    @Test void nativePendingBodiesNeverCreateDuplicateDefaultPlayer() throws Exception {
        String code=Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client/AftermathClient.java"));
        assertTrue(code.contains("nativeBody || io.github.safedecimal.decayingaftermath.compat.OptionalMedicalCompatibility.ragdollifiedSupported()"));
        assertTrue(code.contains("removeCorpse(client); return;"));
    }
    @Test void internalNullToDeathScreenPathIsGatedNotOnlyExplicitScreenArguments() throws Exception {
        String code=Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/client/mixin/AftermathGuiMixin.java"));
        assertTrue(code.contains("LocalPlayer;isDeadOrDying()Z"));
        assertTrue(code.contains("deadOrDying && !AftermathClient.suppressDeathScreen()"));
        assertFalse(code.contains("shouldShowDeathScreen()Z"));
    }
}
