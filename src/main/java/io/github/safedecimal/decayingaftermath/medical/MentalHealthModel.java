package io.github.safedecimal.decayingaftermath.medical;

/**
 * Stateful server-side mood and depression tracker (20 Hz ticks).
 * Balances environmental stressors (darkness, eerie records 11/13, cave ambiance, hostile threats,
 * bad food, sleep deprivation) against positive anchors (daylight surface, pleasant music, good food,
 * sleep, warmth, therapy).
 */
public final class MentalHealthModel {
    private float moodReserve = 1.0f; // +1 healthy baseline, -1 deep distress (maps with physical wellbeing)
    private float antidepressant;     // 0..3; therapeutic plateau builds gradually
    private float anxiolytic;         // 0..3; fast acute stress/psychosis dampener
    private float therapyProgress;    // 0..1 accumulated therapeutic response
    private float lightDeficit;       // 0..1 seasonal/circadian deficit from darkness/caves
    private float situationalStress;  // 0..1 acute trauma/mob threat/bad food shock
    private float chronicBurden;      // 0..1 persistent low-mood accumulation (dysthymia)
    private float psychosisLoad;      // 0..1 eerie music/cave/intoxication/hypoxia psychosis driver
    private float positiveFoodGrace;  // decaying timer for recent good food indicator
    private float negativeFoodGrace;  // decaying timer for recent bad food indicator
    private int caveSoundTicks;       // ticks remaining from recent cave ambience sound
    private float previousOpiateOrStim;
    private float withdrawalLoad;     // 0..1 withdrawal discomfort after high drug burden drops
    private int activeFactors;
    private DepressionType depression = DepressionType.NONE;

    public MentalHealthModel() { }

    public MentalHealthModel(float moodReserve, float antidepressant, float anxiolytic,
            float therapyProgress, float lightDeficit, float situationalStress,
            float chronicBurden, float psychosisLoad, DepressionType depression, int activeFactors) {
        this.moodReserve = clampSigned(moodReserve);
        this.antidepressant = clamp(antidepressant, 0, 3);
        this.anxiolytic = clamp(anxiolytic, 0, 3);
        this.therapyProgress = clamp(therapyProgress, 0, 1);
        this.lightDeficit = clamp(lightDeficit, 0, 1);
        this.situationalStress = clamp(situationalStress, 0, 1);
        this.chronicBurden = clamp(chronicBurden, 0, 1);
        this.psychosisLoad = clamp(psychosisLoad, 0, 1);
        this.depression = depression == null ? DepressionType.NONE : depression;
        this.activeFactors = activeFactors;
    }

    public float moodReserve() { return moodReserve; }
    public float antidepressant() { return antidepressant; }
    public float anxiolytic() { return anxiolytic; }
    public float therapyProgress() { return therapyProgress; }
    public float lightDeficit() { return lightDeficit; }
    public float situationalStress() { return situationalStress; }
    public float chronicBurden() { return chronicBurden; }
    public float psychosisLoad() { return psychosisLoad; }
    public DepressionType depression() { return depression; }
    public int activeFactors() { return activeFactors; }
    public float anxiolyticAction() { return Math.min(1, anxiolytic); }

