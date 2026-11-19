package io.github.safedecimal.decayingaftermath.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnconsciousVisionAssetTest {
    @Test void minecraftDecodesTheTwoPassWorldBlur() throws Exception {
        var asset = getClass().getResourceAsStream("/assets/decaying_aftermath/post_effect/unconscious_vision.json");
        assertNotNull(asset);
        try (var reader = new InputStreamReader(asset, StandardCharsets.UTF_8)) {
            var chain = PostChainConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
            assertEquals(Set.of(PostChain.MAIN_TARGET_ID), PostChain.getReferencedExternalTargets(chain));
            assertEquals(2, chain.passes().size());
            var horizontal = chain.passes().get(0);
            var vertical = chain.passes().get(1);
            assertTrue(chain.internalTargets().containsKey(horizontal.outputTarget()));
            assertEquals(PostChain.MAIN_TARGET_ID, vertical.outputTarget());
            assertPass(horizontal, PostChain.MAIN_TARGET_ID);
            assertPass(vertical, horizontal.outputTarget());
        }
    }

    private static void assertPass(PostChainConfig.Pass pass, Identifier inputTarget) {
        assertEquals(Identifier.fromNamespaceAndPath("minecraft", "post/box_blur"), pass.fragmentShaderId());
        assertEquals(1, pass.inputs().size());
        var input = assertInstanceOf(PostChainConfig.TargetInput.class, pass.inputs().getFirst());
        assertEquals(inputTarget, input.targetId());
        assertTrue(input.bilinear());
        assertFalse(input.useDepthBuffer());
    }
}
