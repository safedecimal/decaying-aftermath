package io.github.safedecimal.decayingaftermath.compat;
import static org.junit.jupiter.api.Assertions.*;
import io.github.safedecimal.decayingaftermath.medical.*;
import org.junit.jupiter.api.Test;
class CruorVisualPolicyTest {
 private static BodySnapshot state(float bleed,BodySnapshot.Mode mode){var parts=new BodyModel().snapshot().parts();parts[BodyPart.LEFT_ARM.ordinal()]=new BodySnapshot.PartSnapshot(.4f,bleed,.5f,false,true,false,false);return new BodySnapshot(.1f,.3f,.8f,.5f,0,80,mode,0,parts);}
 @Test void stoppedMedicalBleedingOverridesOnlyCosmeticHpFallback(){var body=state(0,BodySnapshot.Mode.CONSCIOUS);assertEquals(20,CruorVisualPolicy.fallbackHealth(body,2,20));assertEquals(.1f,body.condition());assertEquals(0,CruorVisualPolicy.nativeBleed(body));}
 @Test void activeBleedingUsesActualMedicalAmount(){var body=state(.6f,BodySnapshot.Mode.CONSCIOUS);assertEquals(60,CruorVisualPolicy.nativeBleed(body),.0001);assertEquals(2,CruorVisualPolicy.fallbackHealth(body,2,20));}
 @Test void remotePlayersAndMobsWithoutTheirOwnSnapshotAreNotRewritten(){var local=state(0,BodySnapshot.Mode.CONSCIOUS);assertEquals(2,CruorVisualPolicy.fallbackHealth(false,local,2,20));}
 @Test void corpseFallbackIsNotRewritten(){assertEquals(2,CruorVisualPolicy.fallbackHealth(state(0,BodySnapshot.Mode.DEAD),2,20));}
 @Test void visualFlowBoundedForMultipleWounds(){var p=new BodyModel().snapshot().parts();for(int i=0;i<p.length;i++)p[i]=new BodySnapshot.PartSnapshot(.2f,1,.8f,false,false,false,false);var body=new BodySnapshot(.1f,.2f,.5f,.8f,0,140,BodySnapshot.Mode.LAST_STANDING,1,p);assertEquals(100,CruorVisualPolicy.nativeBleed(body));}
}
