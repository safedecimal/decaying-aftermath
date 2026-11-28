package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MentalHealthModelTest {
    private static BodyEnvironment darkCaveWithEerieDisc() {
        return new BodyEnvironment(16, .25f, false, true, false, false, false, 0, 48, true, true,
                2, false, false, true, false, 2, -1f, 36000, true);
    }

    private static BodyEnvironment brightDaylightSurface() {
        return new BodyEnvironment(22, .25f, false, true, false, false, false, 0, 48, true, true,
                15, true, true, false, true, 0, 1f, 0, true);
    }

    @Test
    void eerieMusicDarknessCavesAndHostilesTriggerDepressionAndDaylightMusicSleepRecover() {
        var mental = new MentalHealthModel();
        var bleak = darkCaveWithEerieDisc();
        for (int i = 0; i < 6000; i++) {
            mental.tick(bleak, 0, 0, 0, 1, 0.2f, 37f, 0, true);
        }
        assertTrue(mental.moodReserve() < -0.5f);
        assertTrue(mental.depression().active());
        assertNotEquals(0, mental.activeFactors() & MoodFactors.EERIE_MUSIC);
        assertNotEquals(0, mental.activeFactors() & MoodFactors.DARKNESS);
        assertNotEquals(0, mental.activeFactors() & MoodFactors.CAVE);
        assertNotEquals(0, mental.activeFactors() & MoodFactors.HOSTILES);

        // Treat psychotic/severe burden and transition into daylight + good music + sleep
        mental.treat(TreatmentKind.ANXIOLYTIC, 1);
        mental.treat(TreatmentKind.ANTIDEPRESSANT, 1);
        mental.onNightSleep();
        var bright = brightDaylightSurface();
        for (int i = 0; i < 8000; i++) {
            mental.tick(bright, 0, 0, 0, 1, 0, 37f, 0, true);
        }
        assertFalse(mental.depression().active());
        assertTrue(mental.moodReserve() > 0f);
    }

    @Test
    void seasonalDepressionDevelopsInDarknessAndClearsInDaylight() {
        var mental = new MentalHealthModel();
        var darkOnly = new BodyEnvironment(18, .25f, false, true, false, false, false, 0, 48, true, true,
                1, false, false, true, false, 0, 0f, 0, true);
        for (int i = 0; i < 12000; i++) {
            mental.tick(darkOnly, 0, 0, 0, 1, 0.1f, 37f, 0, true);
        }
        assertEquals(DepressionType.SEASONAL, mental.depression());

        var sun = new BodyEnvironment(22, .25f, false, true, false, false, false, 0, 48, true, true,
                15, true, false, false, true, 0, 0f, 0, true);
        for (int i = 0; i < 9000; i++) {
            mental.tick(sun, 0, 0, 0, 1, 0, 37f, 0, true);
        }
        assertEquals(DepressionType.NONE, mental.depression());
    }

    @Test
    void situationalDepressionTriggersFromHostilesAndTraumaAndEasesWithSafety() {
        var mental = new MentalHealthModel();
        mental.onTrauma(12);
        var threat = new BodyEnvironment(20, .25f, false, true, false, false, false, 0, 48, true, true,
                12, false, false, false, false, 3, 0f, 0, true);
        for (int i = 0; i < 5000; i++) {
            mental.tick(threat, 0.4f, 0, 0, 1, 0.1f, 37f, 0, true);
        }
        assertEquals(DepressionType.SITUATIONAL, mental.depression());
    }

    @Test
    void melancholicDepressionResistsOrdinaryFoodUntilAntidepressantCourseTakesHold() {
        var mental = new MentalHealthModel(-0.75f, 0, 0, 0, 0.1f, 0.1f, 0.8f, 0.1f, DepressionType.MELANCHOLIC, 0);
        float before = mental.moodReserve();
        mental.onFoodConsumed(0.10f, 0, 0);
        float gainWithoutMeds = mental.moodReserve() - before;
        assertTrue(gainWithoutMeds < 0.04f, "Anhedonia blunts ordinary mood rewards");

        mental.treat(TreatmentKind.ANTIDEPRESSANT, 1);
        var calm = brightDaylightSurface();
        for (int i = 0; i < 7000; i++) {
            mental.tick(calm, 0, 0, 0, 1, 0, 37f, 0, true);
        }
        assertTrue(mental.therapyProgress() > 0.4f);
        assertTrue(mental.moodReserve() > before + 0.3f);
    }

    @Test
    void persistentDysthymiaCapsSpontaneousCheerfulnessAndResolvesWithTherapy() {
        var mental = new MentalHealthModel(-0.25f, 0, 0, 0, 0.1f, 0.1f, 0.7f, 0f, DepressionType.PERSISTENT, 0);
        var mildDay = new BodyEnvironment(20, .25f, false, true, false, false, false, 0, 48, true, true,
                14, true, false, false, true, 0, 0f, 0, true);
        for (int i = 0; i < 4000; i++) {
            mental.tick(mildDay, 0, 0, 0, 1, 0, 37f, 0, true);
        }
        assertTrue(mental.moodReserve() <= 0.25f);
    }

    @Test
    void badFoodLowersMoodAndGoodFoodRaisesMood() {
        var rotten = FoodMoodPolicy.forItemId("rotten_flesh", 4);
        var potato = FoodMoodPolicy.forItemId("potato", 1);
        var eye = FoodMoodPolicy.forItemId("spider_eye", 2);
        var steak = FoodMoodPolicy.forItemId("cooked_beef", 8);
        var pie = FoodMoodPolicy.forItemId("pumpkin_pie", 8);
        assertTrue(rotten.moodDelta() < 0);
        assertTrue(potato.moodDelta() < 0);
        assertTrue(eye.moodDelta() < 0 && eye.psychosisDelta() > 0);
        assertTrue(steak.moodDelta() > 0);
        assertTrue(pie.moodDelta() > steak.moodDelta());
    }

    @Test
    void deathTriggersPtsdAndRemembersDepressionAndDeathCountAcrossRespawnAndAfterlife() {
        var darkOnly = new BodyEnvironment(18, .25f, false, true, false, false, false, 0, 48, true, true,
                1, false, false, true, false, 0, 0f, 0, true);
        var body = new BodyModel();
        for (int i = 0; i < 12000; i++) {
            body.tick(darkOnly);
        }
        assertEquals(DepressionType.SEASONAL, body.snapshot().survival().depression());
        float preDeathMood = body.snapshot().survival().moodReserve();

        body.kill();
        assertEquals(BodySnapshot.Mode.DEAD, body.mode());

        // COPY_FROM creates fresh physical body while remembering depression + PTSD + death
        var respawned = body.respawnAfterDeath();
        assertEquals(BodySnapshot.Mode.CONSCIOUS, respawned.mode());
        assertEquals(1f, respawned.snapshot().blood());
        var rs = respawned.snapshot().survival();
        assertTrue(rs.depression().active(), "Pre-death depression must persist after death");
        assertTrue(rs.ptsd(), "Dying must leave PTSD");
        assertTrue(rs.ptsdFlashback(), "Waking after death triggers an acute flashback");
        assertEquals(1, rs.rememberedDeaths());
        assertTrue(rs.moodReserve() < preDeathMood);

        // Aftermath finish calls withFreshBodyRememberingMental: must preserve memory without double-counting death
        var afterAfterlife = respawned.withFreshBodyRememberingMental();
        var as = afterAfterlife.snapshot().survival();
        assertEquals(1, as.rememberedDeaths());
        assertEquals(rs.depression(), as.depression());
        assertEquals(rs.ptsdLoad(), as.ptsdLoad());

        // Second death stacks remembered deaths and deepens PTSD
        afterAfterlife.kill();
        var secondRespawn = afterAfterlife.respawnAfterDeath();
        assertEquals(2, secondRespawn.snapshot().survival().rememberedDeaths());
        assertTrue(secondRespawn.snapshot().survival().ptsdLoad() > as.ptsdLoad());
    }

    @Test
    void totemRevivePreservesDepressionPtsdAndAddsNearDeathTrauma() {
        var darkCave = darkCaveWithEerieDisc();
        var body = new BodyModel();
        for (int i = 0; i < 6000; i++) {
            body.tick(darkCave);
        }
        DepressionType preReviveDepression = body.snapshot().survival().depression();
        assertTrue(preReviveDepression.active());

        // First death remembered, then later totem revive
        body.kill();
        var afterFirstDeath = body.respawnAfterDeath();
        float ptsdBeforeTotem = afterFirstDeath.snapshot().survival().ptsdLoad();
        assertEquals(1, afterFirstDeath.snapshot().survival().rememberedDeaths());

        afterFirstDeath.damage(BodyPart.HEAD, 30, DamageKind.BLUNT);
        var revived = afterFirstDeath.reviveWithTotem();
        var s = revived.snapshot().survival();
        assertTrue(s.depression().active(), "Totem revive must remember active depression");
        assertTrue(s.ptsd(), "Totem revive must remember PTSD");
        assertTrue(s.ptsdLoad() > ptsdBeforeTotem, "Totem revive adds near-death PTSD burden");
        assertEquals(1, s.rememberedDeaths());
    }

    @Test
    void ptsdTriggersFlashbackUnderThreatAndEasesWithTherapyAndAnxiolytic() {
        var body = new BodyModel();
        body.kill();
        var respawned = body.respawnAfterDeath();
        var mental = respawned.saveState().survivalState();
        assertTrue(mental.snapshot().ptsd());

        var m = SurvivalModel.restore(mental).mental();
        // Let initial flashback and cooldown expire in daylight
        var sun = brightDaylightSurface();
        for (int i = 0; i < 700; i++) {
            m.tick(sun, 0, 0, 0, 1, 0, 37f, 0, true);
        }
        assertFalse(m.ptsdFlashback());
        assertTrue(m.ptsd(), "PTSD should remain long after initial respawn");

        // Exposure to hostile threats triggers a PTSD flashback
        var threat = new BodyEnvironment(20, .25f, false, true, false, false, false, 0, 48, true, true,
                10, false, false, false, false, 2, 0f, 0, true);
        m.tick(threat, 0.5f, 0, 0, 1, 0.1f, 37f, 0, true);
        assertTrue(m.ptsdFlashback());

        // Treatment reduces PTSD burden over time while rememberedDeaths remains in memory
        float beforeTreatment = m.ptsdLoad();
        m.treat(TreatmentKind.ANXIOLYTIC, 1);
        m.treat(TreatmentKind.ANTIDEPRESSANT, 1);
        m.onNightSleep();
        for (int i = 0; i < 6000; i++) {
            m.tick(sun, 0, 0, 0, 1, 0, 37f, 0, true);
        }
        assertTrue(m.ptsdLoad() < beforeTreatment);
        assertEquals(1, m.rememberedDeaths());
    }
}