    public void tick(BodyEnvironment env, float pain, float toxin, float nausea, float brainHealth,
            float fatigue, float temperature, float opiateOrStimBurden, boolean mentalEnabled) {
        if (!mentalEnabled) {
            activeFactors = 0;
            depression = DepressionType.NONE;
            moodReserve = 1.0f;
            return;
        }
        if (caveSoundTicks > 0) caveSoundTicks--;
        if (positiveFoodGrace > 0) positiveFoodGrace = Math.max(0, positiveFoodGrace - 0.0005f);
        if (negativeFoodGrace > 0) negativeFoodGrace = Math.max(0, negativeFoodGrace - 0.0005f);

        // Withdrawal builds when a heavy opiate/stimulant burden fades sharply.
        if (previousOpiateOrStim > 0.7f && opiateOrStimBurden < previousOpiateOrStim) {
            withdrawalLoad = clamp(withdrawalLoad + (previousOpiateOrStim - opiateOrStimBurden) * 0.4f, 0, 1);
        }
        previousOpiateOrStim = opiateOrStimBurden;
        if (opiateOrStimBurden > 0.6f) withdrawalLoad = Math.max(0, withdrawalLoad - 0.0003f);
        else withdrawalLoad = Math.max(0, withdrawalLoad - 0.00004f);

        int factors = 0;
        float delta = 0;

        // --- Negative environment & physiology drivers ---
        boolean dark = env.lightLevel() <= 4 && !env.nearCampfire();
        if (dark) {
            factors |= MoodFactors.DARKNESS;
            delta -= 0.000080f;
            lightDeficit = clamp(lightDeficit + 0.000090f, 0, 1);
        }
        boolean cave = env.undergroundCave() || caveSoundTicks > 0;
        if (cave) {
            factors |= MoodFactors.CAVE;
            delta -= (caveSoundTicks > 0 ? 0.000100f : 0.000045f);
            lightDeficit = clamp(lightDeficit + 0.000050f, 0, 1);
            if (caveSoundTicks > 0) psychosisLoad = clamp(psychosisLoad + 0.000080f, 0, 1);
        }
        if (env.hostileMobsNearby() > 0) {
            factors |= MoodFactors.HOSTILES;
            float threat = Math.min(3, env.hostileMobsNearby());
            delta -= 0.000090f * threat;
            situationalStress = clamp(situationalStress + 0.000110f * threat, 0, 1);
        } else {
            situationalStress = Math.max(0, situationalStress - (env.daylightSurface() ? 0.00004f : 0.00002f));
        }

        if (env.jukeboxMood() < -0.1f) {
            factors |= MoodFactors.EERIE_MUSIC;
            delta -= 0.00014f;
            psychosisLoad = clamp(psychosisLoad + 0.00018f, 0, 1);
        } else if (env.jukeboxMood() > 0.1f) {
            factors |= MoodFactors.GOOD_MUSIC;
            delta += 0.00012f * depression.positiveGainFactor();
            psychosisLoad = Math.max(0, psychosisLoad - 0.00005f);
        }

        if (env.sleepDeprivedTicks() > 24000) {
            factors |= MoodFactors.SLEEP_DEPRIVED;
            float sev = Math.min(2.5f, (env.sleepDeprivedTicks() - 24000) / 24000f);
            delta -= 0.000035f * sev;
            chronicBurden = clamp(chronicBurden + 0.000020f * sev, 0, 1);
        }

        boolean coldOrRain = (env.wet() && !env.nearCampfire()) || temperature < 35.2f;
        if (coldOrRain) {
            factors |= MoodFactors.COLD_OR_RAIN;
            delta -= 0.000030f;
            lightDeficit = clamp(lightDeficit + 0.000015f, 0, 1);
        }

        if (pain > 0.35f || nausea > 0.3f || brainHealth < 0.7f) {
            factors |= MoodFactors.PAIN_OR_ILLNESS;
            delta -= (pain * 0.00004f + nausea * 0.00004f + (1 - brainHealth) * 0.00006f);
            if (pain > 0.5f) situationalStress = clamp(situationalStress + 0.00003f, 0, 1);
            if (toxin > 0.6f || brainHealth < 0.55f) psychosisLoad = clamp(psychosisLoad + 0.00004f, 0, 1);
        }

        if (withdrawalLoad > 0.15f) {
            factors |= MoodFactors.WITHDRAWAL;
            delta -= 0.000055f * withdrawalLoad;
        }
        if (negativeFoodGrace > 0.05f) factors |= MoodFactors.BAD_FOOD;
        if (positiveFoodGrace > 0.05f) factors |= MoodFactors.GOOD_FOOD;

        // --- Positive anchors & environment ---
        if (env.daylightSurface()) {
            factors |= MoodFactors.DAYLIGHT;
            delta += 0.000095f * depression.positiveGainFactor();
            lightDeficit = Math.max(0, lightDeficit - 0.00016f);
        } else if (env.lightLevel() >= 11) {
            lightDeficit = Math.max(0, lightDeficit - 0.00004f);
        }

        if (env.sleeping()) {
            factors |= MoodFactors.SLEEPING;
            delta += 0.00030f * Math.max(0.5f, depression.positiveGainFactor());
            situationalStress = Math.max(0, situationalStress - 0.00015f);
            lightDeficit = Math.max(0, lightDeficit - 0.00005f);
        }

        if (env.nearCampfire() && !env.wet()) {
            factors |= MoodFactors.COZY_WARMTH;
            delta += 0.000045f * depression.positiveGainFactor();
            situationalStress = Math.max(0, situationalStress - 0.00004f);
        }

        if (env.naturalGreenery() && env.lightLevel() >= 8) {
            factors |= MoodFactors.NATURE;
            delta += 0.000035f * depression.positiveGainFactor();
        }

        // --- Pharmacology ---
        if (antidepressant > 0.05f) {
            factors |= MoodFactors.THERAPY;
            // SSRI-like delayed response: builds therapyProgress gradually while active
            therapyProgress = clamp(therapyProgress + 0.00018f * Math.min(1, antidepressant), 0, 1);
            delta += 0.00012f * therapyProgress;
            chronicBurden = Math.max(0, chronicBurden - 0.00012f * therapyProgress);
            psychosisLoad = Math.max(0, psychosisLoad - 0.00006f * therapyProgress);
            lightDeficit = Math.max(0, lightDeficit - 0.00004f * therapyProgress);
        } else {
            therapyProgress = Math.max(0, therapyProgress - 0.000025f);
        }

        if (anxiolytic > 0.05f) {
            factors |= MoodFactors.SEDATIVE;
            float potency = Math.min(1, anxiolytic);
            situationalStress = Math.max(0, situationalStress - 0.00035f * potency);
            psychosisLoad = Math.max(0, psychosisLoad - 0.00040f * potency);
            delta += 0.00010f * potency;
        } else if (env.jukeboxMood() >= 0 && caveSoundTicks == 0 && toxin < 0.4f && brainHealth > 0.65f) {
            psychosisLoad = Math.max(0, psychosisLoad - 0.00003f);
        }

        // Gentle homeostatic drift toward neutral-positive when healthy & rested
        if ((factors & MoodFactors.NEGATIVE_MASK) == 0 && pain < 0.1f && nausea < 0.1f && fatigue < 0.4f) {
            delta += (0.45f - moodReserve) * 0.00004f * depression.naturalRecoveryFactor();
        }

        antidepressant = Math.max(0, antidepressant - 0.000025f);
        anxiolytic = Math.max(0, anxiolytic - 0.00014f);

        moodReserve = clampSigned(moodReserve + delta);

        // Chronic dysthymic burden accumulates if mood stays moderately negative without treatment
        if (moodReserve < -0.18f && antidepressant < 0.1f) {
            chronicBurden = clamp(chronicBurden + 0.000012f, 0, 1);
        } else if (moodReserve > 0.05f || antidepressant > 0.1f) {
            chronicBurden = Math.max(0, chronicBurden - 0.000025f);
        }

        // Persistent dysthymia caps spontaneous cheerfulness until resolved
        if (depression == DepressionType.PERSISTENT && moodReserve > 0.15f && therapyProgress < 0.35f) {
            moodReserve = Math.min(moodReserve, 0.15f);
        }

        activeFactors = factors;
        depression = classify(brainHealth);
    }

