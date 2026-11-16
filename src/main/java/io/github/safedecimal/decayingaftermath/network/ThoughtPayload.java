package io.github.safedecimal.decayingaftermath.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ThoughtPayload(String message) implements CustomPacketPayload {
    public static final Type<ThoughtPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("decaying_aftermath","thought"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ThoughtPayload> CODEC = CustomPacketPayload.codec((p,b)->b.writeUtf(p.message,160),b->new ThoughtPayload(b.readUtf(160)));
    @Override public Type<ThoughtPayload> type() { return TYPE; }
}
