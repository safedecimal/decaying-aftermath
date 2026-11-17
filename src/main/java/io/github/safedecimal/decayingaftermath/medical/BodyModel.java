package io.github.safedecimal.decayingaftermath.medical;

import java.util.Arrays;
import java.util.Objects;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.Mode;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot.PartSnapshot;

/**
 * Deterministic server-side game anatomy: one invocation of tick represents one 20 Hz game tick.
 * This has no dependency on Minecraft HP, food, wall-clock time or rendering FPS.
 * All treatment amounts are fractions of one fictional game procedure, never real drug doses.
 */
public final class BodyModel {
    // CU TryLastStand restores reserves; 300 seconds is adrenaline, NOT a death timer.
    public static final int LAST_STANDING_TICKS = 20 * 12;
    public static final int LAST_STANDING_BOOST_TICKS = 20 * 300;
    public static final int DYING_TICKS = 20 * 60;
    public static final int WAKE_UP_TICKS = 20 * 10;
    private static final int PARTS = BodyPart.values().length;
    private final float[] integrity = new float[PARTS];
    private final float[] bleeding = new float[PARTS];
    private final float[] localPain = new float[PARTS];
    private final float[] infection = new float[PARTS];
    private final float[] skinHealAmount = new float[PARTS];
    private final float[] bandageSlowAmount = new float[PARTS];
    private final boolean[] fractured = new boolean[PARTS];
    private final boolean[] bandaged = new boolean[PARTS];
    private final boolean[] splinted = new boolean[PARTS];
    private final boolean[] tourniquet = new boolean[PARTS];
    private final int[] careTicks = new int[PARTS];
    private final int[] regenerationTicks = new int[PARTS];
    private final int[] tourniquetTicks = new int[PARTS];
    private final int[] disinfectionTicks = new int[PARTS];
    private final int[] anestheticTicks = new int[PARTS];
    private final int[] infectionAgeTicks = new int[PARTS];
    private float blood = 1, oxygen = 1, energy = 1, drug, bpm = 72;
    private Mode mode = Mode.CONSCIOUS;
    private int lastStandingTicks, wakeTicks, stableTicks;
    private int lastStandingBoostTicks, dyingTicks = -1;
    private boolean lastStandingSpent;

    public BodyModel() { reset(); }

    public void reset() {
        Arrays.fill(integrity, 1); Arrays.fill(bleeding, 0); Arrays.fill(localPain, 0);
        Arrays.fill(fractured, false); Arrays.fill(bandaged, false); Arrays.fill(splinted, false);
        Arrays.fill(tourniquet, false); Arrays.fill(careTicks, 0); Arrays.fill(regenerationTicks, 0);
        Arrays.fill(tourniquetTicks, 0);
        Arrays.fill(infection, 0); Arrays.fill(skinHealAmount, 0); Arrays.fill(bandageSlowAmount, 0);
        Arrays.fill(disinfectionTicks, 0); Arrays.fill(anestheticTicks, 0); Arrays.fill(infectionAgeTicks, 0);
        blood = oxygen = energy = 1; drug = 0; bpm = 72; mode = Mode.CONSCIOUS;
        lastStandingTicks = wakeTicks = stableTicks = 0; lastStandingSpent = false;
        lastStandingBoostTicks = 0; dyingTicks = -1;
    }

    /** Input is already mitigated vanilla damage units, not normalized tissue damage. */
    public void damage(BodyPart part, float amount) {
        Objects.requireNonNull(part, "part");
        if (mode == Mode.DEAD || !Float.isFinite(amount) || amount <= 0) return;
        amount = Math.min(amount, 1000);
        int i = part.ordinal();
        integrity[i] = unit(integrity[i] - amount * .03f);
        localPain[i] = unit(localPain[i] + amount * .05f);
        bleeding[i] = unit(bleeding[i] + amount * .012f);
        // A new hit can disturb a dressing but does not silently remove an applied tourniquet.
        if (tourniquet[i]) bleeding[i] = 0;
        else if (bandaged[i]) bleeding[i] *= .7f;
        if (part.isLimb() && (amount >= 8 || amount >= 4 && integrity[i] <= .45f)) fractured[i] = true;
        evaluate(false);
    }

