package io.github.safedecimal.decayingaftermath.compat;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
class MedicalVisualPayloadTest {
 @Test void woundedArmAndExactNativeBodySerialSurviveCodec(){var expected=new MedicalVisualPayload(17,new UUID(4,8),.8f,false,java.util.List.of(0f,0f,.8f,0f,0f,0f),234);var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{MedicalVisualPayload.CODEC.encode(b,expected);var actual=MedicalVisualPayload.CODEC.decode(b);assertEquals(expected,actual);assertEquals(234,actual.ragdollSerial());assertTrue(actual.collapsed());assertEquals(.8f,actual.partBleeding().get(2));assertEquals(0f,actual.partBleeding().get(3));}finally{b.release();}}
 @Test void impossibleDeadCollapseAndInconsistentAnatomyAreRejected(){assertThrows(IllegalArgumentException.class,()->new MedicalVisualPayload(1,new UUID(1,2),0,true,java.util.Collections.nCopies(6,0f),1));assertThrows(IllegalArgumentException.class,()->new MedicalVisualPayload(1,new UUID(1,2),.5f,false,java.util.Collections.nCopies(6,0f),0));}
 @Test void nativeCodecRoundTripsOnlyBoundedCosmeticSummary(){var expected=new MedicalVisualPayload(17,new UUID(4,8),1.75f,false);var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{MedicalVisualPayload.CODEC.encode(buffer,expected);assertEquals(expected,MedicalVisualPayload.CODEC.decode(buffer));assertEquals(buffer.writerIndex(),buffer.readerIndex());}finally{buffer.release();}}
 @Test void nonFiniteAndOutOfRangeDataAreRejected(){for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,6.1f})assertThrows(IllegalArgumentException.class,()->new MedicalVisualPayload(1,new UUID(4,8),bad,false));assertThrows(IllegalArgumentException.class,()->new MedicalVisualPayload(-1,new UUID(4,8),1,false));assertThrows(NullPointerException.class,()->new MedicalVisualPayload(1,null,1,false));}
 @Test void decodedInvalidPayloadIsRejectedWithoutCreatingClientState(){var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{buffer.writeVarInt(5);buffer.writeUUID(new UUID(4,8));buffer.writeFloat(Float.NaN);buffer.writeBoolean(false);assertThrows(IllegalArgumentException.class,()->MedicalVisualPayload.CODEC.decode(buffer));}finally{buffer.release();}}
}
