package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.client.*;
import io.github.safedecimal.decayingaftermath.network.BodyPayload;
import io.github.safedecimal.decayingaftermath.server.SurvivalPersistence;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.safedecimal.decayingaftermath.medical.BodyPart.*;
import static io.github.safedecimal.decayingaftermath.medical.TreatmentKind.*;

class MedicalMonitorRevisionTest {
    private BodyEnvironment still() { return new BodyEnvironment(20,0,false,true,false,false,false,0,32,false,false); }
    private BodySnapshot body(SurvivalSnapshot s) {
        var b=new BodyModel().snapshot();
        return new BodySnapshot(1,s.bloodVolume(),1,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,1,1,b.parts(),s);
    }
    private SurvivalSnapshot state(float water,float food,float toxin,float gut,float mass,float radiation,float loss) {
        return new SurvivalSnapshot(water,food,37,0,toxin,1,1,0,0,0,0,0,1,0,gut,mass,radiation,loss);
    }
    @Test void requestedReadoutsAreNumericNotDashesAndHealthyPressureIs120Over80() {
        var b=new BodyModel().snapshot();
        for(var readout:CuMedicalGeometry.READOUTS)assertFalse(CuMedicalMonitor.value(b,readout.field(),72).contains("--"),readout.field());
        assertEquals("75.0kg",CuMedicalMonitor.value(b,"WeightText",72));
        assertEquals("72|120/80",CuMedicalMonitor.value(b,"BPMText",72));
        assertEquals("16/m",CuMedicalMonitor.value(b,"RRText",72));
        assertEquals("100%",CuMedicalMonitor.value(b,"HappinessText",72));
        assertEquals("100%",CuMedicalMonitor.value(b,"ImmuneText",72));
    }
    @Test void woundInfectionNeverBecomesDigestiveSicknessButPoisonAndGutIllnessDo() {
        var b=new BodyModel().snapshot();var parts=b.parts();
        parts[LEFT_ARM.ordinal()]=new BodySnapshot.PartSnapshot(.5f,0,0,false,false,false,false,.9f,false,false);
        var wound=new BodySnapshot(1,1,1,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,1,1,parts,b.survival());
        assertEquals("0%",CuMedicalMonitor.value(wound,"SicknessText",72));
        assertEquals("80%",CuMedicalMonitor.value(body(state(1,1,.8f,0,75,0,0)),"SicknessText",72));
        assertEquals("40%",CuMedicalMonitor.value(body(state(1,1,0,.4f,75,0,0)),"SicknessText",72));
        assertEquals("Тошнота / отравление",CuMedicalMonitor.label("SicknessText"));
    }
    @Test void pressureAndRespirationRespondToBloodLossOxygenAndOpiates() {
        var full=new BodyModel().snapshot();var s=state(1,1,0,0,75,0,0);
        var shock=new BodySnapshot(1,.2f,.5f,.4f,0,140,BodySnapshot.Mode.CONSCIOUS,1,.2f,.5f,full.parts(),s);
        assertTrue(MedicalPhysiology.systolic(shock)<90);
        assertTrue(MedicalPhysiology.respiratoryRate(shock)>MedicalPhysiology.respiratoryRate(full));
        var sedated=new BodySnapshot(1,1,1,0,2,45,BodySnapshot.Mode.UNCONSCIOUS,0,1,.2f,full.parts(),s);
        assertTrue(MedicalPhysiology.respiratoryRate(sedated)<8);
    }
    @Test void moodAndImmunityRespondToReservesPainSicknessAndRadiation() {
        var healthy=new BodyModel().snapshot();var sick=body(state(.2f,.1f,.8f,.5f,75,2,0));
        assertTrue(MedicalPhysiology.mood(sick)<MedicalPhysiology.mood(healthy));
        assertTrue(MedicalPhysiology.immunity(sick)<MedicalPhysiology.immunity(healthy));
    }
    @Test void massChangesFromAbsorbedWaterExcessFoodAndProlongedDeficitNotInventoryWeight() {
        var m=new SurvivalModel(state(.5f,1,0,0,74,0,0));m.feed(.2f,.2f);
        for(int i=0;i<1000;i++)m.tick(still(),0,0);
        assertTrue(m.snapshot(0,1).bodyMassKg()>74.4f);
        var hungry=new SurvivalModel(state(1,0,0,0,75,0,0));
        for(int i=0;i<10000;i++)hungry.tick(BodyEnvironment.temperate(false,false),0,0);
        assertTrue(hungry.snapshot(0,1).bodyMassKg()<75);
        var load=new SurvivalModel();load.tick(new BodyEnvironment(20,0,false,true,false,false,false,200,32,false,false),0,0);
        assertEquals(75,load.snapshot(0,1).bodyMassKg());
    }
    @Test void doseAccumulatesAndPersistsRejectingNonFiniteOrNegativeExposures() {
        var m=new SurvivalModel();m.irradiate(.25f);m.irradiate(.75f);m.irradiate(Float.NaN);m.irradiate(-1);
        assertEquals(1,m.snapshot(0,1).radiationGy());
        assertEquals(m.snapshot(0,1),SurvivalModel.restore(m.save(0,1)).snapshot(0,1));
        assertEquals("1.00gy",CuMedicalMonitor.value(body(m.snapshot(0,1)),"RadText",72));
    }
    @Test void cumulativeBloodLossUsesActualRemovedVolumeAndIsNotErasedByInfusion() {
        var m=new SurvivalModel();m.tick(still(),.2f,0);
        assertEquals(1.05f,m.snapshot(0,1).bloodLostLiters(),.00001f);
        assertEquals("1.05L",CuMedicalMonitor.value(body(m.snapshot(0,1)),"BleedText",72));
        m.treat(BLOOD_BAG,1);assertEquals(1.05f,m.snapshot(0,1).bloodLostLiters(),.00001f);
        var empty=new SurvivalModel(new SurvivalSnapshot(1,1,37,0,0,0,0,0,0,0,0,0,1,0));
        empty.tick(still(),1,0);assertEquals(0,empty.snapshot(0,1).bloodLostLiters());
    }
    @Test void surfaceAndConcealedBleedingBothIncreaseTheMedicalLossCounter() {
        var b=new BodyModel();b.damage(LEFT_ARM,4,DamageKind.CUT);b.tick(still());assertTrue(b.snapshot().survival().bloodLostLiters()>0);
        var internal=new BodyModel();internal.damage(LEFT_ARM,8,DamageKind.BLUNT);
        assertEquals(0,internal.snapshot().part(LEFT_ARM).bleeding());internal.tick(still());
        assertTrue(internal.snapshot().survival().bloodLostLiters()>0);
    }
    @Test void theTwoLungsDrainSequentiallyFromEnergyAndRecoverTogether() {
        var base=new BodyModel().snapshot();
        for(float energy:new float[]{0,.25f,.5f,.75f,1}) {
            var b=new BodySnapshot(1,1,1,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,energy,1,base.parts(),base.survival());
            assertEquals(Math.clamp(energy*2,0,1),CuMedicalMonitor.amount(b,"LungFill1"));
            assertEquals(Math.clamp(energy*2-1,0,1),CuMedicalMonitor.amount(b,"LungFill2"));
            assertEquals(Math.round(energy*100)+"%",CuMedicalMonitor.tooltipValue(b,"LungFill2",72));
        }
    }
    @Test void everyMonitorTooltipContainsOnlyNameAndValueAndNoProvenanceOrDisclaimers() {
        var b=new BodyModel().snapshot();
        for(var r:CuMedicalGeometry.READOUTS) {
            String text=CuMedicalMonitor.label(r.field())+CuMedicalMonitor.tooltipValue(b,r.field(),72)+CuMedicalMonitor.explanation(r.field());
            for(String unwanted:new String[]{"CU","Minecraft","условн","передаётся","прочерк","клиничес"})assertFalse(text.contains(unwanted),text);
        }
        assertEquals("37.0°C",CuMedicalMonitor.value(b,"TempText",72));
        assertEquals(4,MedicalPanelGlyphs.advance('°'));
    }
    @Test void internalBleedingIsOnlyOnMonitorNotOnBodyTooltipsOrBodyPartCommandReport() throws Exception {
        var root=Path.of(System.getProperty("user.dir"));var j=root.resolve("src/main/java/io/github/safedecimal/decayingaftermath");
        assertFalse(Files.readString(j.resolve("client/MedicalInventoryScreen.java")).contains("part.internalBleeding()"));
        assertFalse(Files.readString(j.resolve("server/MedicalCommands.java")).contains("p.internalBleeding()"));
        assertEquals("HemothoraxFill",CuMedicalMonitor.hover(new BodyModel().snapshot(),18,130));
    }
    @Test void nativePacketRoundTripIncludesAllNewPersistentFieldsAndConsumesExactBytes() {
        var base=body(state(.6f,.4f,.8f,.3f,82.6f,1.75f,2.25f));
        var treated=BodyModel.restore(base);treated.treat(BodyPart.LEFT_ARM,TreatmentKind.ANTISEPTIC,.75f);
        treated.damage(RIGHT_ARM,2,DamageKind.CUT);
        treated.damage(RIGHT_ARM,5,DamageKind.CUT);
        treated.damage(RIGHT_ARM,15,DamageKind.PUNCTURE);
        base=treated.snapshot();
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            for(var group:BloodGroup.values()) {
                buffer.clear();
                var b=base.withBloodGroup(group);
                BodyPayload.CODEC.encode(buffer,new BodyPayload(b));
                var decoded=BodyPayload.CODEC.decode(buffer).snapshot();
                assertEquals(group,decoded.bloodGroup());
                assertEquals(b.survival(),decoded.survival());assertArrayEquals(b.parts(),decoded.parts());
                assertEquals(buffer.writerIndex(),buffer.readerIndex());
            }
            assertTrue(BodyPayload.TYPE.id().toString().endsWith("body_v9"));
        } finally { buffer.release(); }
    }
    private ValueInput input(Map<String,Object> data) {
        return (ValueInput)Proxy.newProxyInstance(ValueInput.class.getClassLoader(),new Class<?>[]{ValueInput.class},(p,m,args)->{
            String name=m.getName();String key=args!=null&&args.length>0?(String)args[0]:"";
            if(name.equals("childOrEmpty"))return input((Map<String,Object>)data.getOrDefault(key,new HashMap<>()));
            if(name.equals("getFloatOr")||name.equals("getIntOr")||name.equals("getBooleanOr"))return data.getOrDefault(key,args[1]);
            throw new UnsupportedOperationException(name);
        });
    }
    private ValueOutput output(Map<String,Object> data) {
        return (ValueOutput)Proxy.newProxyInstance(ValueOutput.class.getClassLoader(),new Class<?>[]{ValueOutput.class},(p,m,args)->{
            if(m.getName().equals("child")){var child=new HashMap<String,Object>();data.put((String)args[0],child);return output(child);}
            if(m.getName().startsWith("put")){data.put((String)args[0],args[1]);return null;}
            throw new UnsupportedOperationException(m.getName());
        });
    }
    @Test void nativeSaveLoadPreservesMassDoseAndBloodLossAndOldSavesReceiveDefaults() {
        var model=BodyModel.restore(body(state(.6f,.4f,.8f,.3f,82.6f,1.75f,2.25f)));var data=new HashMap<String,Object>();data.put("version",3);
        SurvivalPersistence.save(output(data),model.saveState());
        var loaded=SurvivalPersistence.load(input(data),new BodyModel().saveState()).survivalState().snapshot();
        assertEquals(82.6f,loaded.bodyMassKg());assertEquals(1.75f,loaded.radiationGy());assertEquals(2.25f,loaded.bloodLostLiters());
        var legacy=SurvivalPersistence.load(input(Map.of("version",3)),new BodyModel().saveState()).survivalState().snapshot();
        assertEquals(75,legacy.bodyMassKg());assertEquals(0,legacy.radiationGy());assertEquals(0,legacy.bloodLostLiters());
    }
    @Test void typedSaveKeepsBasePartDataAndRoundTripsActiveAndSuppressedProfiles() {
        var model=new BodyModel();
        for(var part:new BodyPart[]{LEFT_ARM,RIGHT_ARM}) {
            model.damage(part,2,DamageKind.CUT);
            model.damage(part,5,DamageKind.CUT);
            model.damage(part,15,DamageKind.PUNCTURE);
        }
        model.treat(RIGHT_ARM,TOURNIQUET,1);
        var saved=model.saveState();
        var data=new HashMap<String,Object>();data.put("version",4);
        SurvivalPersistence.save(output(data),saved);
        var left=input(data).childOrEmpty("part"+LEFT_ARM.ordinal());
        assertEquals(saved.snapshot().part(LEFT_ARM).integrity(),left.getFloatOr("integrity",1));
        assertEquals(saved.snapshot().part(LEFT_ARM).bleeding(),left.getFloatOr("bleeding",0));
        var oldParts=saved.snapshot().parts();
        oldParts[LEFT_ARM.ordinal()]=oldParts[LEFT_ARM.ordinal()].withExternalBleeding(BleedProfile.NONE);
        var legacy=new BodyModel.SavedState(saved.snapshot().withParts(oldParts),saved.careTicks(),
                saved.regenerationTicks(),saved.tourniquetTicks(),false,0);
        var restored=BodyModel.restoreState(SurvivalPersistence.load(input(data),legacy));
        assertEquals(saved.snapshot().part(LEFT_ARM).externalBleeding(),restored.snapshot().part(LEFT_ARM).externalBleeding());
        assertEquals(BleedProfile.NONE,restored.snapshot().part(RIGHT_ARM).externalBleeding());
        assertTrue(restored.removeTourniquet(RIGHT_ARM));
        assertEquals(saved.wounds().suppressedFlows()[RIGHT_ARM.ordinal()],restored.snapshot().part(RIGHT_ARM).externalBleeding());
    }
    @Test void versionThreeUntypedFlowAndTourniquetResidualKeepLegacyRates() {
        var old=new BodyModel().snapshot();var parts=old.parts();
        parts[LEFT_ARM.ordinal()]=new BodySnapshot.PartSnapshot(.6f,.2f,.1f,false,false,false,false);
        parts[RIGHT_ARM.ordinal()]=new BodySnapshot.PartSnapshot(.6f,0,.1f,false,false,false,true);
        var legacy=new BodyModel.SavedState(old.withParts(parts),new int[6],new int[6],new int[6],false,0);
        var data=Map.<String,Object>of("version",3,"part"+RIGHT_ARM.ordinal(),Map.of("tourniquet_residual",.35f));
        var restored=BodyModel.restoreState(SurvivalPersistence.load(input(data),legacy));
        assertEquals(new BleedProfile(0,.2f,0),restored.snapshot().part(LEFT_ARM).externalBleeding());
        assertTrue(restored.removeTourniquet(RIGHT_ARM));
        assertEquals(new BleedProfile(0,.35f,0),restored.snapshot().part(RIGHT_ARM).externalBleeding());
    }
    @Test void nativeSaveLoadPreservesWakeHysteresisAndOldSavesDefaultToArmed() {
        var model=new BodyModel();var saved=model.saveState();
        var recovered=new BodyModel.SavedState(saved.snapshot(),saved.careTicks(),saved.regenerationTicks(),
                saved.tourniquetTicks(),saved.lastStandingSpent(),saved.stableTicks(),saved.lastStandingTicks(),
                saved.lastStandingBoostTicks(),saved.dyingTicks(),saved.disinfectionTicks(),saved.anestheticTicks(),
                saved.infectionAgeTicks(),saved.skinHealAmount(),saved.bandageSlowAmount(),saved.rawLocalPain(),
                saved.wounds(),saved.survivalState(),true);
        var data=new HashMap<String,Object>();data.put("version",3);SurvivalPersistence.save(output(data),recovered);
        assertEquals(true,data.get("faint_recovered"));
        assertTrue(SurvivalPersistence.load(input(data),saved).faintRecovered());
        assertFalse(SurvivalPersistence.load(input(Map.of("version",3)),saved).faintRecovered());
    }
    @Test void unnecessaryProceduresAreAcceptedAndWrongPlacementWastesWithoutBenefit() {
        for(var part:BodyPart.values())for(var kind:TreatmentKind.values()) {
            var b=new BodyModel();assertTrue(b.canTreat(part,kind));
            if(kind!=POTION)assertTrue(b.treat(part,kind,1),part+"/"+kind);
        }
        var b=new BodyModel();assertTrue(b.treat(HEAD,SPLINT,1));assertFalse(b.snapshot().part(HEAD).splinted());
        assertTrue(b.treat(LEFT_ARM,CHEST_DRAIN,1));assertFalse(b.saveState().wounds().chestDrain());
    }
    @Test void needlessTourniquetAndExcessMedicationStillHaveConsequences() {
        var b=new BodyModel();b.treat(LEFT_ARM,TOURNIQUET,1);for(int i=0;i<1300;i++)b.tick(still());
        assertTrue(b.snapshot().part(LEFT_ARM).integrity()<1);assertTrue(b.removeTourniquet(LEFT_ARM));
        var medication=new BodyModel();for(int i=0;i<4;i++)medication.treat(TORSO,COMBAT_STIMULANT,1);
        for(int i=0;i<100;i++)medication.tick(still());assertTrue(medication.snapshot().survival().toxin()>0);
    }
    @Test void serverInventorySessionAndFiniteInputGuardsAreNotRemoved() throws Exception {
        var j=Path.of(System.getProperty("user.dir"),"src/main/java/io/github/safedecimal/decayingaftermath/network/MedicalNetwork.java");var s=Files.readString(j);
        assertFalse(s.contains("thought.not_needed"));assertTrue(s.contains("!Float.isFinite(request.amount())"));
        assertTrue(s.contains("!ItemStack.isSameItemSameComponents(session.signature,stack)"));
        assertTrue(s.contains("planUnused(player.inventoryMenu,request.slot(),stack)"));
        assertFalse(new BodyModel().treat(LEFT_ARM,BANDAGE,Float.NaN));
    }
}