    public void fall(float amount) {
        if (!Float.isFinite(amount) || amount <= 0 || mode == Mode.DEAD) return;
        damage(BodyPart.LEFT_LEG, amount * .5f);
        damage(BodyPart.RIGHT_LEG, amount * .5f);
        if (amount > 14) damage(BodyPart.TORSO, (amount - 14) * .2f);
    }

    /** Returns whether this procedure was valid. Invalid anatomy does not consume a medical item. */
    public boolean treat(BodyPart part, TreatmentKind treatment, float amount) {
        Objects.requireNonNull(part, "part"); Objects.requireNonNull(treatment, "treatment");
        if (mode == Mode.DEAD || !Float.isFinite(amount) || amount <= 0) return false;
        if (treatment.requiresLimb() && !part.isLimb()) return false;
        int i = part.ordinal();
        amount = treatment == TreatmentKind.BANDAGE || treatment == TreatmentKind.PRESSURE_DRESSING
                ? Math.clamp(amount, 0, 12) : unit(amount);
        switch (treatment) {
            case POTION -> { return false; } // Non-medicinal status effects belong to the server adapter.
            case BANDAGE -> {
                if (bleeding[i] <= 0 && integrity[i] >= 1) return false;
                // Item.bandage: per full turn +2.5 skin-care points, +3.75 bleed-care
                // points, -5 pain. Limb.Update spends that buffer at 1.25 points/s.
                // One turn never instantly blocks bleeding; winding must continue.
                skinHealAmount[i] = Math.min(10, skinHealAmount[i] + .025f * amount);
                bandageSlowAmount[i] = Math.min(10, bandageSlowAmount[i] + .0375f * amount);
                localPain[i] = Math.max(0, localPain[i] - .05f * amount);
                bandaged[i] = true;
                careTicks[i] = Math.min(12000, careTicks[i] + Math.round(amount * 100));
            }
            case PRESSURE_DRESSING -> {
                if (bleeding[i] <= 0 && integrity[i] >= 1) return false;
                bleeding[i] = Math.max(0, bleeding[i] - .7f * amount);
                bandaged[i] = true;
                careTicks[i] = Math.min(2400, careTicks[i] + Math.round(amount * 2400));
            }
            case SPLINT -> {
                if (!fractured[i]) return false;
                splinted[i] = true;
                localPain[i] = Math.max(0, localPain[i] - .25f * amount);
            }
            case TOURNIQUET -> {
                if (bleeding[i] <= 0) return false;
                bleeding[i] = Math.max(0, bleeding[i] - amount);
                if (amount >= .5f) { tourniquet[i] = true; bleeding[i] = 0; }
            }
            case FENTANYL -> drug = Math.clamp(drug + amount * .65f, 0, 3);
            case ANESTHETIC -> {
                if (integrity[i] >= 1 && localPain[i] <= 0) return false;
                // CU localanesthetic: selected limb only, timed analgesia, no opiate
                // burden and no tissue/infection healing. Fractions are game supply.
                anestheticTicks[i] = Math.min(12000, anestheticTicks[i] + Math.round(amount * 1200));
            }
            case ANTISEPTIC -> {
                if (integrity[i] >= 1 && infection[i] <= 0) return false;
                disinfectionTicks[i] = Math.min(12000, disinfectionTicks[i] + Math.round(amount * 6000));
            }
            case HEALING_POTION -> {
                if (integrity[i] >= 1 && localPain[i] <= 0) return false;
                integrity[i] = unit(integrity[i] + .32f * amount);
                localPain[i] = Math.max(0, localPain[i] - .2f * amount);
            }
            case REGENERATION_POTION -> {
                if (integrity[i] >= 1) return false;
                regenerationTicks[i] = Math.min(1200, regenerationTicks[i] + Math.round(amount * 1200));
            }
        }
        evaluate(false);
        return true;
    }

