package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.safedecimal.decayingaftermath.medical.BodyPart.*;
import static io.github.safedecimal.decayingaftermath.medical.TreatmentKind.*;
import static io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode.*;

/** Deterministic game-state checks, not real physiology or an actual game-window test. */
class BodyVitalsTest {
    private static void ticks(BodyModel body, int count) { for (int i=0;i<count;i++) body.tick(false,false); }
    private static BodyModel fragile() {
        var parts = new BodyModel().snapshot().parts();
        parts[LEFT_ARM.ordinal()] = new BodySnapshot.PartSnapshot(.6f,.08f,.7f,false,false,false,false);
        return BodyModel.restore(new BodySnapshot(.12f,.162f,.8f,.4f,0,105,UNCONSCIOUS,0,parts));
    }

    @Test void lastStandingRestoresReservesInsteadOfSchedulingDeath() {
        BodyModel body = new BodyModel(); body.damage(HEAD,34);
        var state = body.snapshot();
        assertEquals(LAST_STANDING,state.mode());
        assertTrue(state.part(HEAD).integrity() >= .75f);
        assertTrue(state.blood() >= .5f); assertEquals(1,state.oxygen()); assertEquals(1,state.energy());
        assertEquals(.408f*.05f,state.part(HEAD).bleeding(),.00001);
        ticks(body,BodyModel.LAST_STANDING_TICKS+800);
        assertNotEquals(DEAD,body.snapshot().mode());
        assertTrue(body.snapshot().bpm()>0);
    }

    @Test void adrenalineExpiryDoesNotKillTheRestoredBody() {
        BodyModel body = new BodyModel(); body.damage(HEAD,34);
        ticks(body,BodyModel.LAST_STANDING_BOOST_TICKS+200);
        assertNotEquals(DEAD,body.snapshot().mode());
        assertEquals(0,body.saveState().lastStandingBoostTicks());
    }

    @Test void postLastStandProvidesFullDyingWindowAndAcceptsLateCare() {
        BodyModel body = fragile(); ticks(body,60);
        assertEquals(DYING,body.snapshot().mode());
        assertTrue(body.snapshot().consciousnessProgress()>.9f);
        for (int i=0;i<5;i++) { assertTrue(body.treat(LEFT_ARM,BANDAGE,1)); ticks(body,40); }
        ticks(body,1800);
        assertNotEquals(DEAD,body.snapshot().mode());
        assertEquals(0,body.snapshot().part(LEFT_ARM).bleeding());
    }

    @Test void untreatedCriticalStateEventuallyDiesRatherThanBecomingImmortal() {
        BodyModel body = fragile(); ticks(body,BodyModel.DYING_TICKS+200);
        assertEquals(DEAD,body.snapshot().mode()); assertEquals(0,body.snapshot().bpm());
        assertFalse(body.treat(LEFT_ARM,ANESTHETIC,1));
    }

    @Test void oneBandageTurnDoesNotInstantlyStopSevereBleeding() {
        BodyModel body = new BodyModel(); body.damage(LEFT_ARM,60);
        float initial = body.snapshot().part(LEFT_ARM).bleeding();
        assertTrue(body.treat(LEFT_ARM,BANDAGE,1));
        assertEquals(initial,body.snapshot().part(LEFT_ARM).bleeding());
        assertEquals(.0375,body.snapshot().part(LEFT_ARM).bandageCare(),.000001);
        ticks(body,60);
        assertTrue(body.snapshot().part(LEFT_ARM).bleeding()>.65f);
        assertEquals(0,body.snapshot().part(LEFT_ARM).bandageCare(),.000001);
    }

    @Test void severeBleedingNeedsMoreThanOneTwelveTurnRoll() {
        BodyModel body = new BodyModel(); body.damage(LEFT_ARM,60);
        int turns=0;
        while (turns<40 && body.snapshot().part(LEFT_ARM).bleeding()>.001f) {
            body.treat(LEFT_ARM,BANDAGE,1); ticks(body,60); turns++;
            if (turns==12) assertTrue(body.snapshot().part(LEFT_ARM).bleeding()>.1f);
        }
        assertTrue(turns>=16&&turns<=22,"Gradual native care, not an instant layer completion");
        assertEquals(0,body.snapshot().part(LEFT_ARM).bleeding());
        assertNotEquals(DEAD,body.snapshot().mode());
    }

