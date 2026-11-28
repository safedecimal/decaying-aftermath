package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.compat.MedicalVisualPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InjuredAnimationsTest {
    @Test
    void loadsAllThreeInjuredClipsAndSamplesFiniteBoneTransforms() {
        assertTrue(InjuredAnimations.loaded());
        Vector3f pos = new Vector3f();
        Vector3f rot = new Vector3f();
        for (InjuredAnimations.Clip clip : InjuredAnimations.Clip.values()) {
            float len = InjuredAnimations.length(clip);
            assertTrue(len > 1.0f, clip.name());
            float maxRot = 0f;
            for (InjuredAnimations.Bone bone : InjuredAnimations.Bone.values()) {
                InjuredAnimations.sample(clip, len * 0.5f, bone, pos, rot);
                assertTrue(Float.isFinite(pos.x) && Float.isFinite(pos.y) && Float.isFinite(pos.z));
                assertTrue(Float.isFinite(rot.x) && Float.isFinite(rot.y) && Float.isFinite(rot.z));
                maxRot = Math.max(maxRot, rot.length());
            }
            assertTrue(maxRot > 0.2f, clip.name() + " should have non-trivial limb motion");
        }
        // Wrapping beyond animation length stays bounded and continuous
        Vector3f a = new Vector3f(), b = new Vector3f(), rA = new Vector3f(), rB = new Vector3f();
        float idleLen = InjuredAnimations.length(InjuredAnimations.Clip.IDLE);
        InjuredAnimations.sample(InjuredAnimations.Clip.IDLE, 0.25f, InjuredAnimations.Bone.RIGHT_ARM, a, rA);
        InjuredAnimations.sample(InjuredAnimations.Clip.IDLE, idleLen + 0.25f, InjuredAnimations.Bone.RIGHT_ARM, b, rB);
        assertEquals(a.x, b.x, 0.001f);
        assertEquals(rA.y, rB.y, 0.001f);
    }

    @Test
    void visualPayloadRoundTripsInjuredAndCollapsedPosture() {
        var payload = new MedicalVisualPayload(11, new UUID(1, 2), 0.5f, false,
                List.of(0.5f, 0f, 0f, 0f, 0f, 0f), 0, 1);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            MedicalVisualPayload.CODEC.encode(buf, payload);
            var decoded = MedicalVisualPayload.CODEC.decode(buf);
            assertEquals(1, decoded.posture());
            assertFalse(decoded.collapsed());
            assertEquals(buf.writerIndex(), buf.readerIndex());
        } finally {
            buf.release();
        }
    }
}
