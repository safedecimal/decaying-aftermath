package io.github.safedecimal.decayingaftermath.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MedicalActionPayload(int action,int slot,int part,int kind,float amount) implements CustomPacketPayload {
    public static final int BEGIN=0, APPLY=1, END=2, FINISH=3, REMOVE_TOURNIQUET=4, REMOVE_SPLINT=5;
    public static final Type<MedicalActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("abjection","medical_action_v4"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MedicalActionPayload> CODEC = CustomPacketPayload.codec(MedicalActionPayload::write,MedicalActionPayload::read);
    @Override public Type<MedicalActionPayload> type() { return TYPE; }
    private void write(RegistryFriendlyByteBuf b) { b.writeVarInt(action);b.writeVarInt(slot);b.writeVarInt(part);b.writeVarInt(kind);b.writeFloat(amount); }
    private static MedicalActionPayload read(RegistryFriendlyByteBuf b) { return new MedicalActionPayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat()); }
}