    /** Every elapsed game tick is explicit; no care is advanced by requesting a snapshot. */
    public void tick(boolean sprinting, boolean moving) {
        if (mode == Mode.DEAD) { bpm = 0; return; }
        if (lastStandingBoostTicks > 0) lastStandingBoostTicks--;
        float totalBleeding = 0;
        boolean receivingCare = false;
        for (int i = 0; i < PARTS; i++) {
            if (anestheticTicks[i] > 0) anestheticTicks[i]--;
            if (disinfectionTicks[i] > 0) {
                disinfectionTicks[i]--;
                infection[i] = unit(infection[i] - .00010f);
            } else if (integrity[i] < .8f) {
                // CU checks wounded skin every40s; default infection growth is7.2/min.
                if (infectionAgeTicks[i] < 800) infectionAgeTicks[i]++;
                else infection[i] = unit(infection[i] + .00006f);
            } else {
                infection[i] = unit(infection[i] - .000025f);
                infectionAgeTicks[i] = 0;
            }
            if (infection[i] > .75f) {
                integrity[i] = unit(integrity[i] - .00010f);
                localPain[i] = unit(localPain[i] + .00006f);
            }
            if (tourniquet[i]) {
                bleeding[i] = 0;
                if (++tourniquetTicks[i] > 1200) {
                    integrity[i] = unit(integrity[i] - .00016f);
                    localPain[i] = unit(localPain[i] + .00018f);
                }
            }
            if (bandageSlowAmount[i] > 0) {
                float used = Math.min(bandageSlowAmount[i], .000625f);
                bleeding[i] = Math.max(0, bleeding[i] - used);
                bandageSlowAmount[i] = Math.max(0, bandageSlowAmount[i] - used);
                receivingCare = true;
            }
            // CU's normal-viscosity/no-venom clotting baseline: .025 points/s.
            // Neither viscosity nor venom is inferred from the Minecraft blood reserve.
            bleeding[i] = Math.max(0, bleeding[i] - .0000125f);
            totalBleeding += bleeding[i];
            boolean nativeSkinCare = skinHealAmount[i] > 0;
            if (nativeSkinCare) {
                float used = Math.min(skinHealAmount[i], .00025f);
                if (oxygen > .2f && blood > .04f) integrity[i] = unit(integrity[i] + used);
                skinHealAmount[i] = Math.max(0, skinHealAmount[i] - used);
                receivingCare = true;
            }
            if (careTicks[i] > 0) {
                receivingCare = true;
                careTicks[i]--;
                if (!nativeSkinCare && bleeding[i] <= .001f && oxygen > .4f && blood > .3f)
                    integrity[i] = unit(integrity[i] + .00004f);
            }
            if (regenerationTicks[i] > 0) {
                regenerationTicks[i]--;
                // This is the selected potion treatment, never hunger/natural regeneration.
                integrity[i] = unit(integrity[i] + .00012f);
            }
            float floor = (1 - integrity[i]) * .2f + bleeding[i] * .3f;
            if (fractured[i]) floor += splinted[i] ? .025f : .15f;
            localPain[i] = Math.max(unit(floor), localPain[i] - .00012f);
            // A dry applied dressing remains actual care, not hunger/vanilla HP regen.
            receivingCare |= bandaged[i] && bleeding[i] <= .001f;
        }
        blood = unit(blood - totalBleeding * .00065f);
        // Slow recovery only during actual wound care, not simply because the player ate/rested.
        if (receivingCare && totalBleeding <= .001f && oxygen > .5f) blood = unit(blood + .00008f);
        drug = Math.max(0, drug - .0002f);
        float depression = Math.clamp((drug - .7f) / .6f, 0, 1.8f);
        float breathRecovery = .0008f * (1 - Math.min(1, depression));
        oxygen = unit(oxygen + breathRecovery - depression * .0012f
                - Math.max(0, .35f - blood) * .0015f - sepsis() * .00015f);
        if (moving && oxygen < .7f) oxygen = unit(oxygen - (sprinting ? .00018f : .00006f));
        boolean exertion = moving && mode != Mode.UNCONSCIOUS;
        float recovery = exertion ? 0 : .0007f * oxygen * Math.min(1, blood * 2);
        energy = unit(energy + recovery - (exertion ? sprinting ? .0012f : .00035f : 0)
                - Math.max(0, drug - .3f) * .00012f - sepsis() * .00018f);
        evaluate(true);
        if (mode == Mode.DEAD) { bpm = 0; return; }
        float target = 72 + (moving ? sprinting ? 65 : 18 : 0) + effectivePain() * 38
                + (1 - blood) * 65 - Math.min(1, depression) * 65;
        if (lastStandingBoostTicks > 0) target += 20;
        target = Math.clamp(target, 18, 200);
        bpm += (target - bpm) * (target > bpm ? .04f : .015f);
    }

