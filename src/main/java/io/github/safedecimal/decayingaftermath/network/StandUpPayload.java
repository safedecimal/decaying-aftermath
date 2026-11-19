package io.github.safedecimal.decayingaftermath.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Carries input intent only; the server owns state and recovery timing. */
public record StandUpPayload() implements CustomPacketPayload {
    public static final StandUpPayload INSTANCE = new StandUpPayload();
    public static final Type<StandUpPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("decaying_aftermath", "stand_up"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StandUpPayload> CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<StandUpPayload> type() { return TYPE; }
}
