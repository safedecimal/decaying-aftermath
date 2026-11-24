package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.safedecimal.decayingaftermath.medical.BodyPart.*;
import static io.github.safedecimal.decayingaftermath.medical.TreatmentKind.*;

/** Pure model regression fixtures; no game/window bootstrap. */
class SurvivalModelTest {
    private static void ticks(BodyModel m,int count){for(int i=0;i<count;i++)m.tick(false,false);}
    @Test void burnsAndClosedBluntTraumaDoNotInventCutsOrBurnFractures() {
        BodyModel burn=new BodyModel(),blunt=new BodyModel();
        burn.damage(LEFT_ARM,8,DamageKind.BURN);blunt.damage(LEFT_ARM,8,DamageKind.BLUNT);
        assertEquals(0,burn.snapshot().part(LEFT_ARM).bleeding());assertFalse(burn.snapshot().part(LEFT_ARM).fractured());
        assertEquals(0,blunt.snapshot().part(LEFT_ARM).bleeding());ticks(blunt,4000);
        assertEquals(0,blunt.snapshot().part(LEFT_ARM).infection());
    }
    @Test void normalSplintedBoneRecoveryTakesTimeAndDoesNotComeFromPainkillers() {
        BodyModel b=new BodyModel();b.damage(LEFT_LEG,8,DamageKind.BLUNT);b.treat(LEFT_LEG,SPLINT,1);
        b.treat(TORSO,PAINKILLERS,1);ticks(b,100);assertTrue(b.snapshot().part(LEFT_LEG).fractured());
        ticks(b,BodyModel.BONE_HEAL_TICKS+100);assertFalse(b.snapshot().part(LEFT_LEG).fractured());
    }
    @Test void salineDoesNotRestoreErythrocytes() {
        BodyModel b=BodyModel.restore(new BodySnapshot(1,.4f,1,0,0,72,BodySnapshot.Mode.CONSCIOUS,1,new BodyModel().snapshot().parts()));
        float red=b.snapshot().survival().redCells();assertTrue(b.treat(TORSO,SALINE,1));
        assertEquals(red,b.snapshot().survival().redCells());assertTrue(b.snapshot().survival().plasma()>red);
        float capacity=b.snapshot().survival().oxygenDelivery();b.treat(TORSO,BLOOD_BAG,1);
        assertTrue(b.snapshot().survival().redCells()>red);assertTrue(b.snapshot().survival().oxygenDelivery()>capacity);
    }
    @Test void naloxoneDoesNotDeleteOpiateAndStarvationDoesNotDirectlyRemoveOxygen() {
        BodyModel b=new BodyModel();b.treat(LEFT_ARM,FENTANYL,1);float drug=b.snapshot().drug();
        b.treat(TORSO,NALOXONE,1);assertEquals(drug,b.snapshot().drug());assertTrue(b.snapshot().survival().antagonist()>0);
        float oxygen=b.snapshot().oxygen(),energy=b.snapshot().energy();b.starve(2);
        assertEquals(oxygen,b.snapshot().oxygen());assertTrue(b.snapshot().energy()<energy);
    }
    @Test void coldInjuryRejectsIceAndColdFluidIsNotMagicallyHeatedByCooling() {
        BodyModel b=new BodyModel();b.damage(LEFT_ARM,8,DamageKind.FROSTBITE);
        assertTrue(b.snapshot().part(LEFT_ARM).coldInjury());assertTrue(b.canTreat(LEFT_ARM,ICE_PACK));
        var cold=new SurvivalSnapshot(1,1,30,0,0,1,1,0,0,0,0,0,1,0);
        SurvivalModel m=new SurvivalModel(cold);m.cool(1);assertEquals(30,m.temperature());
    }
    @Test void laterContaminatedSipsDoNotResetExistingIncubation() {
        SurvivalModel m=new SurvivalModel();m.drinkContaminated(.2f,0);
        for(int i=0;i<800;i++)m.tick(BodyEnvironment.temperate(false,false),0,0);
        m.drinkContaminated(.2f,0);assertEquals(400,m.save(0,1).incubationTicks());
        for(int i=0;i<800;i++)m.tick(BodyEnvironment.temperate(false,false),0,0);
        assertTrue(m.snapshot(0,1).gutInfection()>0);
    }
    @Test void fittedTourniquetDoesNotCreateBleedingAfterItsWoundClotted() {
        BodyModel b=new BodyModel();b.damage(LEFT_LEG,8);b.treat(LEFT_LEG,TOURNIQUET,1);ticks(b,9000);
        assertEquals(0,b.saveState().wounds().suppressedFlows()[LEFT_LEG.ordinal()].total());
        assertTrue(b.removeTourniquet(LEFT_LEG));assertEquals(0,b.snapshot().part(LEFT_LEG).bleeding());
        assertFalse(b.removeTourniquet(LEFT_LEG));
    }
    @Test void healingCareCannotBypassIschemiaOrLowErythrocyteMass() {
        BodyModel b=new BodyModel();b.damage(LEFT_ARM,8);b.treat(LEFT_ARM,TOURNIQUET,1);
        float before=b.snapshot().part(LEFT_ARM).integrity();b.treat(LEFT_ARM,HEALING_POTION,1);b.treat(LEFT_ARM,REGENERATION_POTION,1);
        ticks(b,100);assertEquals(before,b.snapshot().part(LEFT_ARM).integrity());
        var p=new BodyModel().snapshot().parts();p[LEFT_ARM.ordinal()]=new BodySnapshot.PartSnapshot(.6f,0,.2f,false,false,false,false);
        var s=new SurvivalSnapshot(1,1,37,0,0,1,.1f,0,0,0,0,0,1,0);
        BodyModel anemic=BodyModel.restore(new BodySnapshot(1,s.bloodVolume(),1,.2f,0,72,BodySnapshot.Mode.CONSCIOUS,1,1,1,p,s));
        anemic.treat(LEFT_ARM,HEALING_POTION,1);ticks(anemic,100);
        assertEquals(.6f,anemic.snapshot().part(LEFT_ARM).integrity());
    }
    @Test void supernaturalLastStandingStillRestoresItsOriginalReserves() {
        BodyModel b=new BodyModel();b.damage(HEAD,34);var s=b.snapshot();
        assertEquals(BodySnapshot.Mode.LAST_STANDING,s.mode());assertEquals(.75f,s.part(HEAD).integrity());
        assertTrue(s.blood()>=.5f);assertEquals(1,s.oxygen());assertEquals(1,s.energy());
        assertEquals(BodyModel.LAST_STANDING_TICKS,b.saveState().lastStandingTicks());
    }
}
