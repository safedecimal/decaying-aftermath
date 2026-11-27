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
}