    @Test void batchedNetworkTurnsDoNotLoseCareToUnitClamp() {
        BodyModel whole=new BodyModel(),parts=new BodyModel();
        whole.damage(LEFT_ARM,60);parts.damage(LEFT_ARM,60);
        whole.treat(LEFT_ARM,BANDAGE,12);
        for (int i=0;i<12;i++) parts.treat(LEFT_ARM,BANDAGE,1);
        assertEquals(.45,whole.saveState().bandageSlowAmount()[LEFT_ARM.ordinal()],.00001);
        assertEquals(parts.saveState().bandageSlowAmount()[LEFT_ARM.ordinal()],whole.saveState().bandageSlowAmount()[LEFT_ARM.ordinal()],.00001);
        assertEquals(.3,whole.saveState().skinHealAmount()[LEFT_ARM.ordinal()],.00001);
    }

    @Test void skinCareHealsGraduallyAndHasNativePerTurnBudget() {
        BodyModel body=new BodyModel();body.damage(LEFT_ARM,10);
        float before=body.snapshot().part(LEFT_ARM).integrity();body.treat(LEFT_ARM,BANDAGE,1);
        assertEquals(before,body.snapshot().part(LEFT_ARM).integrity());
        ticks(body,100);assertEquals(before+.025f,body.snapshot().part(LEFT_ARM).integrity(),.0001);
    }

    @Test void energyDrainsWithExertionAndRecoversWithoutVanillaHpHealing() {
        BodyModel body=new BodyModel();for(int i=0;i<600;i++)body.tick(true,true);
        float tired=body.snapshot().energy();assertTrue(tired<.6f);
        ticks(body,600);assertTrue(body.snapshot().energy()>tired);
        assertEquals(1,body.snapshot().part(TORSO).integrity());
    }

    @Test void consciousnessDependsOnOxygenPainAndSedationNotRunningEnergy() {
        BodyModel rested=new BodyModel(),hypoxic=new BodyModel(),tired=new BodyModel(),sedated=new BodyModel();
        hypoxic.reduceOxygen(.4f);for(int i=0;i<600;i++)tired.tick(true,true);
        sedated.treat(LEFT_ARM,FENTANYL,1);
        assertTrue(hypoxic.snapshot().consciousness()<rested.snapshot().consciousness());
        assertEquals(rested.snapshot().consciousness(),tired.snapshot().consciousness());
        assertTrue(sedated.snapshot().consciousness()<rested.snapshot().consciousness());
        rested.damage(LEFT_ARM,8);assertTrue(rested.snapshot().consciousness()<1);
    }

    @Test void localAnestheticDoesNotHealOrAnesthetizeOtherLimbs() {
        BodyModel body=new BodyModel();body.damage(LEFT_ARM,8);body.damage(RIGHT_ARM,8);
        float tissue=body.snapshot().part(LEFT_ARM).integrity();assertTrue(body.treat(LEFT_ARM,ANESTHETIC,1));
        ticks(body,60);assertEquals(0,body.snapshot().part(LEFT_ARM).pain());
        assertTrue(body.snapshot().part(LEFT_ARM).anesthetized());assertTrue(body.snapshot().part(RIGHT_ARM).pain()>.1f);
        assertEquals(tissue,body.snapshot().part(LEFT_ARM).integrity());assertEquals(0,body.snapshot().drug());
        ticks(body,1200);assertFalse(body.snapshot().part(LEFT_ARM).anesthetized());
        assertTrue(body.snapshot().part(LEFT_ARM).pain()>0);
    }

    @Test void anestheticRestoresLimbForceDuringItsFinalTwoSeconds() {
        BodyModel body=new BodyModel();body.damage(LEFT_ARM,8);body.treat(LEFT_ARM,ANESTHETIC,1);
        ticks(body,1159);assertTrue(body.snapshot().part(LEFT_ARM).anesthetized());
        body.tick(false,false);assertFalse(body.snapshot().part(LEFT_ARM).anesthetized());
        assertEquals(0,body.snapshot().part(LEFT_ARM).pain());
        ticks(body,40);assertTrue(body.snapshot().part(LEFT_ARM).pain()>0);
    }

    @Test void healthySkinDoesNotInventInfection() {
        BodyModel body=new BodyModel();ticks(body,10000);
        for(var part:body.snapshot().parts())assertEquals(0,part.infection());
    }