    /** For environmental suffocation/drowning: no invented anatomical cut is created. */
    public void reduceOxygen(float amount) {
        if (mode == Mode.DEAD || !Float.isFinite(amount) || amount <= 0) return;
        oxygen = unit(oxygen - amount);
        evaluate(false);
    }

    /** Explicit lethal sources such as void/kill; ordinary attacks use damage(). */
    public void kill() { mode = Mode.DEAD; bpm = 0; wakeTicks = lastStandingTicks = 0; dyingTicks = 0; }

    public boolean removeTourniquet(BodyPart part) {
        int i = Objects.requireNonNull(part).ordinal();
        if (!tourniquet[i] || mode == Mode.DEAD) return false;
        tourniquet[i] = false; tourniquetTicks[i] = 0;
        bleeding[i] = unit((1 - integrity[i]) * .18f);
        return true;
    }

    public BodySnapshot snapshot() {
        PartSnapshot[] parts = new PartSnapshot[PARTS];
        float totalIntegrity = 0;
        for (int i = 0; i < PARTS; i++) {
            parts[i] = new PartSnapshot(integrity[i], bleeding[i], limbPain(i), fractured[i],
                    bandaged[i], splinted[i], tourniquet[i], infection[i], disinfectionTicks[i] > 0,
                    anestheticTicks[i] > 40, bandageSlowAmount[i]);
            totalIntegrity += integrity[i];
        }
        float condition = Math.min(Math.min(vitalIntegrity() * 1.15f, blood * 1.15f),
                Math.min(oxygen * 1.15f, totalIntegrity / PARTS - effectivePain() * .08f));
        if (mode == Mode.UNCONSCIOUS) condition = Math.min(condition, .32f);
        if (mode == Mode.LAST_STANDING) condition = Math.min(condition, .15f);
        if (mode == Mode.DYING) condition = Math.min(condition, .12f);
        if (mode == Mode.DEAD) condition = 0;
        float progress = mode == Mode.UNCONSCIOUS ? (float) wakeTicks / WAKE_UP_TICKS
                : mode == Mode.LAST_STANDING ? (float) lastStandingTicks / LAST_STANDING_TICKS
                : mode == Mode.DYING ? (float) Math.max(0, dyingTicks) / DYING_TICKS : 1;
        return new BodySnapshot(unit(condition), blood, oxygen, effectivePain(), drug, bpm, mode,
                progress, energy, consciousness(), parts);
    }

