package io.github.safedecimal.decayingaftermath.medical;

import java.util.Objects;

/** Game-only quantities are normalized except BPM and the cumulative 0..3 drug burden. */
public record BodySnapshot(float condition, float blood, float oxygen, float pain, float drug,
        float bpm, Mode mode, float consciousnessProgress, float energy, float consciousness,
        PartSnapshot[] parts, SurvivalSnapshot survival, BloodGroup bloodGroup) {
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
                energy,consciousness,parts,survival,Objects.requireNonNull(group));
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
        drug = finiteClamp(drug, 0, 3); bpm = finiteClamp(bpm, 0, 220);
        consciousnessProgress = unit(consciousnessProgress);
        energy = unit(energy); consciousness = unit(consciousness);
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(survival, "survival");
        Objects.requireNonNull(parts, "parts");
        if (parts.length != BodyPart.values().length) throw new IllegalArgumentException("Six body parts required");
        parts = parts.clone();
        for (PartSnapshot part : parts) Objects.requireNonNull(part, "part");
    }

    @Override public PartSnapshot[] parts() { return parts.clone(); }
    public PartSnapshot part(BodyPart part) { return parts[Objects.requireNonNull(part).ordinal()]; }

    public record PartSnapshot(float integrity, float bleeding, float pain, boolean fractured,
            boolean bandaged, boolean splinted, boolean tourniquet, float infection,
            boolean disinfected, boolean anesthetized, float bandageCare, float burn,
            float internalBleeding, boolean openWound, boolean foreignBody, float boneRecovery,boolean coldInjury) {
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
            integrity = unit(integrity); bleeding = unit(bleeding); pain = unit(pain); infection = unit(infection);
            bandageCare = finiteClamp(bandageCare, 0, 10);
            burn=unit(burn); internalBleeding=unit(internalBleeding); boneRecovery=unit(boneRecovery);
        }
    }

    private static float unit(float value) { return finiteClamp(value, 0, 1); }
    private static float finiteClamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite body state");
        return Math.clamp(value, minimum, maximum);
    }
}
