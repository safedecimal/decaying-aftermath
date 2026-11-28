package io.github.safedecimal.decayingaftermath.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client request to start voluntary sleep anywhere or wake up via the Awake button. */
public record SleepPayload(boolean wake) implements CustomPacketPayload {
    public static final Type<SleepPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("abjection", "sleep"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SleepPayload> CODEC =
            CustomPacketPayload.codec((p, b) -> b.writeBoolean(p.wake), b -> new SleepPayload(b.readBoolean()));

    @Override public Type<SleepPayload> type() { return TYPE; }
}