    /** Snapshot-only restore is conservative; use SavedState to preserve all treatment timers. */
    public static BodyModel restore(BodySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        BodyModel model = new BodyModel();
        model.loadSnapshot(snapshot);
        model.lastStandingSpent = snapshot.mode() != Mode.CONSCIOUS;
        model.lastStandingBoostTicks = model.lastStandingSpent ? LAST_STANDING_BOOST_TICKS : 0;
        if (snapshot.mode() == Mode.LAST_STANDING && model.vitalIntegrity() <= .11f)
            model.restoreLastStandingReserves(); // Migrate the old lethal-timer save conservatively.
        return model;
    }

    public SavedState saveState() {
        return new SavedState(snapshot(), careTicks, regenerationTicks, tourniquetTicks,
                lastStandingSpent, stableTicks, lastStandingTicks, lastStandingBoostTicks, dyingTicks,
                disinfectionTicks, anestheticTicks, infectionAgeTicks, skinHealAmount, bandageSlowAmount, localPain);
    }

    public static BodyModel restoreState(SavedState state) {
        Objects.requireNonNull(state, "state");
        BodyModel model = restore(state.snapshot());
        copyTimers(state.careTicks(), model.careTicks);
        copyTimers(state.regenerationTicks(), model.regenerationTicks);
        copyTimers(state.tourniquetTicks(), model.tourniquetTicks);
        model.lastStandingSpent = state.lastStandingSpent();
        model.stableTicks = Math.clamp(state.stableTicks(), 0, 1200);
        model.lastStandingTicks = Math.clamp(state.lastStandingTicks(), 0, LAST_STANDING_TICKS);
        model.lastStandingBoostTicks = Math.clamp(state.lastStandingBoostTicks(), 0, LAST_STANDING_BOOST_TICKS);
        model.dyingTicks = Math.clamp(state.dyingTicks(), -1, DYING_TICKS);
        copyTimers(state.disinfectionTicks(), model.disinfectionTicks);
        copyTimers(state.anestheticTicks(), model.anestheticTicks);
        copyTimers(state.infectionAgeTicks(), model.infectionAgeTicks);
        System.arraycopy(state.skinHealAmount(), 0, model.skinHealAmount, 0, PARTS);
        System.arraycopy(state.bandageSlowAmount(), 0, model.bandageSlowAmount, 0, PARTS);
        System.arraycopy(state.rawLocalPain(), 0, model.localPain, 0, PARTS);
        return model;
    }

    /** Serializable fields, independent of a particular NBT/JSON codec. */
    public record SavedState(BodySnapshot snapshot, int[] careTicks, int[] regenerationTicks,
            int[] tourniquetTicks, boolean lastStandingSpent, int stableTicks, int lastStandingTicks,
            int lastStandingBoostTicks, int dyingTicks, int[] disinfectionTicks, int[] anestheticTicks,
            int[] infectionAgeTicks, float[] skinHealAmount, float[] bandageSlowAmount, float[] rawLocalPain) {
        public SavedState(BodySnapshot snapshot, int[] careTicks, int[] regenerationTicks,
                int[] tourniquetTicks, boolean lastStandingSpent, int stableTicks) {
            this(snapshot, careTicks, regenerationTicks, tourniquetTicks, lastStandingSpent, stableTicks,
                    snapshot.mode() == Mode.LAST_STANDING ? Math.round(snapshot.consciousnessProgress() * LAST_STANDING_TICKS) : 0,
                    lastStandingSpent ? LAST_STANDING_BOOST_TICKS : 0,
                    snapshot.mode() == Mode.DYING ? Math.round(snapshot.consciousnessProgress() * DYING_TICKS) : -1,
                    new int[PARTS], new int[PARTS], new int[PARTS], new float[PARTS], new float[PARTS], snapshotPain(snapshot));
        }
        public SavedState {
            Objects.requireNonNull(snapshot, "snapshot");
            careTicks = checkedTimers(careTicks); regenerationTicks = checkedTimers(regenerationTicks);
            tourniquetTicks = checkedTimers(tourniquetTicks);
            disinfectionTicks = checkedTimers(disinfectionTicks); anestheticTicks = checkedTimers(anestheticTicks);
            infectionAgeTicks = checkedTimers(infectionAgeTicks);
            skinHealAmount = checkedCare(skinHealAmount); bandageSlowAmount = checkedCare(bandageSlowAmount);
            rawLocalPain = checkedCare(rawLocalPain);
            for (float value : rawLocalPain) if (value > 1) throw new IllegalArgumentException("Invalid raw pain");
        }
        @Override public int[] careTicks() { return careTicks.clone(); }
        @Override public int[] regenerationTicks() { return regenerationTicks.clone(); }
        @Override public int[] tourniquetTicks() { return tourniquetTicks.clone(); }
        @Override public int[] disinfectionTicks() { return disinfectionTicks.clone(); }
        @Override public int[] anestheticTicks() { return anestheticTicks.clone(); }
        @Override public int[] infectionAgeTicks() { return infectionAgeTicks.clone(); }
        @Override public float[] skinHealAmount() { return skinHealAmount.clone(); }
        @Override public float[] bandageSlowAmount() { return bandageSlowAmount.clone(); }
        @Override public float[] rawLocalPain() { return rawLocalPain.clone(); }
    }

