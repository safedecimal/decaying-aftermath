package io.github.safedecimal.decayingaftermath.network;

import io.github.safedecimal.decayingaftermath.medical.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BodyPayload(BodySnapshot snapshot) implements CustomPacketPayload {
    public static final Type<BodyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("abjection", "body_v12"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyPayload> CODEC = CustomPacketPayload.codec(BodyPayload::write, BodyPayload::read);
    @Override public Type<BodyPayload> type() { return TYPE; }
    private void write(RegistryFriendlyByteBuf b) {
        b.writeFloat(snapshot.condition()); b.writeFloat(snapshot.blood()); b.writeFloat(snapshot.oxygen());
        b.writeFloat(snapshot.pain()); b.writeFloat(snapshot.drug()); b.writeFloat(snapshot.bpm());
        b.writeVarInt(snapshot.mode().ordinal()); b.writeFloat(snapshot.consciousnessProgress());
        b.writeFloat(snapshot.energy()); b.writeFloat(snapshot.consciousness());
        for (var p : snapshot.parts()) {
            b.writeFloat(p.integrity());
            var flow=p.externalBleeding();
            b.writeFloat(flow.capillary()); b.writeFloat(flow.venous()); b.writeFloat(flow.arterial());
            b.writeFloat(p.pain());
            b.writeBoolean(p.fractured()); b.writeBoolean(p.bandaged()); b.writeBoolean(p.splinted()); b.writeBoolean(p.tourniquet());
            b.writeFloat(p.infection()); b.writeBoolean(p.disinfected()); b.writeBoolean(p.anesthetized());
            b.writeFloat(p.bandageCare());
            b.writeFloat(p.burn());b.writeFloat(p.internalBleeding());b.writeBoolean(p.openWound());
            b.writeBoolean(p.foreignBody());b.writeFloat(p.boneRecovery());
            b.writeBoolean(p.coldInjury()); b.writeVarInt(p.disinfectionTicks()); b.writeFloat(p.infectionDelta());
        }
        var s=snapshot.survival();
        b.writeFloat(s.hydration());b.writeFloat(s.nutrition());b.writeFloat(s.temperature());b.writeFloat(s.fatigue());
        b.writeFloat(s.toxin());b.writeFloat(s.plasma());b.writeFloat(s.redCells());b.writeFloat(s.antibiotic());
        b.writeFloat(s.stimulant());b.writeFloat(s.antagonist());b.writeFloat(s.coagulant());b.writeFloat(s.internalBleeding());
        b.writeFloat(s.lungFunction());b.writeFloat(s.encumbrance());b.writeFloat(s.gutInfection());
        b.writeFloat(s.bodyMassKg());b.writeFloat(s.radiationGy());b.writeFloat(s.bloodLostLiters());
        b.writeFloat(s.moodReserve());b.writeEnum(s.depression());b.writeFloat(s.antidepressant());
        b.writeFloat(s.anxiolytic());b.writeFloat(s.therapyProgress());b.writeVarInt(s.moodFactors());
        b.writeFloat(s.ptsdLoad());b.writeVarInt(s.rememberedDeaths());b.writeBoolean(s.ptsdFlashback());
        b.writeEnum(snapshot.bloodGroup()); b.writeInt(snapshot.cardiacTicks());b.writeFloat(snapshot.brainHealth());
        b.writeBoolean(snapshot.voluntarySleeping());b.writeFloat(snapshot.sleepProgress());
    }
    private static BodyPayload read(RegistryFriendlyByteBuf b) {
        float condition=b.readFloat(), blood=b.readFloat(), oxygen=b.readFloat(), pain=b.readFloat(), drug=b.readFloat(), bpm=b.readFloat();
        int mode=b.readVarInt();
        if (mode<0 || mode>=BodySnapshot.Mode.values().length) throw new IllegalArgumentException("Unknown body mode");
        float progress=b.readFloat(), energy=b.readFloat(), consciousness=b.readFloat();
        var parts=new BodySnapshot.PartSnapshot[BodyPart.values().length];
        for (int i=0;i<parts.length;i++) {
            float integrity=b.readFloat();
            var flow=new BleedProfile(b.readFloat(),b.readFloat(),b.readFloat());
            parts[i]=new BodySnapshot.PartSnapshot(integrity,flow,b.readFloat(),b.readBoolean(),b.readBoolean(),
                    b.readBoolean(),b.readBoolean(),b.readFloat(),b.readBoolean(),b.readBoolean(),b.readFloat(),
                    b.readFloat(),b.readFloat(),b.readBoolean(),b.readBoolean(),b.readFloat(),b.readBoolean(),
                    b.readVarInt(),b.readFloat());
        }
        var s=new SurvivalSnapshot(b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),
                b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),
                b.readFloat(),b.readFloat(),b.readFloat(),
                b.readFloat(),b.readEnum(DepressionType.class),b.readFloat(),b.readFloat(),b.readFloat(),b.readVarInt(),
                b.readFloat(),b.readVarInt(),b.readBoolean());
        var group=b.readEnum(BloodGroup.class);
        return new BodyPayload(new BodySnapshot(condition,blood,oxygen,pain,drug,bpm,BodySnapshot.Mode.values()[mode],progress,energy,consciousness,parts,s,group,b.readInt(),b.readFloat(),b.readBoolean(),b.readFloat()));
    }
}
