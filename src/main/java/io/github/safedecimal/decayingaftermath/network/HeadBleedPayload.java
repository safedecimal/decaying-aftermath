package io.github.safedecimal.decayingaftermath.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Notifies nearby clients + victim that a head-detonation suicide occurred so neck blood pours out. */
public record HeadBleedPayload(int entityId, UUID playerUuid, double x, double y, double z, int durationTicks)
        implements CustomPacketPayload {
    public static final Type<HeadBleedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("abjection", "head_bleed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HeadBleedPayload> CODEC =
            CustomPacketPayload.codec(HeadBleedPayload::write, HeadBleedPayload::read);

    @Override public Type<HeadBleedPayload> type() { return TYPE; }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUUID(playerUuid);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeVarInt(durationTicks);
    }

    private static HeadBleedPayload read(RegistryFriendlyByteBuf buf) {
        return new HeadBleedPayload(buf.readVarInt(), buf.readUUID(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
    }
}
