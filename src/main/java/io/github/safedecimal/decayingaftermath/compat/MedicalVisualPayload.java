package io.github.safedecimal.decayingaftermath.compat;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C cosmetic open-wound summary only; never accepted as authoritative player physiology. */
public record MedicalVisualPayload(int entityId, UUID playerId, float bleeding, boolean dead,
        java.util.List<Float> partBleeding, int ragdollSerial, int posture) implements CustomPacketPayload {
    public static final Type<MedicalVisualPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("abjection", "medical_visual_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MedicalVisualPayload> CODEC =
            CustomPacketPayload.codec(MedicalVisualPayload::write, MedicalVisualPayload::read);
    public MedicalVisualPayload {
        Objects.requireNonNull(playerId, "playerId");
        if (entityId < 0 || !Float.isFinite(bleeding) || bleeding < 0 || bleeding > 6)
            throw new IllegalArgumentException("Invalid medical visual summary");
        partBleeding = MedicalBleedOrigins.checked(partBleeding);
        if (Math.abs(MedicalBleedOrigins.total(partBleeding) - bleeding) > .0001f || ragdollSerial < 0 || dead && ragdollSerial != 0)
            throw new IllegalArgumentException("Inconsistent medical visual summary");
        if (posture < 0 || posture > 3 || (dead && posture != 0))
            throw new IllegalArgumentException("Invalid posture in medical visual summary");
    }
    public MedicalVisualPayload(int entityId, UUID playerId, float bleeding, boolean dead,
            java.util.List<Float> partBleeding, int ragdollSerial) {
        this(entityId, playerId, bleeding, dead, partBleeding, ragdollSerial, ragdollSerial > 0 ? 2 : 0);
    }
    public MedicalVisualPayload(int entityId, UUID playerId, float bleeding, boolean dead) {
        this(entityId, playerId, bleeding, dead, MedicalBleedOrigins.uniform(bleeding), 0, 0);
    }
    public boolean collapsed() { return ragdollSerial > 0 || posture == 2 || posture == 3; }
    @Override public Type<MedicalVisualPayload> type() { return TYPE; }
    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId); buffer.writeUUID(playerId); buffer.writeFloat(bleeding); buffer.writeBoolean(dead);
        for (float amount : partBleeding) buffer.writeFloat(amount);
        buffer.writeVarInt(ragdollSerial);
        buffer.writeByte(posture);
    }
    private static MedicalVisualPayload read(RegistryFriendlyByteBuf buffer) {
        int id = buffer.readVarInt(); UUID uuid = buffer.readUUID(); float total = buffer.readFloat(); boolean dead = buffer.readBoolean();
        if (id < 0 || !Float.isFinite(total) || total < 0 || total > 6) throw new IllegalArgumentException("Invalid visual prefix");
        var parts = new java.util.ArrayList<Float>(6); for (int i = 0; i < 6; i++) parts.add(buffer.readFloat());
        return new MedicalVisualPayload(id, uuid, total, dead, parts, buffer.readVarInt(), buffer.readUnsignedByte());
    }
}
