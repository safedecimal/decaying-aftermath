package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.network.MedicalNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalSupplyNbtTest {
    @Test void nativeCustomDataCodecPreservesRemainingMaterial() {
        var tag=new CompoundTag();tag.putFloat(MedicalNetwork.FILL_KEY,.75f);
        var encoded=CustomData.CODEC.encodeStart(NbtOps.INSTANCE,CustomData.of(tag)).getOrThrow();
        var restored=CustomData.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow();
        assertEquals(.75f,restored.copyTag().getFloatOr(MedicalNetwork.FILL_KEY,1));
    }
    @Test void supplyDataDoesNotEraseOtherNativeFields() {
        var tag=new CompoundTag();tag.putFloat(MedicalNetwork.FILL_KEY,.25f);tag.putFloat("fixture_other",.625f);
        var encoded=CustomData.CODEC.encodeStart(NbtOps.INSTANCE,CustomData.of(tag)).getOrThrow();
        var restored=CustomData.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow().copyTag();
        assertEquals(.25f,restored.getFloatOr(MedicalNetwork.FILL_KEY,1));assertEquals(.625f,restored.getFloatOr("fixture_other",0));
    }
}