    @Test void damagedSkinDevelopsInfectionAndAntisepticTreatsItOverTime() {
        BodyModel body=new BodyModel();body.damage(LEFT_ARM,12);body.treat(LEFT_ARM,PRESSURE_DRESSING,1);
        ticks(body,8000);float infection=body.snapshot().part(LEFT_ARM).infection();assertTrue(infection>.25f);
        float tissue=body.snapshot().part(LEFT_ARM).integrity();assertTrue(body.treat(LEFT_ARM,ANTISEPTIC,1));
        assertEquals(infection,body.snapshot().part(LEFT_ARM).infection());
        assertEquals(tissue,body.snapshot().part(LEFT_ARM).integrity());assertTrue(body.snapshot().part(LEFT_ARM).disinfected());
        ticks(body,1000);assertTrue(body.snapshot().part(LEFT_ARM).infection()<infection);
    }

    @Test void fullSavePreservesEnergyInfectionAnesthesiaAndFractionalCare() {
        BodyModel body=new BodyModel();body.damage(LEFT_ARM,12);body.treat(LEFT_ARM,PRESSURE_DRESSING,1);ticks(body,8000);
        body.treat(LEFT_ARM,ANTISEPTIC,.5f);body.treat(LEFT_ARM,ANESTHETIC,.5f);body.treat(LEFT_ARM,BANDAGE,.125f);
        for(int i=0;i<100;i++)body.tick(true,true);
        var saved=body.saveState();var restored=BodyModel.restoreState(saved).saveState();
        assertEquals(saved.snapshot().energy(),restored.snapshot().energy());
        assertEquals(saved.snapshot().consciousness(),restored.snapshot().consciousness());
        assertEquals(saved.snapshot().mode(),restored.snapshot().mode());
        assertArrayEquals(saved.snapshot().parts(),restored.snapshot().parts());
        assertArrayEquals(saved.disinfectionTicks(),restored.disinfectionTicks());
        assertArrayEquals(saved.anestheticTicks(),restored.anestheticTicks());
        assertArrayEquals(saved.bandageSlowAmount(),restored.bandageSlowAmount());
        assertArrayEquals(saved.skinHealAmount(),restored.skinHealAmount());
    }

    @Test void careAndTimerArraysAreDefensiveCopies() {
        BodyModel body=new BodyModel();body.damage(LEFT_ARM,8);body.treat(LEFT_ARM,BANDAGE,1);body.treat(LEFT_ARM,ANESTHETIC,1);
        var saved=body.saveState();float[] care=saved.bandageSlowAmount();care[LEFT_ARM.ordinal()]=0;
        int[] timer=saved.anestheticTicks();timer[LEFT_ARM.ordinal()]=0;
        assertTrue(saved.bandageSlowAmount()[LEFT_ARM.ordinal()]>0);assertTrue(saved.anestheticTicks()[LEFT_ARM.ordinal()]>0);
    }

    @Test void reloadAndBriefStateHoppingCannotRefillDyingGrace() {
        BodyModel body=fragile();ticks(body,120);int before=body.saveState().dyingTicks();
        body=BodyModel.restoreState(body.saveState());assertEquals(before,body.saveState().dyingTicks());
        body.treat(LEFT_ARM,TOURNIQUET,1);assertNotEquals(DYING,body.snapshot().mode());
        body.damage(HEAD,40);assertEquals(DYING,body.snapshot().mode());
        assertEquals(before,body.saveState().dyingTicks());
    }

    @Test void oldOrdinalAndOldLastStandingSavesRemainSafe() {
        assertEquals(3,DEAD.ordinal());
        var parts=new BodyModel().snapshot().parts();parts[HEAD.ordinal()]=new BodySnapshot.PartSnapshot(0,.4f,1,false,false,false,false);
        BodyModel old=BodyModel.restore(new BodySnapshot(.1f,.4f,.5f,.5f,0,100,LAST_STANDING,.5f,parts));
        assertTrue(old.snapshot().part(HEAD).integrity()>=.75f);ticks(old,400);assertNotEquals(DEAD,old.snapshot().mode());
    }

    @Test void snapshotsAndPausesDoNotSpendGameTimers() {
        BodyModel body=fragile();ticks(body,60);int before=body.saveState().dyingTicks();
        for(int i=0;i<1000;i++)body.snapshot();assertEquals(before,body.saveState().dyingTicks());
    }

    @Test void newFieldsRejectNonFiniteNumbersAndClampOnlyFiniteGameUnits() {
        var parts=new BodyModel().snapshot().parts();
        assertThrows(IllegalArgumentException.class,()->new BodySnapshot(1,1,1,0,0,72,CONSCIOUS,1,Float.NaN,1,parts));
        assertThrows(IllegalArgumentException.class,()->new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false,Float.NaN,false,false,0));
        assertThrows(IllegalArgumentException.class,()->new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false,0,false,false,Float.NaN));
    }
}
