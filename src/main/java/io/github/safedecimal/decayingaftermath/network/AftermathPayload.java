package io.github.safedecimal.decayingaftermath.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-owned deadline and death anchor; ordinary body snapshots cannot select this cause. */
public record AftermathPayload(boolean active, boolean depression, int remainingTicks, long token,
        Identifier dimension, double x, double y, double z, float yaw, int corpseId) implements CustomPacketPayload {
    public static final Type<AftermathPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("abjection", "afterlife"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AftermathPayload> CODEC = new StreamCodec<>() {
        @Override public AftermathPayload decode(RegistryFriendlyByteBuf buffer) {
            return new AftermathPayload(buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(),
                    buffer.readLong(), buffer.readIdentifier(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readFloat(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, AftermathPayload value) {
            buffer.writeBoolean(value.active); buffer.writeBoolean(value.depression);
            buffer.writeVarInt(value.remainingTicks); buffer.writeLong(value.token);
            buffer.writeIdentifier(value.dimension); buffer.writeDouble(value.x); buffer.writeDouble(value.y);
            buffer.writeDouble(value.z); buffer.writeFloat(value.yaw); buffer.writeVarInt(value.corpseId);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
