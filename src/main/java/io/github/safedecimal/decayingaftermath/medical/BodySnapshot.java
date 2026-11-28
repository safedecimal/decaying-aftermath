package io.github.safedecimal.decayingaftermath.medical;

import java.util.Objects;

/** Game-only quantities are normalized except BPM and the cumulative 0..3 drug burden. */
public record BodySnapshot(float condition, float blood, float oxygen, float pain, float drug,
        float bpm, Mode mode, float consciousnessProgress, float energy, float consciousness,
        PartSnapshot[] parts, SurvivalSnapshot survival, BloodGroup bloodGroup, int cardiacTicks, float brainHealth,
        boolean voluntarySleeping, float sleepProgress) {
    public BodySnapshot(float condition, float blood, float oxygen, float pain, float drug,
            float bpm, Mode mode, float consciousnessProgress, float energy, float consciousness,
            PartSnapshot[] parts, SurvivalSnapshot survival, BloodGroup bloodGroup, int cardiacTicks, float brainHealth) {
        this(condition, blood, oxygen, pain, drug, bpm, mode, consciousnessProgress, energy, consciousness,
                parts, survival, bloodGroup, cardiacTicks, brainHealth, false, 0f);
    }
    public BodySnapshot(float condition,float blood,float oxygen,float pain,float drug,float bpm,Mode mode,
            float progress,float energy,float consciousness,PartSnapshot[] parts,SurvivalSnapshot survival,
            BloodGroup group,int cardiacTicks) {
        this(condition,blood,oxygen,pain,drug,bpm,mode,progress,energy,consciousness,parts,survival,group,cardiacTicks,mode==Mode.DEAD?0:1);
    }
    public BodySnapshot(float condition, float blood, float oxygen, float pain, float drug,
            float bpm, Mode mode, float consciousnessProgress, float energy, float consciousness,
            PartSnapshot[] parts, SurvivalSnapshot survival, BloodGroup group) {
        this(condition,blood,oxygen,pain,drug,bpm,mode,consciousnessProgress,energy,consciousness,parts,survival,group,0);
    }
    public boolean cardiacArrest() { return cardiacTicks < 0; }
    public boolean racingHeart() { return CardiacRhythm.unstable(cardiacTicks); }
    public BodySnapshot withCardiacTicks(int ticks) {
        return new BodySnapshot(condition,blood,oxygen,pain,drug,bpm,mode,consciousnessProgress,
                energy,consciousness,parts,survival,bloodGroup,ticks,brainHealth,voluntarySleeping,sleepProgress);
    }
    public BodySnapshot withParts(PartSnapshot[] values) {
        return new BodySnapshot(condition,blood,oxygen,pain,drug,bpm,mode,consciousnessProgress,
                energy,consciousness,values,survival,bloodGroup,cardiacTicks,brainHealth,voluntarySleeping,sleepProgress);
    }
    public BodySnapshot withBrainHealth(float value) {
        return new BodySnapshot(condition,blood,oxygen,pain,drug,bpm,mode,consciousnessProgress,
                energy,consciousness,parts,survival,bloodGroup,cardiacTicks,value,voluntarySleeping,sleepProgress);
    }
    // Append modes: saved pre-0.3.9 ordinal 3 must continue to mean DEAD.
    public enum Mode { CONSCIOUS, UNCONSCIOUS, LAST_STANDING, DEAD, DYING }

    /** Local body snapshots have no player identity until the server attaches it. */
    public BodySnapshot(float condition, float blood, float oxygen, float pain, float drug,
            float bpm, Mode mode, float consciousnessProgress, float energy, float consciousness,
            PartSnapshot[] parts, SurvivalSnapshot survival) {
        this(condition,blood,oxygen,pain,drug,bpm,mode,consciousnessProgress,energy,consciousness,
                parts,survival,null);
    }

    public BodySnapshot withBloodGroup(BloodGroup group) {
        return new BodySnapshot(condition,blood,oxygen,pain,drug,bpm,mode,consciousnessProgress,
                energy,consciousness,parts,survival,Objects.requireNonNull(group),cardiacTicks,brainHealth,
                voluntarySleeping,sleepProgress);
    }

    public BodySnapshot(float condition, float blood, float oxygen, float pain, float drug,
            float bpm, Mode mode, float consciousnessProgress, float energy, float consciousness,
            PartSnapshot[] parts) {
        this(condition,blood,oxygen,pain,drug,bpm,mode,consciousnessProgress,energy,consciousness,
                parts,SurvivalSnapshot.legacy(blood));
    }

    /** Older fixtures/save adapters have no fatigue field; they represent a rested body. */
    public BodySnapshot(float condition, float blood, float oxygen, float pain, float drug,
            float bpm, Mode mode, float consciousnessProgress, PartSnapshot[] parts) {
        this(condition, blood, oxygen, pain, drug, bpm, mode, consciousnessProgress, 1,
                mode == Mode.DEAD ? 0 : mode == Mode.UNCONSCIOUS ? .25f : 1, parts);
    }

    public BodySnapshot {
        condition = unit(condition); blood = unit(blood); oxygen = unit(oxygen); pain = unit(pain);
        drug = finiteClamp(drug, 0, 3);
        cardiacTicks = mode == Mode.DEAD ? 0 : CardiacRhythm.sanitize(cardiacTicks);
        bpm = cardiacTicks < 0 ? 0 : finiteClamp(bpm, 0, 220);
        consciousnessProgress = unit(consciousnessProgress);
        energy = unit(energy); consciousness = unit(consciousness);
        brainHealth=mode==Mode.DEAD?0:unit(brainHealth);
        if (mode != Mode.CONSCIOUS) { voluntarySleeping = false; sleepProgress = 0f; }
        else sleepProgress = voluntarySleeping ? unit(sleepProgress) : 0f;
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(survival, "survival");
        Objects.requireNonNull(parts, "parts");
        if (parts.length != BodyPart.values().length) throw new IllegalArgumentException("Six body parts required");
        parts = parts.clone();
        for (PartSnapshot part : parts) Objects.requireNonNull(part, "part");
    }

    @Override public PartSnapshot[] parts() { return parts.clone(); }
    public PartSnapshot part(BodyPart part) { return parts[Objects.requireNonNull(part).ordinal()]; }

    public record PartSnapshot(float integrity, BleedProfile externalBleeding, float pain, boolean fractured,
            boolean bandaged, boolean splinted, boolean tourniquet, float infection,
            boolean disinfected, boolean anesthetized, float bandageCare, float burn,
            float internalBleeding, boolean openWound, boolean foreignBody, float boneRecovery,boolean coldInjury,
            int disinfectionTicks,float infectionDelta) {
        public PartSnapshot(float integrity,float bleeding,float pain,boolean fractured,boolean bandaged,boolean splinted,
                boolean tourniquet,float infection,boolean disinfected,boolean anesthetized,float bandageCare,float burn,
                float internalBleeding,boolean openWound,boolean foreignBody,float boneRecovery,boolean coldInjury,
                int disinfectionTicks,float infectionDelta) {
            this(integrity,BleedProfile.legacy(bleeding),pain,fractured,bandaged,splinted,tourniquet,infection,disinfected,
                    anesthetized,bandageCare,burn,internalBleeding,openWound,foreignBody,boneRecovery,coldInjury,
                    disinfectionTicks,infectionDelta);
        }
        public float bleeding() { return externalBleeding.total(); }
        public PartSnapshot withExternalBleeding(BleedProfile flow) {
            return new PartSnapshot(integrity,flow,pain,fractured,bandaged,splinted,tourniquet,infection,disinfected,
                    anesthetized,bandageCare,burn,internalBleeding,openWound,foreignBody,boneRecovery,coldInjury,
                    disinfectionTicks,infectionDelta);
        }
        public PartSnapshot(float integrity,float bleeding,float pain,boolean fractured,boolean bandaged,boolean splinted,
                boolean tourniquet,float infection,boolean disinfected,boolean anesthetized,float bandageCare,float burn,
                float internalBleeding,boolean openWound,boolean foreignBody,float boneRecovery,boolean coldInjury) {
            this(integrity,bleeding,pain,fractured,bandaged,splinted,tourniquet,infection,disinfected,anesthetized,
                    bandageCare,burn,internalBleeding,openWound,foreignBody,boneRecovery,coldInjury,
                    disinfected ? WoundInfection.SPRAY_TICKS : 0, WoundInfection.localDelta(disinfected,foreignBody));
        }
        public PartSnapshot(float integrity,float bleeding,float pain,boolean fractured,boolean bandaged,boolean splinted,
                boolean tourniquet,float infection,boolean disinfected,boolean anesthetized,float bandageCare,float burn,
                float internalBleeding,boolean openWound,boolean foreignBody,float boneRecovery) {
            this(integrity,bleeding,pain,fractured,bandaged,splinted,tourniquet,infection,disinfected,anesthetized,
                    bandageCare,burn,internalBleeding,openWound,foreignBody,boneRecovery,false);
        }
        public PartSnapshot(float integrity, float bleeding, float pain, boolean fractured,
                boolean bandaged, boolean splinted, boolean tourniquet, float infection,
                boolean disinfected, boolean anesthetized, float bandageCare) {
            this(integrity,bleeding,pain,fractured,bandaged,splinted,tourniquet,infection,disinfected,
                    anesthetized,bandageCare,0,0,bleeding>0||bandaged,false,fractured?0:1);
        }
        public PartSnapshot(float integrity, float bleeding, float pain, boolean fractured,
                boolean bandaged, boolean splinted, boolean tourniquet) {
            this(integrity, bleeding, pain, fractured, bandaged, splinted, tourniquet, 0, false, false, bandaged ? 1 : 0);
        }
        public PartSnapshot(float integrity, float bleeding, float pain, boolean fractured,
                boolean bandaged, boolean splinted, boolean tourniquet, float infection,
                boolean disinfected, boolean anesthetized) {
            this(integrity, bleeding, pain, fractured, bandaged, splinted, tourniquet, infection,
                    disinfected, anesthetized, bandaged ? 1 : 0);
        }
        public PartSnapshot {
            integrity = unit(integrity); pain = unit(pain); infection = unit(infection);
            Objects.requireNonNull(externalBleeding, "externalBleeding");
            bandageCare = finiteClamp(bandageCare, 0, 10);
            burn=unit(burn); internalBleeding=unit(internalBleeding); boneRecovery=unit(boneRecovery);
            disinfectionTicks=Math.max(0,disinfectionTicks);
            disinfected=disinfectionTicks>0; infectionDelta=finiteClamp(infectionDelta,-1,1);
        }
    }

    private static float unit(float value) { return finiteClamp(value, 0, 1); }
    private static float finiteClamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite body state");
        return Math.clamp(value, minimum, maximum);
    }
}
