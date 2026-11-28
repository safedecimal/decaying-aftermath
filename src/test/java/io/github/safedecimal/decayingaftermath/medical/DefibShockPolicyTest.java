package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.network.BodyPayload;
import io.github.safedecimal.decayingaftermath.network.DefibNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static io.github.safedecimal.decayingaftermath.medical.DefibShockPolicy.Result.*;
import static org.junit.jupiter.api.Assertions.*;

final class DefibShockPolicyTest {
    private static BodyModel rhythm(int value) {
        return BodyModel.restore(new BodyModel().snapshot().withCardiacTicks(value));
    }
    private static void unchanged(BodySnapshot a,BodySnapshot b) {
        // Record.equals compares its cloned parts array by identity, not by element values.
        assertArrayEquals(a.parts(),b.parts());assertEquals(a.survival(),b.survival());
        assertEquals(a.condition(),b.condition());assertEquals(a.blood(),b.blood());
        assertEquals(a.oxygen(),b.oxygen());assertEquals(a.pain(),b.pain());assertEquals(a.drug(),b.drug());
        assertEquals(a.bpm(),b.bpm());assertEquals(a.mode(),b.mode());
        assertEquals(a.consciousnessProgress(),b.consciousnessProgress());
        assertEquals(a.energy(),b.energy());assertEquals(a.consciousness(),b.consciousness());
        assertEquals(a.bloodGroup(),b.bloodGroup());assertEquals(a.cardiacTicks(),b.cardiacTicks());
    }
    @Test void organizedRhythmShockCanCauseVfWithoutInstantlyDestroyingBloodOrOxygen() {
        var b=new BodyModel();var before=b.snapshot();
        assertEquals(ARREST,b.defibrillate(false,200,0));
        var s=b.snapshot();
        assertEquals(-CardiacRhythm.RESCUE,s.cardiacTicks());assertEquals(0,s.bpm());
        assertEquals(0,MedicalPhysiology.systolic(s));assertEquals(0,MedicalPhysiology.diastolic(s));
        assertEquals(BodySnapshot.Mode.UNCONSCIOUS,s.mode());assertEquals(0,s.consciousness());
        assertEquals(before.blood(),s.blood());assertEquals(before.oxygen(),s.oxygen());
        assertFalse(b.tryStandUp());
    }
    @Test void unsafeShockIsNotGuaranteedArrestButStillCausesTachyarrhythmiaAndPoorFilling() {
        var b=new BodyModel();
        assertEquals(ARRHYTHMIA,b.defibrillate(false,200,.99f));
        var s=b.snapshot();assertFalse(s.cardiacArrest());assertTrue(s.racingHeart());
        assertTrue(s.bpm()>=150);assertTrue(MedicalPhysiology.systolic(s)<90);
        assertTrue(s.part(BodyPart.TORSO).burn()>0);assertTrue(s.part(BodyPart.TORSO).integrity()<1);
        assertEquals(0,s.part(BodyPart.TORSO).bleeding());assertFalse(s.part(BodyPart.TORSO).fractured());
    }
    @Test void higherGameEnergyIncreasesInductionRiskAndLocalInjury() {
        assertEquals(ARRHYTHMIA,DefibShockPolicy.assess(0,false,10,.4f,0).result());
        assertEquals(ARREST,DefibShockPolicy.assess(0,false,200,.4f,0).result());
        assertTrue(DefibShockPolicy.assess(0,false,200,.99f,0).injury()>DefibShockPolicy.assess(0,false,10,.99f,0).injury());
    }
    @Test void compromisedPhysiologyIncreasesRiskWithoutChangingDeliveredEnergy() {
        assertEquals(ARRHYTHMIA,DefibShockPolicy.assess(0,false,100,.5f,0).result());
        assertEquals(ARREST,DefibShockPolicy.assess(0,false,100,.5f,1).result());
    }
    @Test void underchargeDuringPulselessRhythmDoesNotBypassEnergyCheckOrResetRescueWindow() {
        var b=rhythm(-600);
        for(int i=0;i<5;i++)assertEquals(NO_CONVERSION,b.defibrillate(false,10,.5f));
        assertEquals(-600,b.snapshot().cardiacTicks());assertEquals(0,b.snapshot().bpm());
        for(int i=0;i<20;i++)b.tick(false,false);
        assertEquals(-580,b.snapshot().cardiacTicks());assertEquals(.952f,b.snapshot().oxygen(),.0001f);
        assertTrue(b.snapshot().energy()<1);assertEquals(1,b.snapshot().blood());
    }
    @Test void failedShockAtEndOfRescueWindowCannotPostponeDeath() {
        var b=rhythm(-1);assertEquals(NO_CONVERSION,b.defibrillate(false,10,.5f));
        b.tick(false,false);assertEquals(BodySnapshot.Mode.DEAD,b.mode());
    }
    @Test void matchedChargeCanConvertButDoesNotHealOrRefillReserves() {
        var b=new BodyModel();b.damage(BodyPart.LEFT_ARM,2,DamageKind.CUT);b.reduceOxygen(.3f);
        b=BodyModel.restore(b.snapshot().withCardiacTicks(-600));var before=b.snapshot();
        assertEquals(CONVERTED,b.defibrillate(false,200,.5f));var s=b.snapshot();
        assertEquals(CardiacRhythm.RECOVERY,s.cardiacTicks());assertEquals(78,s.bpm());
        assertEquals(before.part(BodyPart.LEFT_ARM),s.part(BodyPart.LEFT_ARM));
        assertEquals(before.blood(),s.blood());assertEquals(before.oxygen(),s.oxygen());
        assertEquals(before.energy(),s.energy());assertEquals(0,s.part(BodyPart.TORSO).burn());
        assertEquals(BodySnapshot.Mode.UNCONSCIOUS,s.mode());assertEquals(0,s.consciousnessProgress());
    }
    @Test void excessiveFailedShockWorsensUnstableRhythmAndAccumulatesBurns() {
        var b=rhythm(80);
        assertEquals(ARRHYTHMIA,b.defibrillate(false,200,.99f));var first=b.snapshot();
        assertTrue(first.cardiacTicks()>80);assertTrue(first.part(BodyPart.TORSO).burn()>0);
        // Shock another organized/recovered rhythm: unlike a therapeutic conversion, this is dangerous.
        b=BodyModel.restore(first.withCardiacTicks(CardiacRhythm.RECOVERY));
        assertEquals(ARRHYTHMIA,b.defibrillate(false,200,.99f));
        assertTrue(b.snapshot().part(BodyPart.TORSO).burn()>first.part(BodyPart.TORSO).burn());
        assertTrue(b.snapshot().part(BodyPart.TORSO).integrity()<first.part(BodyPart.TORSO).integrity());
    }
    @Test void smallMatchedChargeIsNotPunishedAsOvercharge() {
        var b=rhythm(80);assertEquals(CONVERTED,b.defibrillate(false,50,.5f));
        assertEquals(0,b.snapshot().part(BodyPart.TORSO).burn());assertEquals(1,b.snapshot().part(BodyPart.TORSO).integrity());
    }
    @Test void aedBlocksEveryOrganizedNonshockableTimerWithoutAnySideEffects() {
        for(int timer:new int[]{0,39,201,440}) {
            var b=rhythm(timer);var s=b.saveState();
            assertEquals(BLOCKED,b.defibrillate(true,0,.5f));
            unchanged(s.snapshot(),b.saveState().snapshot());
        }
    }
    @Test void aedCanTreatGameShockableRhythmWithoutManualEnergyRequirement() {
        for(int timer:new int[]{40,199,-600}) {
            var b=rhythm(timer);assertEquals(CONVERTED,b.defibrillate(true,0,.5f));
            assertEquals(CardiacRhythm.RECOVERY,b.snapshot().cardiacTicks());assertEquals(0,b.snapshot().part(BodyPart.TORSO).burn());
        }
    }
    @Test void malformedEnergyAndRandomSamplesNeverMutateTheBody() {
        for(float energy:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,0,9,201}) {
            var b=new BodyModel();var before=b.saveState().snapshot();
            assertEquals(REJECTED,b.defibrillate(false,energy,.5f));unchanged(before,b.saveState().snapshot());
        }
        for(float roll:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,1,2}) {
            var b=new BodyModel();var before=b.saveState().snapshot();
            assertEquals(REJECTED,b.defibrillate(false,100,roll));unchanged(before,b.saveState().snapshot());
        }
    }
    @Test void deadBodyCannotReceiveDeviceShock() {
        var b=new BodyModel();b.kill();
        assertEquals(REJECTED,b.defibrillate(false,200,0));assertEquals(REJECTED,b.defibrillate(true,0,0));
        assertEquals(0,b.snapshot().part(BodyPart.TORSO).burn());assertEquals(BodySnapshot.Mode.DEAD,b.mode());
    }
    @Test void injuryArrestAndRemainingTimeSurviveSaveReloadAndWireCodec() {
        var b=new BodyModel();b.defibrillate(false,200,0);for(int i=0;i<20;i++)b.tick(false,false);
        var before=b.snapshot();var restored=BodyModel.restoreState(b.saveState()).snapshot();
        assertEquals(before.cardiacTicks(),restored.cardiacTicks());assertEquals(before.part(BodyPart.TORSO),restored.part(BodyPart.TORSO));
        assertEquals(before.energy(),restored.energy());assertEquals(before.oxygen(),restored.oxygen());
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            BodyPayload.CODEC.encode(buf,new BodyPayload(before.withBloodGroup(BloodGroup.O_POSITIVE)));
            var decoded=BodyPayload.CODEC.decode(buf).snapshot();assertEquals(before.cardiacTicks(),decoded.cardiacTicks());
            assertEquals(before.part(BodyPart.TORSO).burn(),decoded.part(BodyPart.TORSO).burn(),.01f);
            assertEquals(0,decoded.bpm());assertEquals(BodySnapshot.Mode.UNCONSCIOUS,decoded.mode());
        } finally {buf.release();}
    }
    @Test void actualServerPathUsesDeliveredChargeAndSynchronizesAllOutcomes() throws Exception {
        var root=Path.of(System.getProperty("user.dir"),"src/main/java/io/github/safedecimal/decayingaftermath");
        var network=Files.readString(root.resolve("network/DefibNetwork.java"));
        assertTrue(network.contains("defibrillate(s.patient,s.automatic,s.game.lastShockCharge(),s.actor.getRandom().nextFloat())"));
        assertFalse(network.contains("chanceToWork("));assertFalse(network.contains("!body.cardiacArrest()"));
        var server=Files.readString(root.resolve("server/ServerMedicalSystem.java"));
        assertTrue(server.contains("var result=body.defibrillate(automatic,charge,roll);"));
        assertTrue(server.contains("mirrorHealth(player);syncBody(player,body.snapshot());"));
        var screen=Files.readString(root.resolve("client/DefibScreen.java"));
        assertTrue(screen.contains("if(next.result()==DefibNetwork.HARM)harmTicks=60;"));
        for(String lang:new String[]{"ru_ru","en_us"})
            assertTrue(Files.readString(Path.of("src/main/resources/assets/abjection/lang",lang+".json")).contains("abjection.defib.harm"));
    }
    @Test void harmfulShockOutcomeAndZeroPulseRoundTripInDeviceState() {
        var state=new DefibNetwork.State(17,DefibNetwork.HARM,false,2,-1,125,105,1,0,200,.83f,0,0,0,0,0,.95f,-880,130,200,false);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {DefibNetwork.State.CODEC.encode(buf,state);assertEquals(state,DefibNetwork.State.CODEC.decode(buf));}
        finally {buf.release();}
    }
}
