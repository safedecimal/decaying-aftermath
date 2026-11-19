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
    public static final int WAKE_UP_TICKS = 20 * 6;
    public static final int BONE_HEAL_TICKS = 20 * 60 * 8; // Deliberately compressed game recovery time.
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
    private final float[] burns = new float[PARTS], internalBleeding = new float[PARTS];
    private final float[] suppressedBleeding = new float[PARTS];
    private final boolean[] coldInjuries = new boolean[PARTS];
    private final boolean[] openWounds = new boolean[PARTS], foreignBodies = new boolean[PARTS];
    private final int[] boneHealing = new int[PARTS], coolingTicks = new int[PARTS];
    private float lungCollapse;
    private boolean chestDrain;
    private SurvivalModel survival = new SurvivalModel();
    private float blood = 1, oxygen = 1, energy = 1, drug, bpm = 72;
    private Mode mode = Mode.CONSCIOUS;
    private int lastStandingTicks, wakeTicks, stableTicks;
    private int lastStandingBoostTicks, dyingTicks = -1;
    private boolean lastStandingSpent, faintRecovered;

    public BodyModel() { reset(); }

    public void reset() {
        Arrays.fill(integrity, 1); Arrays.fill(bleeding, 0); Arrays.fill(localPain, 0);
        Arrays.fill(fractured, false); Arrays.fill(bandaged, false); Arrays.fill(splinted, false);
        Arrays.fill(tourniquet, false); Arrays.fill(careTicks, 0); Arrays.fill(regenerationTicks, 0);
        Arrays.fill(tourniquetTicks, 0);
        Arrays.fill(infection, 0); Arrays.fill(skinHealAmount, 0); Arrays.fill(bandageSlowAmount, 0);
        Arrays.fill(disinfectionTicks, 0); Arrays.fill(anestheticTicks, 0); Arrays.fill(infectionAgeTicks, 0);
        Arrays.fill(burns,0); Arrays.fill(internalBleeding,0); Arrays.fill(openWounds,false);
        Arrays.fill(foreignBodies,false); Arrays.fill(boneHealing,0); Arrays.fill(coolingTicks,0);
        Arrays.fill(suppressedBleeding,0);
        Arrays.fill(coldInjuries,false);
        lungCollapse=0; chestDrain=false; survival=new SurvivalModel();
        blood = oxygen = energy = 1; drug = 0; bpm = 72; mode = Mode.CONSCIOUS;
        lastStandingTicks = wakeTicks = stableTicks = 0; lastStandingSpent = false;
        lastStandingBoostTicks = 0; dyingTicks = -1; faintRecovered = false;
    }

    /** Input is already mitigated vanilla damage units, not normalized tissue damage. */
    public void damage(BodyPart part, float amount) {
        damage(part, amount, DamageKind.CUT); // Legacy callers lack a reliable mechanism.
    }

    public void damage(BodyPart part, float amount, DamageKind kind) {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(kind, "kind");
        if (mode == Mode.DEAD || !Float.isFinite(amount) || amount <= 0) return;
        amount = Math.min(amount, 1000);
        int i = part.ordinal();
        integrity[i] = unit(integrity[i] - amount * kind.tissue);
        localPain[i] = unit(localPain[i] + amount * kind.pain);
        bleeding[i] = unit(bleeding[i] + amount * kind.surfaceBleed);
        if (kind.opensSkin) { openWounds[i]=true; infectionAgeTicks[i]=0; }
        if (kind==DamageKind.BURN || kind==DamageKind.CHEMICAL) {
            burns[i]=unit(burns[i]+amount*.025f);
            if (burns[i]>.3f) openWounds[i]=true;
        }
        if(kind==DamageKind.FROSTBITE){coldInjuries[i]=true;if(integrity[i]<.45f)openWounds[i]=true;}
        if ((kind==DamageKind.BLUNT || kind==DamageKind.EXPLOSION || kind==DamageKind.FALL_IMPACT) && amount>6)
            internalBleeding[i]=unit(internalBleeding[i]+(amount-6)*.006f);
        if (kind==DamageKind.PUNCTURE && amount>=4) {
            foreignBodies[i]=true;
            if (part==BodyPart.TORSO) { internalBleeding[i]=unit(internalBleeding[i]+amount*.004f); lungCollapse=unit(lungCollapse+amount*.03f); }
        }
        // A new hit can disturb a dressing but does not silently remove an applied tourniquet.
        if (tourniquet[i]) {suppressedBleeding[i]=unit(suppressedBleeding[i]+amount*kind.surfaceBleed);bleeding[i] = 0;}
        else if (bandaged[i]) bleeding[i] *= .7f;
        if (kind.fractures && part.isLimb() && (amount >= 8 || amount >= 4 && integrity[i] <= .45f)) {
            fractured[i] = true; boneHealing[i]=0;
        }
        faintRecovered = false; // A new injury may cause a new faint, unlike an unchanged wound.
        evaluateAndAlign(false);
    }

    public void fall(float amount) {
        if (!Float.isFinite(amount) || amount <= 0 || mode == Mode.DEAD) return;
        for(var hit:FallTrauma.hits(amount))if(hit.amount()>0)damage(hit.part(),hit.amount(),hit.kind());
    }
    public void projectile(BodyPart part,float amount,boolean embedded) {
        boolean existing=foreignBodies[Objects.requireNonNull(part).ordinal()];
        damage(part,amount,DamageKind.PUNCTURE);
        if(!embedded)foreignBodies[part.ordinal()]=existing;
    }

    /** Supplies may be wasted. Only a dead target rejects a procedure. */
    public boolean canTreat(BodyPart part,TreatmentKind treatment) {
        Objects.requireNonNull(part);Objects.requireNonNull(treatment);return mode!=Mode.DEAD;
    }

    /** Accepted does not imply useful: wrong placement consumes supplies without treatment benefit. */
    public boolean treat(BodyPart part, TreatmentKind treatment, float amount) {
        Objects.requireNonNull(part, "part"); Objects.requireNonNull(treatment, "treatment");
        if (mode == Mode.DEAD || !Float.isFinite(amount) || amount <= 0) return false;
        if (!canTreat(part,treatment)) return false;
        if(treatment.requiresLimb()&&!part.isLimb()||treatment.requiresTorso()&&part!=BodyPart.TORSO)return true;
        int i = part.ordinal();
        amount = treatment == TreatmentKind.BANDAGE || treatment == TreatmentKind.PRESSURE_DRESSING
                ? Math.clamp(amount, 0, 12) : unit(amount);
        switch (treatment) {
            case POTION -> { return false; } // Non-medicinal status effects belong to the server adapter.
            case BANDAGE -> {
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
                bleeding[i] = Math.max(0, bleeding[i] - .7f * amount);
                bandaged[i] = true;
                careTicks[i] = Math.min(2400, careTicks[i] + Math.round(amount * 2400));
            }
            case SPLINT -> {
                splinted[i] = true;
                localPain[i] = Math.max(0, localPain[i] - .25f * amount);
            }
            case TOURNIQUET -> {
                float residual=bleeding[i];
                bleeding[i] = Math.max(0, bleeding[i] - amount);
                if (amount >= .5f) { suppressedBleeding[i]=residual; tourniquet[i] = true; bleeding[i] = 0; }
            }
            case FENTANYL -> drug = Math.clamp(drug + amount * .65f, 0, 3);
            case PAINKILLERS -> drug = Math.clamp(drug + amount * .30f, 0, 3);
            case ANTIBIOTICS,NALOXONE,COAGULANT,COMBAT_STIMULANT,BLOOD_BAG,SALINE -> {
                survival.treat(treatment,amount); blood=survival.bloodVolume();
            }
            case ICE_PACK -> coolingTicks[i]=Math.min(2400,coolingTicks[i]+Math.round(amount*1200));
            case CHEST_DRAIN -> chestDrain=true;
            case REMOVE_FOREIGN_BODY -> foreignBodies[i]=false;
            case ANESTHETIC -> {
                // CU localanesthetic: selected limb only, timed analgesia, no opiate
                // burden and no tissue/infection healing. Fractions are game supply.
                anestheticTicks[i] = Math.min(12000, anestheticTicks[i] + Math.round(amount * 1200));
            }
            case ANTISEPTIC -> {
                disinfectionTicks[i] = Math.max(disinfectionTicks[i], Math.round(amount * WoundInfection.SPRAY_TICKS));
                localPain[i] = unit(localPain[i] + .10f * amount);
            }
            case HEALING_POTION -> {
                // Outside Last Standing even potion care is gradual and reserve-dependent.
                skinHealAmount[i]=Math.min(10,skinHealAmount[i]+.16f*amount);
                careTicks[i]=Math.min(2400,careTicks[i]+Math.round(amount*1200));
                localPain[i]=Math.max(0,localPain[i]-.08f*amount);
            }
            case REGENERATION_POTION -> {
                regenerationTicks[i] = Math.min(1200, regenerationTicks[i] + Math.round(amount * 1200));
            }
        }
        evaluateAndAlign(false);
        return true;
    }

    /** Every elapsed game tick is explicit; no care is advanced by requesting a snapshot. */
    public void tick(boolean sprinting, boolean moving) {
        tick(BodyEnvironment.temperate(sprinting,moving));
    }

    public void tick(BodyEnvironment environment) {
        Objects.requireNonNull(environment);
        boolean sprinting=environment.sprinting(),moving=environment.moving();
        if (mode == Mode.DEAD) { bpm = 0; return; }
        if (lastStandingBoostTicks > 0) lastStandingBoostTicks--;
        float totalBleeding = 0;
        boolean receivingCare = false;
        for (int i = 0; i < PARTS; i++) {
            if (anestheticTicks[i] > 0) anestheticTicks[i]--;
            float infectionBefore = infection[i];
            boolean protectedWound = disinfectionTicks[i] > 0;
            infection[i] = unit(infectionBefore + infectionDelta(i));
            if (protectedWound) disinfectionTicks[i]--;
            if (infection[i] == 0 && (protectedWound || infectionBefore > 0 || !canIncubate(i))) {
                infectionAgeTicks[i] = 0;
            } else if (infection[i] == 0 && infectionAgeTicks[i] < WoundInfection.INCUBATION_TICKS) {
                infectionAgeTicks[i]++;
            }
            if(coolingTicks[i]>0) {
                coolingTicks[i]--; localPain[i]=Math.max(0,localPain[i]-.00015f);
                survival.cool(.02f);
            }
            if (infection[i] > .75f) {
                integrity[i] = unit(integrity[i] - .00010f);
                localPain[i] = unit(localPain[i] + .00006f);
            }
            if (tourniquet[i]) {
                bleeding[i] = 0;
                suppressedBleeding[i]=Math.max(0,suppressedBleeding[i]-.0000125f*survival.clottingFactor());
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
            bleeding[i] = Math.max(0, bleeding[i] - .0000125f*survival.clottingFactor());
            totalBleeding += bleeding[i];
            internalBleeding[i]=Math.max(0,internalBleeding[i]-.000008f*survival.clottingFactor());
            totalBleeding+=internalBleeding[i];
            boolean perfused=!tourniquet[i] && oxygen>.5f && blood>.3f && survival.nutrition()>.2f
                    && survival.oxygenDelivery(1-lungCollapse)>.6f && infection[i]<.6f;
            boolean nativeSkinCare = skinHealAmount[i] > 0;
            if (nativeSkinCare) {
                float used = Math.min(skinHealAmount[i], .00025f);
                if (perfused) {
                    integrity[i] = unit(integrity[i] + used);
                    skinHealAmount[i] = Math.max(0, skinHealAmount[i] - used);
                }
                receivingCare = true;
            }
            if (careTicks[i] > 0) {
                receivingCare = true;
                careTicks[i]--;
                if (!nativeSkinCare && bleeding[i] <= .001f && perfused)
                    integrity[i] = unit(integrity[i] + .00004f);
            }
            if (regenerationTicks[i] > 0) {
                regenerationTicks[i]--;
                // This is the selected potion treatment, never hunger/natural regeneration.
                if(perfused)integrity[i] = unit(integrity[i] + .00012f);
            }
            if(fractured[i] && perfused && oxygen>.6f && blood>.5f && survival.nutrition()>.25f
                    && infection[i]<.35f && !tourniquet[i]) {
                if(splinted[i]) boneHealing[i]++;
                else if(moving) boneHealing[i]=Math.max(0,boneHealing[i]-2);
                int needed=BONE_HEAL_TICKS*(integrity[i]<.4f?2:1);
                if(boneHealing[i]>=needed) { fractured[i]=false; boneHealing[i]=0; }
            }
            if(burns[i]>0 && integrity[i]>.7f && perfused && infection[i]<.25f)
                burns[i]=Math.max(0,burns[i]-.000004f);
            if(coldInjuries[i]&&integrity[i]>.9f&&survival.temperature()>=36)coldInjuries[i]=false;
            if(integrity[i]>=.999f && bleeding[i]<=0 && infection[i]<=.001f && !foreignBodies[i]) openWounds[i]=false;
            float floor = (1 - integrity[i]) * .2f + bleeding[i] * .3f;
            if (fractured[i]) floor += splinted[i] ? .025f : .15f;
            localPain[i] = Math.max(unit(floor), localPain[i] - .00012f);
            // A dry applied dressing remains actual care, not hunger/vanilla HP regen.
            receivingCare |= bandaged[i] && bleeding[i] <= .001f;
        }
        survival.tick(environment,totalBleeding*MedicalPhysiology.BLOOD_LOSS_PER_TICK_PER_SEVERITY,sepsis());
        blood=survival.bloodVolume();
        if(chestDrain) lungCollapse=Math.max(0,lungCollapse-.0006f);
        drug = Math.max(0, drug - .0002f);
        float activeDrug=survival.effectiveOpiate(drug);
        float depression = Math.clamp((activeDrug - .7f) / .6f, 0, 1.8f);
        float breathRecovery = environment.breathable() ? .0008f * (1 - Math.min(1, depression))*(1-lungCollapse) : 0;
        oxygen = unit(oxygen + breathRecovery - depression * .0012f
                - Math.max(0, .35f - blood) * .0015f - sepsis() * .00015f
                - lungCollapse*.0005f - (environment.breathable()?0:.0008f));
        if (moving && oxygen < .7f) oxygen = unit(oxygen - (sprinting ? .00018f : .00006f));
        boolean exertion = moving && mode != Mode.UNCONSCIOUS;
        float recovery = exertion ? 0 : .0007f * oxygen * survival.oxygenDelivery(1-lungCollapse)
                * Math.min(1,survival.hydration()*2)*Math.min(1,survival.nutrition()*2)*(1-survival.fatigue()*.7f);
        float load=1+Math.max(0,environment.encumbrance()-.5f)*.6f;
        energy = unit(energy + recovery - (exertion ? sprinting ? .0012f : .00035f : 0)*load
                - Math.max(0,activeDrug-.3f)*.00012f - sepsis()*.00018f - survival.systemicStress()*.00018f);
        if(survival.nutrition()<.08f) energy=unit(energy-.0001f);
        if(survival.thermalStress()>.8f) integrity[BodyPart.TORSO.ordinal()]=unit(integrity[BodyPart.TORSO.ordinal()]-.000025f);
        if(survival.nutrition()<.02f||survival.hydration()<.04f||survival.systemicStress()>.85f)
            integrity[BodyPart.TORSO.ordinal()]=unit(integrity[BodyPart.TORSO.ordinal()]-.000025f);
        evaluateAndAlign(true);
        if (mode == Mode.DEAD) { bpm = 0; return; }
        float target = 72 + (moving ? sprinting ? 65 : 18 : 0) + effectivePain() * 38
                + (1 - blood) * 65 - Math.min(1, depression) * 65;
        if (lastStandingBoostTicks > 0) target += 20;
        target+=survival.stimulantAction()*25+survival.systemicStress()*18;
        target = Math.clamp(target, 18, 200);
        bpm += (target - bpm) * (target > bpm ? .04f : .015f);
    }

    /** For environmental suffocation/drowning: no invented anatomical cut is created. */
    public void reduceOxygen(float amount) {
        if (mode == Mode.DEAD || !Float.isFinite(amount) || amount <= 0) return;
        oxygen = unit(oxygen - amount);
        evaluateAndAlign(false);
    }

    public void consume(float nutrition,float hydration) { if(mode!=Mode.DEAD) survival.feed(nutrition,hydration); }
    public void irradiate(float doseGy) { if(mode!=Mode.DEAD)survival.irradiate(doseGy); }
    public void poison(float amount) { if(mode!=Mode.DEAD) survival.poison(amount); }
    public void drinkContaminated(float pathogens,float chemicals){if(mode!=Mode.DEAD)survival.drinkContaminated(pathogens,chemicals);}
    public void caffeinate(float amount){if(mode!=Mode.DEAD)survival.caffeinate(amount);}
    public void starve(float amount) {
        if(mode==Mode.DEAD||!Float.isFinite(amount)||amount<=0)return;
        survival.starvation(amount); energy=unit(energy-amount*.025f); evaluateAndAlign(false);
    }
    public void finishNightSleep() { if(mode!=Mode.DEAD)survival.wakeFromNight(); }

    /** Explicit lethal sources such as void/kill; ordinary attacks use damage(). */
    public void kill() { mode = Mode.DEAD; bpm = 0; wakeTicks = lastStandingTicks = 0; dyingTicks = 0; }

    public boolean removeTourniquet(BodyPart part) {
        int i = Objects.requireNonNull(part).ordinal();
        if (!tourniquet[i] || mode == Mode.DEAD) return false;
        tourniquet[i] = false; tourniquetTicks[i] = 0;
        bleeding[i] = suppressedBleeding[i];suppressedBleeding[i]=0;
        evaluateAndAlign(false);
        return true;
    }

    public boolean removeSplint(BodyPart part) {
        int i=Objects.requireNonNull(part).ordinal();
        if(!splinted[i]||mode==Mode.DEAD)return false;
        splinted[i]=false;
        // Removal never heals a fracture or discards its existing recovery progress.
        evaluateAndAlign(false);return true;
    }

    private boolean canIncubate(int i) {
        return (openWounds[i] && integrity[i] < .8f) || foreignBodies[i];
    }

    private float infectionDelta(int i) {
        boolean treated = disinfectionTicks[i] > 0;
        if (infection[i] == 0 && (treated || !canIncubate(i)
                || infectionAgeTicks[i] < WoundInfection.INCUBATION_TICKS)) return 0;
        return WoundInfection.localDelta(treated, foreignBodies[i]) - .000075f * survival.antibioticAction();
    }

    public BodySnapshot snapshot() {
        PartSnapshot[] parts = new PartSnapshot[PARTS];
        float totalIntegrity = 0;
        for (int i = 0; i < PARTS; i++) {
            parts[i] = new PartSnapshot(integrity[i], bleeding[i], limbPain(i), fractured[i],
                    bandaged[i], splinted[i], tourniquet[i], infection[i], disinfectionTicks[i] > 0,
                    anestheticTicks[i] > 40, bandageSlowAmount[i],burns[i],internalBleeding[i],openWounds[i],
                    foreignBodies[i],fractured[i]?Math.clamp((float)boneHealing[i]/BONE_HEAL_TICKS,0,1):1,coldInjuries[i],
                    disinfectionTicks[i], infectionDelta(i));
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
                progress, energy, consciousness(), parts,survival.snapshot(totalInternalBleeding(),1-lungCollapse));
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
        model.reconcileBlood();
        return model;
    }

    public SavedState saveState() {
        return new SavedState(snapshot(), careTicks, regenerationTicks, tourniquetTicks,
                lastStandingSpent, stableTicks, lastStandingTicks, lastStandingBoostTicks, dyingTicks,
                disinfectionTicks, anestheticTicks, infectionAgeTicks, skinHealAmount, bandageSlowAmount, localPain,
                new WoundState(burns,internalBleeding,openWounds,foreignBodies,boneHealing,coolingTicks,lungCollapse,chestDrain,suppressedBleeding,coldInjuries),
                survival.save(totalInternalBleeding(),1-lungCollapse), faintRecovered);
    }

    public static BodyModel restoreState(SavedState state) {
        Objects.requireNonNull(state, "state");
        BodyModel model = restore(state.snapshot());
        copyTimers(state.careTicks(), model.careTicks);
        copyTimers(state.regenerationTicks(), model.regenerationTicks);
        copyTimers(state.tourniquetTicks(), model.tourniquetTicks);
        model.lastStandingSpent = state.lastStandingSpent();
        model.faintRecovered = state.faintRecovered();
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
        WoundState wounds=state.wounds();
        System.arraycopy(wounds.burns(),0,model.burns,0,PARTS); System.arraycopy(wounds.internalBleeding(),0,model.internalBleeding,0,PARTS);
        System.arraycopy(wounds.openWounds(),0,model.openWounds,0,PARTS); System.arraycopy(wounds.foreignBodies(),0,model.foreignBodies,0,PARTS);
        copyTimers(wounds.boneHealing(),model.boneHealing); copyTimers(wounds.coolingTicks(),model.coolingTicks);
        System.arraycopy(wounds.suppressedBleeding(),0,model.suppressedBleeding,0,PARTS);
        System.arraycopy(wounds.coldInjuries(),0,model.coldInjuries,0,PARTS);
        model.lungCollapse=wounds.lungCollapse(); model.chestDrain=wounds.chestDrain();
        model.survival=SurvivalModel.restore(state.survivalState());
        model.blood=model.survival.bloodVolume();
        return model;
    }

    /** Serializable fields, independent of a particular NBT/JSON codec. */
    public record SavedState(BodySnapshot snapshot, int[] careTicks, int[] regenerationTicks,
            int[] tourniquetTicks, boolean lastStandingSpent, int stableTicks, int lastStandingTicks,
            int lastStandingBoostTicks, int dyingTicks, int[] disinfectionTicks, int[] anestheticTicks,
            int[] infectionAgeTicks, float[] skinHealAmount, float[] bandageSlowAmount, float[] rawLocalPain,
            WoundState wounds,SurvivalModel.SavedState survivalState, boolean faintRecovered) {
        public SavedState(BodySnapshot snapshot, int[] careTicks, int[] regenerationTicks,
                int[] tourniquetTicks, boolean lastStandingSpent, int stableTicks, int lastStandingTicks,
                int lastStandingBoostTicks, int dyingTicks, int[] disinfectionTicks, int[] anestheticTicks,
                int[] infectionAgeTicks, float[] skinHealAmount, float[] bandageSlowAmount, float[] rawLocalPain,
                WoundState wounds, SurvivalModel.SavedState survivalState) {
            this(snapshot, careTicks, regenerationTicks, tourniquetTicks, lastStandingSpent, stableTicks,
                    lastStandingTicks, lastStandingBoostTicks, dyingTicks, disinfectionTicks, anestheticTicks,
                    infectionAgeTicks, skinHealAmount, bandageSlowAmount, rawLocalPain, wounds, survivalState, false);
        }
        public SavedState(BodySnapshot snapshot, int[] careTicks, int[] regenerationTicks,
                int[] tourniquetTicks, boolean lastStandingSpent, int stableTicks, int lastStandingTicks,
                int lastStandingBoostTicks,int dyingTicks,int[] disinfectionTicks,int[] anestheticTicks,
                int[] infectionAgeTicks,float[] skinHealAmount,float[] bandageSlowAmount,float[] rawLocalPain) {
            this(snapshot,careTicks,regenerationTicks,tourniquetTicks,lastStandingSpent,stableTicks,lastStandingTicks,
                    lastStandingBoostTicks,dyingTicks,disinfectionTicks,anestheticTicks,infectionAgeTicks,skinHealAmount,
                    bandageSlowAmount,rawLocalPain,WoundState.legacy(snapshot),new SurvivalModel.SavedState(snapshot.survival(),0,0));
        }
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
            Objects.requireNonNull(wounds, "wounds"); Objects.requireNonNull(survivalState, "survivalState");
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
        survival=new SurvivalModel(state.survival()); lungCollapse=1-state.survival().lungFunction();
        wakeTicks = mode == Mode.UNCONSCIOUS ? Math.round(state.consciousnessProgress() * WAKE_UP_TICKS) : 0;
        lastStandingTicks = mode == Mode.LAST_STANDING ? Math.round(state.consciousnessProgress() * LAST_STANDING_TICKS) : 0;
        dyingTicks = mode == Mode.DYING ? Math.round(state.consciousnessProgress() * DYING_TICKS) : -1;
        for (BodyPart part : BodyPart.values()) {
            int i = part.ordinal(); PartSnapshot value = state.part(part);
            integrity[i] = value.integrity(); bleeding[i] = value.bleeding(); localPain[i] = value.pain();
            fractured[i] = value.fractured(); bandaged[i] = value.bandaged(); splinted[i] = value.splinted();
            tourniquet[i] = value.tourniquet();
            infection[i] = value.infection();
            burns[i]=value.burn(); internalBleeding[i]=value.internalBleeding(); openWounds[i]=value.openWound();
            foreignBodies[i]=value.foreignBody(); boneHealing[i]=Math.round(value.boneRecovery()*BONE_HEAL_TICKS);
            coldInjuries[i]=value.coldInjury();
            bandageSlowAmount[i] = value.bandageCare();
            disinfectionTicks[i] = value.disinfectionTicks();
            anestheticTicks[i] = value.anesthetized() ? 1200 : 0;
        }
        if (mode == Mode.DEAD) bpm = 0;
    }

    /** Finish a mild recovery early without changing oxygen, blood or injury. */
    public boolean tryStandUp() {
        if (mode != Mode.UNCONSCIOUS || wakeTicks < WakeRecovery.EARLY_STAND_TICKS
                || !WakeRecovery.canStandEarly(snapshot())) return false;
        energy = unit(energy - WakeRecovery.STAND_ENERGY_COST);
        finishFaintRecovery();
        return true;
    }

    private void finishFaintRecovery() {
        mode = Mode.CONSCIOUS;
        wakeTicks = 0;
        faintRecovered = true;
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
                || effectivePain() >= .85f || consciousness() <= .15f;
        boolean blocked = WakeRecovery.blocker(vitalIntegrity(), blood, oxygen, consciousness()) != WakeRecovery.Blocker.NONE;
        if (!faint) faintRecovered = false; // Rearm only after the original trigger clears (hysteresis).
        if (mode == Mode.UNCONSCIOUS) {
            // Persistent pain/tissue damage no longer erase progress every tick. Unsafe physiology
            // pauses the countdown instead; critical Last Standing / dying handling above is unchanged.
            if (!blocked && elapsedTick && ++wakeTicks >= WAKE_UP_TICKS) finishFaintRecovery();
        } else if (faint && (!faintRecovered || blocked)) {
            mode = Mode.UNCONSCIOUS; wakeTicks = stableTicks = 0;
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
    private float totalBleeding() { float sum = 0; for (float value : bleeding) sum += value;return sum+totalInternalBleeding(); }
    private float totalInternalBleeding(){float sum=0;for(float value:internalBleeding)sum+=value;return sum;}
    private float sepsis() {
        float sum=0,maximum=0;for(float value:infection){sum+=value;maximum=Math.max(maximum,value);}
        return MedicalPhysiology.sepsis(sum,maximum);
    }
    private float limbPain(int i) { return anestheticTicks[i] > 0 ? 0 : localPain[i]; }
    private float consciousness() {
        if (mode == Mode.DEAD) return 0;
        float reserves = Math.min(Math.min(unit(oxygen / .8f), unit(blood / .6f)), unit(vitalIntegrity() / .35f));
        return unit(reserves * (1 - effectivePain() * .35f)
                * (1 - Math.min(1, survival.effectiveOpiate(drug)) * .45f) * (1 - sepsis() * .4f)
                * Math.max(.1f,survival.oxygenDelivery(1-lungCollapse))*(1-survival.systemicStress()*.45f));
    }
    private float effectivePain() {
        float sum = 0, maximum = 0;
        for (int i = 0; i < PARTS; i++) { float value = limbPain(i); sum += value; maximum = Math.max(maximum, value); }
        float raw = .5f * (sum / PARTS) + .5f * maximum;
        return unit(raw * (1 - Math.min(.8f, survival.effectiveOpiate(drug) * .9f)));
    }
    private void evaluateAndAlign(boolean elapsedTick) { evaluate(elapsedTick); reconcileBlood(); }
    private void reconcileBlood() {
        // Last Standing selection and reserve restoration are unchanged.
        // Only their supernatural volume jump may restore both blood components instantly.
        if(Math.abs(blood-survival.bloodVolume())>.00001f) survival.restoreLastStandingBlood(blood);
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
