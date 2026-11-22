package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.network.DefibNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefibPayloadTest {
    @Test void sourcePointerInputRoundTripsWithoutClientPatientIdOrCompletionFlag() {
        var expected=new DefibNetwork.Input(17,DefibGame.DIAL,-225,-174);
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {DefibNetwork.Input.CODEC.encode(b,expected);assertEquals(expected,DefibNetwork.Input.CODEC.decode(b));assertEquals(b.writerIndex(),b.readerIndex());}
        finally {b.release();}
    }
    @Test void malformedControlAndPointerPacketsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new DefibNetwork.Input(17,9,0,0));
        assertThrows(IllegalArgumentException.class,()->new DefibNetwork.Input(17,DefibGame.MOVE,Float.NaN,0));
        assertThrows(IllegalArgumentException.class,()->new DefibNetwork.Input(17,DefibGame.MOVE,0,525));
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {b.writeInt(17);b.writeByte(2);b.writeFloat(Float.POSITIVE_INFINITY);b.writeFloat(0);
            assertThrows(IllegalArgumentException.class,()->DefibNetwork.Input.CODEC.decode(b));}
        finally {b.release();}
    }
    @Test void authoritativePhaseClockChargeBatteryPatientVitalsAndPadPositionsRoundTrip() {
        var expected=new DefibNetwork.State(17,1,true,1,-1,125,105,2,40,100,.83f,-296,153,273,-141,178,.92f,99,40,150,false);
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {DefibNetwork.State.CODEC.encode(b,expected);assertEquals(expected,DefibNetwork.State.CODEC.decode(b));assertEquals(b.writerIndex(),b.readerIndex());}
        finally {b.release();}
    }
}
