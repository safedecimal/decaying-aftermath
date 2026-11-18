package io.github.safedecimal.decayingaftermath.client;

import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import static org.junit.jupiter.api.Assertions.*;

class MedicalInventoryLifecycleTest {
    private static final String MIXIN = "io.github.safedecimal.decayingaftermath.client.mixin.MedicalInventoryOpenMixin";

    @Test void inventoryIsSelectedBeforeVanillaInitializesTheScreen() throws Exception {
        try (var input = getClass().getResourceAsStream("/decaying-aftermath.client.mixins.json")) {
            assertNotNull(input);
            assertTrue(new String(input.readAllBytes(), StandardCharsets.UTF_8).contains("\"MedicalInventoryOpenMixin\""));
        }
        var handler = Class.forName(MIXIN, false, getClass().getClassLoader())
                .getDeclaredMethod("decayingAftermath$chooseInventory", Screen.class);
        var injection = handler.getAnnotation(ModifyVariable.class);
        assertNotNull(injection);
        assertArrayEquals(new String[]{"setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"}, injection.method());
        assertEquals(1, injection.at().length);
        assertEquals("HEAD", injection.at()[0].value());
        assertTrue(injection.argsOnly());

        // Don't register another replacement from a lifecycle callback after the transition starts.
        try (var input = MedicalClient.class.getResourceAsStream("MedicalClient.class")) {
            assertNotNull(input);
            var code = ClassFile.of().parse(input.readAllBytes());
            assertFalse(code.methods().stream().flatMap(m -> m.code().stream()).flatMap(c -> c.elementStream())
                    .anyMatch(e -> e instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals("net/minecraft/client/gui/Gui")
                            && call.name().equalsString("setScreen")));
        }
    }
}