    public void onFoodConsumed(float moodDelta, float stressDelta, float psychosisDelta) {
        if (!Float.isFinite(moodDelta)) return;
        if (moodDelta > 0) {
            moodReserve = clampSigned(moodReserve + moodDelta * depression.positiveGainFactor());
            positiveFoodGrace = 1.0f;
            situationalStress = Math.max(0, situationalStress - moodDelta * 0.35f);
        } else if (moodDelta < 0) {
            moodReserve = clampSigned(moodReserve + moodDelta);
            negativeFoodGrace = 1.0f;
        }
        if (Float.isFinite(stressDelta) && stressDelta > 0) situationalStress = clamp(situationalStress + stressDelta, 0, 1);
        if (Float.isFinite(psychosisDelta) && psychosisDelta > 0) psychosisLoad = clamp(psychosisLoad + psychosisDelta, 0, 1);
    }

    public void onTrauma(float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return;
        float shock = Math.min(0.35f, amount * 0.025f);
        moodReserve = clampSigned(moodReserve - shock);
        situationalStress = clamp(situationalStress + shock * 1.4f, 0, 1);
    }

    public void onCaveSound() {
        caveSoundTicks = Math.max(caveSoundTicks, 200);
        moodReserve = clampSigned(moodReserve - 0.035f);
        psychosisLoad = clamp(psychosisLoad + 0.04f, 0, 1);
    }

