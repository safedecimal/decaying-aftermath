package io.github.safedecimal.decayingaftermath.network;

import io.github.safedecimal.decayingaftermath.medical.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BodyPayload(BodySnapshot snapshot) implements CustomPacketPayload {
    public static final Type<BodyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("decaying_aftermath", "body"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyPayload> CODEC = CustomPacketPayload.codec(BodyPayload::write, BodyPayload::read);
    @Override public Type<BodyPayload> type() { return TYPE; }
    private void write(RegistryFriendlyByteBuf b) {
        b.writeFloat(snapshot.condition()); b.writeFloat(snapshot.blood()); b.writeFloat(snapshot.oxygen());
        b.writeFloat(snapshot.pain()); b.writeFloat(snapshot.drug()); b.writeFloat(snapshot.bpm());
        b.writeVarInt(snapshot.mode().ordinal()); b.writeFloat(snapshot.consciousnessProgress());
        b.writeFloat(snapshot.energy()); b.writeFloat(snapshot.consciousness());
        for (var p : snapshot.parts()) {
            b.writeFloat(p.integrity()); b.writeFloat(p.bleeding()); b.writeFloat(p.pain());
            b.writeBoolean(p.fractured()); b.writeBoolean(p.bandaged()); b.writeBoolean(p.splinted()); b.writeBoolean(p.tourniquet());
            b.writeFloat(p.infection()); b.writeBoolean(p.disinfected()); b.writeBoolean(p.anesthetized());
            b.writeFloat(p.bandageCare());
        }
    }
    private static BodyPayload read(RegistryFriendlyByteBuf b) {
        float condition=b.readFloat(), blood=b.readFloat(), oxygen=b.readFloat(), pain=b.readFloat(), drug=b.readFloat(), bpm=b.readFloat();
        int mode=b.readVarInt();
        if (mode<0 || mode>=BodySnapshot.Mode.values().length) throw new IllegalArgumentException("Unknown body mode");
        float progress=b.readFloat(), energy=b.readFloat(), consciousness=b.readFloat();
        var parts=new BodySnapshot.PartSnapshot[BodyPart.values().length];
        for (int i=0;i<parts.length;i++) parts[i]=new BodySnapshot.PartSnapshot(b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readFloat(),b.readBoolean(),b.readBoolean(),b.readFloat());
        return new BodyPayload(new BodySnapshot(condition,blood,oxygen,pain,drug,bpm,BodySnapshot.Mode.values()[mode],progress,energy,consciousness,parts));
    }
}
