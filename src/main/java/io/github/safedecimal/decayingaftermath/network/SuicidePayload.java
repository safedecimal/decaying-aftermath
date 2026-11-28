package io.github.safedecimal.decayingaftermath.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Clientbound /suicide self-destruct timeline cue (private diegetic sound & head explosion). */
public record SuicidePayload(int stage) implements CustomPacketPayload {
    public static final int START = 0, DETONATE = 1, ABORT = 2;
    public static final Type<SuicidePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("abjection", "suicide"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SuicidePayload> CODEC =
            CustomPacketPayload.codec((p, b) -> b.writeByte(p.stage), b -> new SuicidePayload(b.readUnsignedByte()));

    @Override public Type<SuicidePayload> type() { return TYPE; }
}