    private void loadSnapshot(BodySnapshot state) {
        blood = state.blood(); oxygen = state.oxygen(); energy = state.energy(); drug = state.drug(); bpm = state.bpm(); mode = state.mode();
        wakeTicks = mode == Mode.UNCONSCIOUS ? Math.round(state.consciousnessProgress() * WAKE_UP_TICKS) : 0;
        lastStandingTicks = mode == Mode.LAST_STANDING ? Math.round(state.consciousnessProgress() * LAST_STANDING_TICKS) : 0;
        dyingTicks = mode == Mode.DYING ? Math.round(state.consciousnessProgress() * DYING_TICKS) : -1;
        for (BodyPart part : BodyPart.values()) {
            int i = part.ordinal(); PartSnapshot value = state.part(part);
            integrity[i] = value.integrity(); bleeding[i] = value.bleeding(); localPain[i] = value.pain();
            fractured[i] = value.fractured(); bandaged[i] = value.bandaged(); splinted[i] = value.splinted();
            tourniquet[i] = value.tourniquet();
            infection[i] = value.infection();
            bandageSlowAmount[i] = value.bandageCare();
            disinfectionTicks[i] = value.disinfected() ? 6000 : 0;
            anestheticTicks[i] = value.anesthetized() ? 1200 : 0;
        }
        if (mode == Mode.DEAD) bpm = 0;
    }

    private void evaluate(boolean elapsedTick) {
        if (mode == Mode.DEAD) return;
        boolean extreme = vitalIntegrity() <= .08f || oxygen <= .15f || blood <= .04f
                || blood <= .16f && totalBleeding() > .001f;
        if (mode == Mode.LAST_STANDING) {
            if (elapsedTick && lastStandingTicks > 0) lastStandingTicks--;
            if (lastStandingTicks > 0) return;
            mode = Mode.CONSCIOUS; // Intro expiry is not death or loss of the restored reserves.
        }
        if (mode == Mode.DYING) {
            if (!extreme) { mode = Mode.UNCONSCIOUS; wakeTicks = 0; }
            else {
                if (elapsedTick && dyingTicks > 0) dyingTicks--;
                if (dyingTicks <= 0) kill();
                return;
            }
        }
        if (extreme) {
            if (lastStandingSpent) {
                if (dyingTicks < 0) dyingTicks = DYING_TICKS;
                mode = Mode.DYING; wakeTicks = stableTicks = 0;
                return;
            }
            lastStandingSpent = true; lastStandingTicks = LAST_STANDING_TICKS;
            lastStandingBoostTicks = LAST_STANDING_BOOST_TICKS;
            restoreLastStandingReserves();
            mode = Mode.LAST_STANDING; wakeTicks = stableTicks = 0;
            return;
        }
        boolean faint = vitalIntegrity() <= .22f || blood <= .45f || oxygen <= .45f
                || effectivePain() >= .85f || energy <= .06f || consciousness() <= .15f;
        if (faint) {
            mode = Mode.UNCONSCIOUS; wakeTicks = stableTicks = 0;
        } else if (mode == Mode.UNCONSCIOUS) {
            if (elapsedTick && ++wakeTicks >= WAKE_UP_TICKS) { mode = Mode.CONSCIOUS; wakeTicks = 0; }
        }
        if (elapsedTick && mode == Mode.CONSCIOUS && blood >= .7f && oxygen >= .7f && vitalIntegrity() >= .6f) {
            stableTicks = Math.min(1200, stableTicks + 1);
            if (stableTicks >= 1200 && lastStandingBoostTicks == 0) {
                lastStandingSpent = false; dyingTicks = -1;
            }
        } else if (mode != Mode.CONSCIOUS) stableTicks = 0;
    }

