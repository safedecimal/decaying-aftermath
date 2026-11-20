package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.client.MedicalPresentationPolicy;
import io.github.safedecimal.decayingaftermath.network.BodyPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CardiacRescueTest {
    private static void tick(BodyModel body,int ticks) {for(int i=0;i<ticks;i++)body.tick(false,false);}
    private static BodyModel strained() {
        var body=new BodyModel();
        body.damage(BodyPart.LEFT_ARM,2,DamageKind.CUT);
        assertTrue(body.treat(BodyPart.TORSO,TreatmentKind.COMBAT_STIMULANT,1));
        assertTrue(body.treat(BodyPart.TORSO,TreatmentKind.COMBAT_STIMULANT,1));
        return body;
    }
    @Test void sprintAloneDoesNotTriggerArrest() {
        var body=new BodyModel();
        for(int i=0;i<1600;i++)body.tick(true,true);
        assertEquals(0,body.snapshot().cardiacTicks());
        // A stable rhythm can still be shocked; it simply stays stable.        assertTrue(body.defibrillate());        assertEquals(0,body.snapshot().cardiacTicks());
        assertTrue(body.snapshot().bpm()>72);
    }
    @Test void racingHeartIsAudibleThenStopsAtExactlyZero() {
        var body=strained();
        tick(body,CardiacRhythm.WARNING);
        var racing=body.snapshot();
        assertTrue(racing.racingHeart());
        assertTrue(racing.bpm()>=150);
        assertEquals("heartbeat_heavy",MedicalPresentationPolicy.beatSound(false,racing));
        tick(body,CardiacRhythm.COLLAPSE-CardiacRhythm.WARNING);
        var arrested=body.snapshot();
        assertTrue(arrested.cardiacArrest());
        assertEquals(0f,arrested.bpm());
        assertEquals(0,MedicalPhysiology.systolic(arrested));
        assertEquals(BodySnapshot.Mode.UNCONSCIOUS,arrested.mode());
        assertNull(MedicalPresentationPolicy.beatSound(false,arrested));
        assertFalse(body.tryStandUp());
    }
    @Test void savesAndPacketsPreserveRescueWindowAndShockDoesNotHealWounds() {
        var body=strained();
        tick(body,CardiacRhythm.COLLAPSE+20);
        var saved=body.snapshot();
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            BodyPayload.CODEC.encode(buf,new BodyPayload(saved.withBloodGroup(BloodGroup.values()[0])));
            var decoded=BodyPayload.CODEC.decode(buf).snapshot();
            assertEquals(saved.cardiacTicks(),decoded.cardiacTicks());
            assertEquals(0f,decoded.bpm());
        } finally {buf.release();}
        body=BodyModel.restoreState(body.saveState());
        assertEquals(saved.cardiacTicks(),body.snapshot().cardiacTicks());
        assertTrue(body.defibrillate());
        var shock=body.snapshot();
        assertEquals(78f,shock.bpm());
        assertEquals(saved.part(BodyPart.LEFT_ARM),shock.part(BodyPart.LEFT_ARM));
        assertEquals(saved.oxygen(),shock.oxygen());
        assertEquals(saved.blood(),shock.blood());
        assertTrue(body.defibrillate());        assertEquals(CardiacRhythm.RECOVERY,body.snapshot().cardiacTicks());
        tick(body,BodyModel.WAKE_UP_TICKS);
        assertEquals(BodySnapshot.Mode.CONSCIOUS,body.snapshot().mode());
    }
    @Test void elapsedRescueWindowKillsAndDeadBodyCannotBeShocked() {
        var body=strained();
        tick(body,CardiacRhythm.COLLAPSE+CardiacRhythm.RESCUE-1);
        assertEquals(-1,body.snapshot().cardiacTicks());
        tick(body,1);
        assertEquals(BodySnapshot.Mode.DEAD,body.snapshot().mode());
        assertEquals(0f,body.snapshot().bpm());
        assertFalse(body.defibrillate());
    }
    @Test void clearingStrainClearsWarningAndManualShockCanPreventAnArrest() {
        int strain=CardiacRhythm.WARNING;
        for(int i=0;i<20;i++)strain=CardiacRhythm.advance(strain,false);
        assertEquals(0,strain);
        var body=strained();tick(body,CardiacRhythm.WARNING);
        assertTrue(body.defibrillate());
        tick(body,CardiacRhythm.COLLAPSE-CardiacRhythm.WARNING);
        assertFalse(body.snapshot().cardiacArrest());
    }
}
