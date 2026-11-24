package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.client.MedicalDeathMusicState;
import io.github.safedecimal.decayingaftermath.client.MedicalSituationSoundState;
import io.github.safedecimal.decayingaftermath.client.CuMedicalMonitor;
import io.github.safedecimal.decayingaftermath.network.BodyPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryPhysiologyTest {
    private static SurvivalModel blood(float blood) {return new SurvivalModel(new SurvivalSnapshot(1,1,37,0,0,blood,blood,0,0,0,0,0,1,0));}
    private static void tick(BodyModel body,int ticks) {for(int i=0;i<ticks;i++)body.tick(false,false);}
    @Test void marrowContinuesProducingDuringSmallBloodLossAndPlasmaRecoversFaster() {
        var m=blood(.55f);for(int i=0;i<1200;i++)m.tick(BodyEnvironment.temperate(false,false),.000002f,0);
        var s=m.snapshot(0,1);assertTrue(s.plasma()>.55f);assertTrue(s.redCells()>.55f);
        assertTrue(s.plasma()- .55f>s.redCells()-.55f);assertTrue(s.bloodVolume()>.58f);
        assertTrue(s.bloodLostLiters()>0);
    }
    @Test void productionDoesNotCounteractMassiveBleedingOrRecreateReservesInstantly() {
        var m=blood(.55f);m.tick(BodyEnvironment.temperate(false,false),.001f,0);
        assertTrue(m.bloodVolume()<.55f);
        var normal=blood(.55f);normal.tick(BodyEnvironment.temperate(false,false),0,0);
        assertTrue(normal.bloodVolume()>.55f);assertTrue(normal.bloodVolume()<.551f);
    }
    @Test void absentCirculationCannotSupportMarrowOrFluidRestoration() {
        var m=blood(.55f);for(int i=0;i<1200;i++)m.tick(BodyEnvironment.temperate(false,false),0,0,0);
        assertEquals(.55f,m.snapshot(0,1).plasma());assertEquals(.55f,m.snapshot(0,1).redCells());
    }
    @Test void fluidAndNutritionAreSeparateSubstratesAndInflammationSuppressesMarrow() {
        var fed=RecoveryPhysiology.production(.5f,.5f,1,1,0,1);
        var hungry=RecoveryPhysiology.production(.5f,.5f,1,0,0,1);
        var dry=RecoveryPhysiology.production(.5f,.5f,0,1,0,1);
        var septic=RecoveryPhysiology.production(.5f,.5f,1,1,1,1);
        assertEquals(0,hungry.cells());assertTrue(hungry.plasma()>0);
        assertEquals(0,dry.cells());assertEquals(0,dry.plasma());assertTrue(septic.cells()<fed.cells());
    }
    @Test void salineImprovesPlasmaButNeverInstantlyRestoresRedCellMass() {
        var m=blood(.5f);m.treat(TreatmentKind.SALINE,1);assertEquals(.5f,m.snapshot(0,1).redCells());
        assertEquals(1,m.snapshot(0,1).plasma());
    }
    @Test void componentRecoveryNeverExceedsNormalReserve() {
        var m=blood(.99999f);for(int i=0;i<100;i++)m.tick(BodyEnvironment.temperate(false,false),0,0);
        assertEquals(1,m.snapshot(0,1).plasma());assertEquals(1,m.snapshot(0,1).redCells());
    }
    @Test void viableClosedTissueHealsWithoutPotionButIschemicOrUnprotectedWoundsDoNot() {
        var m=new BodyModel();m.damage(BodyPart.HEAD,6,DamageKind.BLUNT);tick(m,24000);
        assertEquals(1,m.snapshot().part(BodyPart.HEAD).integrity());assertEquals(1,m.snapshot().consciousness());
        assertEquals(0,RecoveryPhysiology.tissueRepair(.6f,false,false,true,0,0,0,false,1));
        assertEquals(0,RecoveryPhysiology.tissueRepair(.6f,false,true,false,0,0,0,false,1));
        assertEquals(0,RecoveryPhysiology.tissueRepair(.6f,false,true,true,.01f,0,0,false,1));
        assertEquals(0,RecoveryPhysiology.tissueRepair(.6f,false,true,true,0,.01f,0,false,1));
        assertEquals(0,RecoveryPhysiology.tissueRepair(.6f,false,true,true,0,0,.5f,false,1));
        assertEquals(0,RecoveryPhysiology.tissueRepair(.6f,false,true,true,0,0,0,true,1));
    }
    @Test void destroyedOrSeverelyDamagedVitalTissueIsNotMagicallyRegrown() {
        assertEquals(0,RecoveryPhysiology.tissueRepair(0,false,true,true,0,0,0,false,1));
        assertEquals(0,RecoveryPhysiology.tissueRepair(.2f,true,true,true,0,0,0,false,1));
        assertEquals(0,BrainPhysiology.advance(0,1,1,1,0,true));
    }
    @Test void neuralReserveRecoversGraduallyWhenPerfusionAndSubstratesReturn() {
        var m=BodyModel.restore(new BodyModel().snapshot().withBrainHealth(.6f));tick(m,6000);
        assertTrue(m.snapshot().brainHealth()>.7f);assertTrue(m.snapshot().brainHealth()<1);
        assertEquals(m.snapshot().brainHealth(),m.snapshot().consciousness());
        assertTrue(BrainPhysiology.advance(.6f,1,1,1,0,true)>BrainPhysiology.advance(.6f,1,1,1,0,false));
    }
    @Test void oxygenDeficitDamagesBrainAndNoCirculationDoesNotRecoverIt() {
        assertTrue(BrainPhysiology.advance(.8f,0,1,1,0,true)<.8f);
        var m=BodyModel.restore(new BodyModel().snapshot().withCardiacTicks(-600).withBrainHealth(.8f));tick(m,20);
        assertTrue(m.snapshot().brainHealth()<.8f);assertEquals(0,m.snapshot().consciousness());
    }
    @Test void starvationDehydrationAndSystemicStressPreventBrainRecovery() {
        assertEquals(.6f,BrainPhysiology.advance(.6f,1,0,1,0,true));
        assertEquals(.6f,BrainPhysiology.advance(.6f,1,1,0,0,true));
        assertEquals(.6f,BrainPhysiology.advance(.6f,1,1,1,.8f,true));
    }
    @Test void superficialScalpCutIsNotClassifiedAsNeuralTrauma() {
        var m=new BodyModel();m.damage(BodyPart.HEAD,2,DamageKind.CUT);assertEquals(1,m.snapshot().brainHealth());
        m=new BodyModel();m.damage(BodyPart.HEAD,2,DamageKind.BLUNT);assertTrue(m.snapshot().brainHealth()<1);
    }
    @Test void savesAndSnapshotsPreserveIndependentBrainHealthAndOldSavesDefaultSafely() {
        var m=BodyModel.restore(new BodyModel().snapshot().withBrainHealth(.6f));
        assertEquals(.6f,BodyModel.restoreState(m.saveState()).snapshot().brainHealth());
        assertEquals(.6f,m.snapshot().withBloodGroup(BloodGroup.A_POSITIVE).brainHealth());
        assertEquals(.6f,m.snapshot().withCardiacTicks(-500).brainHealth());
        assertEquals(1,new BodyModel().snapshot().brainHealth());
        assertThrows(IllegalArgumentException.class,()->new BodyModel().snapshot().withBrainHealth(Float.NaN));
    }
    @Test void wireCodecPreservesBrainHealthAndUsesNewVersion() {
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        var expected=new BodyModel().snapshot().withBrainHealth(.6f).withBloodGroup(BloodGroup.O_POSITIVE);
        try {BodyPayload.CODEC.encode(b,new BodyPayload(expected));var read=BodyPayload.CODEC.decode(b).snapshot();
            assertEquals(.6f,read.brainHealth());assertEquals(b.writerIndex(),b.readerIndex());}
        finally {b.release();}
        assertTrue(BodyPayload.TYPE.id().getPath().equals("body_v9"));
    }
    @Test void monitorShowsBrainReserveSeparatelyFromConsciousness() {
        var b=new BodyModel().snapshot().withBrainHealth(.6f);
        assertEquals("60",CuMedicalMonitor.value(b,"BrainText",72));
        assertEquals(.6f,CuMedicalMonitor.amount(b,"BrainBar"));
        assertEquals("100%",CuMedicalMonitor.value(b,"ConscText",72));
        assertFalse(CuMedicalMonitor.critical(b,"BrainText"));
        assertTrue(CuMedicalMonitor.critical(b.withBrainHealth(.3f),"BrainText"));
    }
    @Test void originalLastStandingEntryAndTimersRemainUnchanged() {
        var m=new BodyModel();m.damage(BodyPart.HEAD,34);var s=m.snapshot();
        assertEquals(BodySnapshot.Mode.LAST_STANDING,s.mode());assertEquals(.75f,s.part(BodyPart.HEAD).integrity());
        assertEquals(1,s.blood());assertEquals(1,s.oxygen());assertEquals(1,s.energy());assertEquals(1,s.brainHealth());
        assertEquals(240,m.saveState().lastStandingTicks());assertEquals(6000,m.saveState().lastStandingBoostTicks());
    }
    @Test void legacyFlowKeepsLastStandingBaselineAndArterialWoundsStillLoseBloodFaster() {
        var head=BodyPart.HEAD;
        var arterial=new BodyModel();arterial.damage(head,34);
        var initial=arterial.saveState();var parts=initial.snapshot().parts();
        parts[head.ordinal()]=parts[head.ordinal()].withExternalBleeding(BleedProfile.legacy(parts[head.ordinal()].bleeding()));
        var legacy=BodyModel.restoreState(new BodyModel.SavedState(initial.snapshot().withParts(parts),initial.careTicks(),
                initial.regenerationTicks(),initial.tourniquetTicks(),initial.lastStandingSpent(),initial.stableTicks()));
        tick(legacy,240);tick(arterial,240);var s=legacy.snapshot();
        assertEquals(.99705017f,s.blood());assertEquals(113.81707f,s.bpm());
        assertEquals(.99904156f,s.survival().hydration());assertEquals(.99928474f,s.survival().nutrition());
        assertEquals(.75f,s.part(head).integrity());assertEquals(.75f,arterial.snapshot().part(head).integrity());
        assertEquals(5760,legacy.saveState().lastStandingBoostTicks());
        assertEquals(5760,arterial.saveState().lastStandingBoostTicks());
        assertTrue(arterial.snapshot().blood()<s.blood());
    }
    @Test void originalMusicAssetsNowReachHypoxiaAndHemorrhageNotOnlyDyingMode() {
        var base=new BodyModel().snapshot();
        var low=new BodySnapshot(1,1,.5f,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,1,1,base.parts());
        assertEquals(1,MedicalSituationSoundState.volumes(low).dying());
        assertEquals(0,MedicalSituationSoundState.volumes(base).dying());
        var pain=new BodySnapshot(1,1,1,.8f,0,72,BodySnapshot.Mode.CONSCIOUS,1,1,1,base.parts());
        assertTrue(MedicalSituationSoundState.volumes(pain).pain()>0);
    }
    @Test void cardiacArrestStartsBrainDyingMusicAndRescueStopsItAndRearmsNextEpisode() {
        var state=new MedicalDeathMusicState();var base=new BodyModel().snapshot();var arrested=base.withCardiacTicks(-600);
        assertTrue(state.advance(arrested).startDying());assertFalse(state.advance(arrested).startDying());
        assertTrue(state.advance(base).stopDying());assertTrue(state.advance(arrested).startDying());
    }
    @Test void malformedDirectTreatmentsAndTickInputsCannotPoisonSavedPhysiology() {
        var m=blood(.55f);var before=m.snapshot(0,1);
        for(float amount:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,0})m.treat(TreatmentKind.BLOOD_BAG,amount);
        assertEquals(before,m.snapshot(0,1));m.tick(BodyEnvironment.temperate(false,false),Float.NaN,0);
        assertEquals(before,m.snapshot(0,1));m.tick(BodyEnvironment.temperate(false,false),0,Float.NaN);
        assertEquals(before,m.snapshot(0,1));
    }
    @Test void actualSaveAndClientPlaybackPathsUseNewStateWithoutEditingLastStanding() throws Exception {
        var root=Path.of("src/main/java/io/github/safedecimal/decayingaftermath");
        var server=Files.readString(root.resolve("server/ServerMedicalSystem.java"));
        assertTrue(server.contains("node.putFloat(\"brain_health\",body.brainHealth())"));
        assertTrue(server.contains("withBrainHealth(number(node,\"brain_health\",1,1))"));
        var client=Files.readString(root.resolve("client/MedicalClient.java"));assertTrue(client.contains("DEATH_MUSIC.advance(body)"));
        assertTrue(Files.readString(root.resolve("client/MedicalLoopSound.java")).contains("!manager.isActive(this)"));
    }
}