    private float vitalIntegrity() { return Math.min(integrity[BodyPart.HEAD.ordinal()], integrity[BodyPart.TORSO.ordinal()]); }
    private void restoreLastStandingReserves() {
        integrity[BodyPart.HEAD.ordinal()] = Math.max(.75f, integrity[BodyPart.HEAD.ordinal()]);
        integrity[BodyPart.TORSO.ordinal()] = Math.max(.5f, integrity[BodyPart.TORSO.ordinal()]);
        blood = Math.max(.5f, blood); oxygen = energy = 1;
        for (int i = 0; i < PARTS; i++) { bleeding[i] *= .05f; infection[i] *= .05f; }
        bpm = Math.max(100, bpm);
    }
    private float totalBleeding() { float sum = 0; for (float value : bleeding) sum += value; return sum; }
    private float sepsis() { float sum = 0; for (float value : infection) sum += value; return unit((sum - 1) / 3); }
    private float limbPain(int i) { return anestheticTicks[i] > 0 ? 0 : localPain[i]; }
    private float consciousness() {
        if (mode == Mode.DEAD) return 0;
        float reserves = Math.min(Math.min(unit(oxygen / .8f), unit(blood / .6f)), unit(vitalIntegrity() / .35f));
        return unit(reserves * (.25f + .75f * energy) * (1 - effectivePain() * .35f)
                * (1 - Math.min(1, drug) * .45f) * (1 - sepsis() * .4f));
    }
    private float effectivePain() {
        float sum = 0, maximum = 0;
        for (int i = 0; i < PARTS; i++) { float value = limbPain(i); sum += value; maximum = Math.max(maximum, value); }
        float raw = .5f * (sum / PARTS) + .5f * maximum;
        return unit(raw * (1 - Math.min(.8f, drug * .9f)));
    }
    private static float unit(float value) { return Math.clamp(value, 0, 1); }
    private static float[] snapshotPain(BodySnapshot snapshot) {
        float[] values = new float[PARTS];
        for (int i = 0; i < PARTS; i++) values[i] = snapshot.parts()[i].pain();
        return values;
    }
    private static float[] checkedCare(float[] values) {
        Objects.requireNonNull(values, "care");
        if (values.length != PARTS) throw new IllegalArgumentException("Six care buffers required");
        float[] copy = values.clone();
        for (float value : copy) if (!Float.isFinite(value) || value < 0 || value > 10)
            throw new IllegalArgumentException("Invalid care buffer");
        return copy;
    }
    private static int[] checkedTimers(int[] values) {
        Objects.requireNonNull(values, "timers");
        if (values.length != PARTS) throw new IllegalArgumentException("Six timers required");
        int[] copy = values.clone();
        for (int value : copy) if (value < 0) throw new IllegalArgumentException("Negative treatment timer");
        return copy;
    }
    private static void copyTimers(int[] source, int[] target) { System.arraycopy(source, 0, target, 0, PARTS); }
}
