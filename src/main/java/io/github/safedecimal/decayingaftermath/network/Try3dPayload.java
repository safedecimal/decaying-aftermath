package io.github.safedecimal.decayingaftermath.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Toggles experimental 3D minigame rendering via /trynew3dsystem. */
public record Try3dPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<Try3dPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("abjection", "try_3d_system"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Try3dPayload> CODEC =
            CustomPacketPayload.codec((p, b) -> b.writeBoolean(p.enabled), b -> new Try3dPayload(b.readBoolean()));

    @Override public Type<Try3dPayload> type() { return TYPE; }
}
