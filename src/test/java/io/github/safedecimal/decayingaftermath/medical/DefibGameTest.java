package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static io.github.safedecimal.decayingaftermath.medical.DefibGame.Result.MISS;
import static io.github.safedecimal.decayingaftermath.medical.DefibGame.Result.PROGRESS;
import static io.github.safedecimal.decayingaftermath.medical.DefibGame.*;
import static org.junit.jupiter.api.Assertions.*;

final class DefibGameTest {
    private static void place(DefibGame g,int pad,float x,float y,long now) {
        assertEquals(PROGRESS,g.input(pad,g.padX(pad),g.padY(pad),now));
        assertEquals(PROGRESS,g.input(MOVE,x,y,now+1));
        assertEquals(PROGRESS,g.input(RELEASE,x,y,now+2));
    }
    private static DefibGame aed() {
        var g=new DefibGame(true,0,100);
        place(g,0,-296,153,100);place(g,1,273,-141,103);return g;
    }
    private static void dial(DefibGame g,float amount,long now) {
        assertEquals(PROGRESS,g.input(DIAL,-225,-174,now));
        double angle=Math.toRadians(angleForCharge(amount)+90);
        float x=-225+(float)Math.cos(angle)*75,y=-174+(float)Math.sin(angle)*75;
        assertEquals(PROGRESS,g.input(MOVE,x,y,now));assertEquals(PROGRESS,g.input(RELEASE,x,y,now));
        assertEquals(amount,g.desiredCharge(),.001f);
    }
    @Test void oldThreeBeatConfirmationGameIsGone() {
        var g=new DefibGame(false,0,100);
        for(long now:new long[]{121,153,185})assertEquals(MISS,g.input(CONFIRM,now));
        assertEquals(0,g.shocks());assertEquals(0,g.currentCharge());
    }
    @Test void padSpawnUsesOriginalInsideUnitCircleRadius() {
        for(int seed=0;seed<100;seed++) {
            var g=new DefibGame(true,seed,100);
            for(int pad=0;pad<2;pad++)assertTrue(Math.hypot(g.padX(pad),g.padY(pad))<=150);
            assertEquals(0,g.pads());
        }
    }
    @Test void padsCanBeDraggedInEitherOrderButNeedBothCorrectRegions() {
        var g=new DefibGame(true,3,100);
        place(g,1,273,-141,100);assertEquals(Stage.PLACING,g.stage());
        place(g,0,-296,153,103);assertEquals(Stage.ANALYZING,g.stage());
        assertEquals(2,g.pads());assertEquals(105,g.stageAt());
    }
    @Test void originalStrictPadToleranceAndReleaseAreRequired() {
        var g=new DefibGame(true,0,100);place(g,0,-146,153,100);place(g,1,273,-141,103);
        assertEquals(Stage.PLACING,g.stage());assertEquals(1,g.pads());
        g.input(0,g.padX(0),g.padY(0),106);g.input(MOVE,-147,153,107);
        assertEquals(Stage.PLACING,g.stage());g.input(RELEASE,-147,153,108);
        assertEquals(Stage.ANALYZING,g.stage());
    }
    @Test void arbitraryGrabAndMoveCannotCompletePadPlacement() {
        var g=new DefibGame(true,0,100);
        assertEquals(MISS,g.input(0,-524,524,100));
        assertEquals(MISS,g.input(MOVE,-296,153,101));
        assertEquals(MISS,g.input(RELEASE,-296,153,102));assertEquals(0,g.pads());
    }
    @Test void analysisUsesCuFourPointTwoFiveSecondsAndDoesNotAutoFire() {
        var g=aed();g.tick(190,true);assertEquals(Stage.ANALYZING,g.stage());
        g.tick(191,true);assertEquals(Stage.READY,g.stage());
        g.tick(10000,true);assertEquals(0,g.shocks());assertFalse(g.autoReady(10000));
    }
    @Test void aedNeedsVisibleButtonThenCuThreePointFiveSeconds() {
        var g=aed();g.tick(191,true);
        assertEquals(MISS,g.input(CONFIRM,0,0,192));assertEquals(PROGRESS,g.input(CONFIRM,0,-421,192));
        assertEquals(MISS,g.input(CONFIRM,0,-421,193));
        assertEquals(PROGRESS,g.tick(262,true));assertEquals(DefibGame.Result.SHOCK,g.tick(263,true));
        assertEquals(Stage.COMPLETE,g.stage());assertEquals(1,g.shocks());
        assertEquals(PROGRESS,g.tick(1000,true));assertEquals(1,g.shocks());
        assertEquals(.83f,g.battery(),.00001f);
    }
    @Test void noShockAdvisoryButtonReanalyzesButCannotOverrideInterlock() {
        var g=aed();g.tick(191,false);assertEquals(Stage.NO_SHOCK,g.stage());
        assertEquals(PROGRESS,g.input(CONFIRM,0,-421,192));assertEquals(Stage.ANALYZING,g.stage());
        assertEquals(PROGRESS,g.tick(263,false));assertEquals(0,g.shocks());
        g.tick(278,false);assertEquals(Stage.NO_SHOCK,g.stage());
    }
    @Test void aedRechecksRhythmAtDeliveryAndDoesNotWasteShockEnergyOnStableRhythm() {
        var g=aed();g.tick(191,true);g.input(CONFIRM,0,-421,192);
        float before=g.battery();
        assertEquals(PROGRESS,g.tick(263,false));assertEquals(Stage.NO_SHOCK,g.stage());
        assertEquals(0,g.shocks());assertEquals(before,g.battery());
    }
    @Test void cuAedCanChargeAgainWithoutInventedRhythmLocks() {
        var g=aed();g.tick(191,true);g.input(CONFIRM,0,-421,192);g.tick(263,true);
        assertEquals(PROGRESS,g.input(CONFIRM,0,-421,264));
        assertEquals(DefibGame.Result.SHOCK,g.tick(335,true));assertEquals(2,g.shocks());
    }
    @Test void dialMapsTheExactCuClampAndRemap() {
        assertEquals(200,chargeForAngle(-170));assertEquals(10,chargeForAngle(170));
        assertEquals(200,chargeForAngle(-180));assertEquals(10,chargeForAngle(180));
        for(float value:new float[]{10,25,50,100,150,200})assertEquals(value,chargeForAngle(angleForCharge(value)),.001f);
    }
    @Test void manualChargesFortyGameUnitsPerSecondAndOnlyShockButtonFires() {
        var g=new DefibGame(false,0,100);dial(g,100,100);g.input(CHARGE,8,-174,100);
        g.tick(120,true);assertEquals(40,g.currentCharge(),.001f);assertEquals(0,g.shocks());
        g.tick(150,true);assertEquals(100,g.currentCharge(),.001f);assertEquals(Stage.READY,g.stage());
        assertEquals(MISS,g.input(SHOCK,0,0,150));assertEquals(DefibGame.Result.SHOCK,g.input(SHOCK,234.3f,-174,150));
        assertEquals(0,g.currentCharge());assertEquals(1,g.shocks());assertEquals(100,g.lastShockCharge(),.001f);
    }
    @Test void chargeCanBePausedAndResumedWithoutLosingStoredEnergy() {
        var g=new DefibGame(false,0,100);dial(g,100,100);g.input(CHARGE,8,-174,100);
        g.tick(110,true);g.input(CHARGE,8,-174,110);g.tick(160,true);
        assertEquals(20,g.currentCharge(),.001f);assertEquals(Stage.READY,g.stage());
        g.input(CHARGE,8,-174,160);g.tick(170,true);assertEquals(40,g.currentCharge(),.001f);
    }
    @Test void insufficientManualChargeCannotFireOrIncrementCounter() {
        var g=new DefibGame(false,0,100);dial(g,100,100);g.input(CHARGE,8,-174,100);
        g.tick(104,true);assertEquals(8,g.currentCharge(),.001f);
        assertEquals(MISS,g.input(SHOCK,234.3f,-174,104));assertEquals(0,g.shocks());
        g.tick(105,true);assertEquals(DefibGame.Result.SHOCK,g.input(SHOCK,234.3f,-174,105));
        assertEquals(MISS,g.input(SHOCK,234.3f,-174,105));
    }
    @Test void manualShockChanceUsesCuEnergyComparisonNotTapTiming() {
        var g=new DefibGame(false,0,100);dial(g,100,100);g.input(CHARGE,8,-174,100);g.tick(150,true);
        g.input(SHOCK,234.3f,-174,150);
        assertEquals(1,g.chanceToWork(50),.00001f);assertEquals(.5f,g.chanceToWork(30),.001f);assertEquals(0,g.chanceToWork(0));
    }
    @Test void loweringDialDoesNotDiscardAlreadyStoredCharge() {
        var g=new DefibGame(false,0,100);dial(g,100,100);g.input(CHARGE,8,-174,100);g.tick(150,true);
        dial(g,10,150);g.input(CHARGE,8,-174,150);g.tick(151,true);
        assertEquals(100,g.currentCharge(),.001f);assertEquals(Stage.READY,g.stage());
    }
    @Test void secondManualShockRequiresChargingAgainAndKeepsScreenState() {
        var g=new DefibGame(false,0,100);dial(g,100,100);g.input(CHARGE,8,-174,100);g.tick(150,true);
        g.input(SHOCK,234.3f,-174,150);dial(g,200,151);g.input(CHARGE,8,-174,151);g.tick(251,true);
        assertEquals(DefibGame.Result.SHOCK,g.input(SHOCK,234.3f,-174,251));assertEquals(2,g.shocks());
        assertEquals(Stage.READY,g.stage());assertEquals(0,g.currentCharge());
    }
    @Test void batteryHasNativeIdleDrainAndCannotGoNegative() {
        var g=new DefibGame(false,0,100);g.tick(120,true);assertEquals(1-1/800f,g.battery(),.00001f);
        var empty=new DefibGame(false,0,100,.0001f);empty.tick(200,true);assertEquals(0,empty.battery());
        assertEquals(MISS,empty.input(CHARGE,8,-174,200));
        assertEquals(0,sanitizeBattery(Float.NaN));assertEquals(1,sanitizeBattery(2));
    }
    @Test void nonFiniteOutOfRangeAndBackdatedPointersAreRejected() {
        var g=new DefibGame(false,0,100);
        assertEquals(MISS,g.input(DIAL,Float.NaN,-174,100));
        assertEquals(MISS,g.input(DIAL,-225,Float.POSITIVE_INFINITY,100));
        assertEquals(MISS,g.input(DIAL,-525,-174,100));assertEquals(MISS,g.input(DIAL,-225,-174,99));
        assertEquals(MISS,g.tick(99,true));assertEquals(100,g.desiredCharge());
    }
}
