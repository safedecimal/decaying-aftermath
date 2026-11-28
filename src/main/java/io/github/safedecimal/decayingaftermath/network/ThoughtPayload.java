package io.github.safedecimal.decayingaftermath.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Translation key resolved on the receiving client, never in the server locale. */
public record ThoughtPayload(String message) implements CustomPacketPayload {
    public static final Type<ThoughtPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("abjection","thought"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ThoughtPayload> CODEC = CustomPacketPayload.codec((p,b)->b.writeUtf(p.message,160),b->new ThoughtPayload(b.readUtf(160)));
    @Override public Type<ThoughtPayload> type() { return TYPE; }
}
