package io.github.safedecimal.decayingaftermath.medical;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalStackPrototypeTest {
    @Test void nativeRemoveDeletesPrototypeInsteadOfRestoringTheStackLimit() {
        DataComponentType<Integer> limit=DataComponentType.<Integer>builder().persistent(Codec.INT).build();
        var prototype=DataComponentMap.builder().set(limit,16).build();
        var stack=new PatchedDataComponentMap(prototype);
        assertEquals(16,stack.get(limit));
        stack.remove(limit);
        assertNull(stack.get(limit));assertEquals(1,stack.getOrDefault(limit,1));assertTrue(stack.hasNonDefault(limit));
        stack.set(limit,16);
        assertEquals(16,stack.get(limit));assertFalse(stack.hasNonDefault(limit));
    }
}
