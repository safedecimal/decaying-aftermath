package io.github.safedecimal.decayingaftermath.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client observation of a vanilla cave ambience sound event; rate-limited on the server. */
public record CaveSoundPayload() implements CustomPacketPayload {
    public static final CaveSoundPayload INSTANCE = new CaveSoundPayload();
    public static final Type<CaveSoundPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("abjection", "cave_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaveSoundPayload> CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<CaveSoundPayload> type() { return TYPE; }
}