    public void onNightSleep() {
        moodReserve = clampSigned(moodReserve + 0.22f * Math.max(0.5f, depression.positiveGainFactor()));
        lightDeficit = Math.max(0, lightDeficit - 0.25f);
        situationalStress = Math.max(0, situationalStress - 0.35f);
        chronicBurden = Math.max(0, chronicBurden - 0.12f);
        psychosisLoad = Math.max(0, psychosisLoad - 0.15f);
    }

    public void onCompanionshipOrPet() {
        moodReserve = clampSigned(moodReserve + 0.06f * depression.positiveGainFactor());
        situationalStress = Math.max(0, situationalStress - 0.08f);
    }

    public void treat(TreatmentKind kind, float amount) {
        if (kind == null || !Float.isFinite(amount) || amount <= 0) return;
        float dose = clamp(amount, 0, 1);
        switch (kind) {
            case ANTIDEPRESSANT -> antidepressant = clamp(antidepressant + dose * 0.9f, 0, 3);
            case ANXIOLYTIC -> anxiolytic = clamp(anxiolytic + dose * 0.9f, 0, 3);
            case HERBAL_TEA -> {
                moodReserve = clampSigned(moodReserve + dose * 0.14f * Math.max(0.6f, depression.positiveGainFactor()));
                situationalStress = Math.max(0, situationalStress - dose * 0.18f);
                lightDeficit = Math.max(0, lightDeficit - dose * 0.08f);
            }
            default -> { }
        }
    }

    private DepressionType classify(float brainHealth) {
        if (moodReserve > -0.15f && chronicBurden < 0.45f && lightDeficit < 0.55f
                && situationalStress < 0.55f && psychosisLoad < 0.55f) {
            return DepressionType.NONE;
        }
        if ((moodReserve <= -0.70f && psychosisLoad >= 0.40f) || (psychosisLoad >= 0.65f && moodReserve < -0.35f)) {
            return DepressionType.PSYCHOTIC;
        }
        if (lightDeficit >= 0.45f && lightDeficit >= situationalStress && lightDeficit >= chronicBurden && moodReserve <= -0.18f) {
            if (moodReserve <= -0.75f && chronicBurden >= 0.65f) return DepressionType.MELANCHOLIC;
            return DepressionType.SEASONAL;
        }
        if (situationalStress >= 0.40f && situationalStress >= chronicBurden && moodReserve <= -0.18f) {
            if (moodReserve <= -0.75f && chronicBurden >= 0.65f) return DepressionType.MELANCHOLIC;
            return DepressionType.SITUATIONAL;
        }
        if (moodReserve <= -0.50f && (chronicBurden >= 0.45f || brainHealth < 0.55f || (therapyProgress < 0.2f && moodReserve <= -0.60f))) {
            return DepressionType.MELANCHOLIC;
        }
        if (chronicBurden >= 0.40f || moodReserve <= -0.22f) {
            return DepressionType.PERSISTENT;
        }
        return DepressionType.NONE;
    }

    private static float clampSigned(float v) { return Float.isFinite(v) ? Math.clamp(v, -1, 1) : 0; }
    private static float clamp(float v, float min, float max) { return Float.isFinite(v) ? Math.clamp(v, min, max) : min; }
}
