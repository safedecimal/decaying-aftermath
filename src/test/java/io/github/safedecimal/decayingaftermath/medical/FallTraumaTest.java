package io.github.safedecimal.decayingaftermath.medical;

import io.github.safedecimal.decayingaftermath.client.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.safedecimal.decayingaftermath.medical.BodyPart.*;

class FallTraumaTest {
    private BodyEnvironment still() { return new BodyEnvironment(20,0,false,true,false,false,false,0,32,false,false); }
    @Test void fallPlanConservesTheOneMitigatedDamageBudget() {
        for(float damage:new float[]{.001f,1,4,8,12,20,40,100,1000,2000}) {
            var hits=FallTrauma.hits(damage);assertEquals(6,hits.size());
            assertEquals(Math.min(1000,damage),hits.stream().mapToDouble(FallTrauma.Hit::amount).sum(),.001);
            for(var hit:hits)assertTrue(Float.isFinite(hit.amount())&&hit.amount()>=0);
        }
    }
    @Test void invalidAndNonPositiveFallInputsAreInert() {
        for(float value:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}) {
            assertTrue(FallTrauma.hits(value).isEmpty());var b=new BodyModel();b.fall(value);assertEquals(1,b.snapshot().condition());
        }
    }
    @Test void minorFallsDoNotInventOpenWoundsOrWholeBodyTrauma() {
        var b=new BodyModel();b.fall(4);
        assertTrue(b.snapshot().part(LEFT_LEG).integrity()<1);
        for(var part:b.snapshot().parts()){assertEquals(0,part.bleeding());assertEquals(0,part.internalBleeding());assertFalse(part.openWound());}
        for(var part:new BodyPart[]{HEAD,TORSO,LEFT_ARM,RIGHT_ARM})assertEquals(1,b.snapshot().part(part).integrity());
    }
    @Test void highFallsHaveVisibleExternalAndInternalBloodLossAndOpenContactWounds() {
        var b=new BodyModel();b.fall(40);var s=b.snapshot();
        assertTrue(s.part(LEFT_LEG).openWound());assertTrue(s.part(RIGHT_LEG).openWound());assertTrue(s.part(TORSO).openWound());
        assertTrue(CuMedicalMonitor.externalBleeding(s)>0);assertTrue(s.survival().internalBleeding()>0);
        assertNotNull(MedicalStatusIcons.bleeding(s.part(LEFT_LEG)));assertNotNull(CuMedicalMonitor.externalBleedSource(s));
        assertTrue(CuMedicalMonitor.critical(s,"BleedText"));
        b.tick(still());assertTrue(b.snapshot().survival().bloodLostLiters()>0);
    }
    @Test void allPartsCanBeHurtButHandsStayMuchLessDamagedThanLegsAndTorso() {
        var b=new BodyModel();b.fall(40);var s=b.snapshot();
        for(var p:s.parts())assertTrue(p.integrity()<1);
        assertTrue(s.part(LEFT_LEG).fractured());assertTrue(s.part(RIGHT_LEG).fractured());
        assertFalse(s.part(LEFT_ARM).fractured());assertFalse(s.part(RIGHT_ARM).fractured());
        assertTrue(s.part(LEFT_ARM).integrity()>s.part(TORSO).integrity());
        assertTrue(s.part(LEFT_ARM).integrity()>s.part(LEFT_LEG).integrity());
    }
    @Test void plainBluntAndBurnTraumaStillDoNotCreateAutomaticCuts() {
        for(var kind:new DamageKind[]{DamageKind.BLUNT,DamageKind.BURN}) {
            var b=new BodyModel();b.damage(LEFT_ARM,8,kind);assertEquals(0,b.snapshot().part(LEFT_ARM).bleeding());
        }
    }
    @Test void cutWoundsAlwaysRetainTheirExternalIndicatorOutsideFalls() {
        var b=new BodyModel();b.damage(LEFT_ARM,4,DamageKind.CUT);var s=b.snapshot();
        assertNotNull(CuMedicalMonitor.externalBleedSource(s));assertTrue(CuMedicalMonitor.bleedFlowMlPerMinute(s,false)>0);
        assertEquals(0,CuMedicalMonitor.bleedFlowMlPerMinute(s,true));
    }
    @Test void currentFlowRatesSeparateOutsideAndInsideAndMatchTheLossPerTick() {
        var b=new BodyModel();b.fall(40);var before=b.snapshot();
        float external=CuMedicalMonitor.externalBleeding(before),internal=before.survival().internalBleeding();
        float total=Math.min(before.survival().bloodVolume(),(external+internal)*.00065f)*1200*75*.07f*1000;
        assertEquals(total,CuMedicalMonitor.bleedFlowMlPerMinute(before,false)+CuMedicalMonitor.bleedFlowMlPerMinute(before,true),1);
        assertEquals(2,CuMedicalMonitor.bleedingDetails(before).size());
        assertTrue(CuMedicalMonitor.bleedingDetails(before).get(0).startsWith("Наружное:"));
        assertTrue(CuMedicalMonitor.bleedingDetails(before).get(1).startsWith("Внутреннее:"));
    }
    @Test void emptyBloodPoolCannotReportActualBloodFlow() {
        var parts=new BodyModel().snapshot().parts();parts[0]=new BodySnapshot.PartSnapshot(.5f,.2f,0,false,false,false,false);
        var s=new SurvivalSnapshot(1,1,37,0,0,0,0,0,0,0,0,.2f,1,0);
        var b=new BodySnapshot(1,0,1,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,1,1,parts,s);
        assertEquals(0,CuMedicalMonitor.bleedFlowMlPerMinute(b,false));assertEquals(0,CuMedicalMonitor.bleedFlowMlPerMinute(b,true));
    }
    @Test void stopTheWoundAndActiveExternalIndicatorDisappearsButCumulativeLossRemains() {
        var b=new BodyModel();b.damage(LEFT_ARM,4,DamageKind.CUT);b.tick(still());float lost=b.snapshot().survival().bloodLostLiters();
        b.treat(LEFT_ARM,TreatmentKind.TOURNIQUET,1);
        assertEquals(0,CuMedicalMonitor.externalBleeding(b.snapshot()));assertNull(CuMedicalMonitor.externalBleedSource(b.snapshot()));
        assertEquals(lost,b.snapshot().survival().bloodLostLiters());
    }
    @Test void visibilityFloorDoesNotReplaceTheStoredNativeSeverityGradient() {
        var part=new BodySnapshot.PartSnapshot(.5f,1,0,false,false,false,false);
        int nativeColor=MedicalStatusIcons.bleedingColor(part),visible=MedicalStatusIcons.visibleBleedingColor(nativeColor);
        assertEquals(0xFF340600,nativeColor);assertTrue(((visible>>>16)&255)>=192);assertEquals(nativeColor&0xFFFF,visible&0xFFFF);
    }
    @Test void allFallTraumasSurviveBodySaveRestoreIncludingExternalWounds() {
        var b=new BodyModel();b.fall(40);var restored=BodyModel.restoreState(b.saveState()).snapshot();
        for(var part:BodyPart.values())assertEquals(b.snapshot().part(part),restored.part(part));
        assertEquals(b.snapshot().survival(),restored.survival());
    }
}
