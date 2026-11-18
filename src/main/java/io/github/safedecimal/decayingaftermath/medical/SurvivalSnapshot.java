package io.github.safedecimal.decayingaftermath.medical;

/** Fictional game reserves/concentrations. No real medication doses or clinical measurements. */
public record SurvivalSnapshot(float hydration, float nutrition, float temperature, float fatigue,
        float toxin, float plasma, float redCells, float antibiotic, float stimulant, float antagonist,
        float coagulant, float internalBleeding, float lungFunction, float encumbrance,float gutInfection) {
    public SurvivalSnapshot(float hydration,float nutrition,float temperature,float fatigue,float toxin,
            float plasma,float redCells,float antibiotic,float stimulant,float antagonist,float coagulant,
            float internalBleeding,float lungFunction,float encumbrance) {
        this(hydration,nutrition,temperature,fatigue,toxin,plasma,redCells,antibiotic,stimulant,antagonist,
                coagulant,internalBleeding,lungFunction,encumbrance,0);
    }
    public SurvivalSnapshot {
        hydration=range(hydration,0,1); nutrition=range(nutrition,0,1);
        temperature=range(temperature,25,45); fatigue=range(fatigue,0,1);
        toxin=range(toxin,0,3); plasma=range(plasma,0,1); redCells=range(redCells,0,1);
        antibiotic=range(antibiotic,0,3); stimulant=range(stimulant,0,3);
        antagonist=range(antagonist,0,3); coagulant=range(coagulant,0,3);
        internalBleeding=range(internalBleeding,0,6); lungFunction=range(lungFunction,0,1);
        encumbrance=range(encumbrance,0,10);
        gutInfection=range(gutInfection,0,1);
    }
    public static SurvivalSnapshot legacy(float blood) {
        return new SurvivalSnapshot(1,1,37,0,0,blood,blood,0,0,0,0,0,1,0);
    }
    public float bloodVolume() { return plasma*.55f+redCells*.45f; }
    /** Saline can improve perfusion, but cannot recreate lost erythrocytes. */
    public float oxygenDelivery() { return Math.min(1,bloodVolume()/.5f)*Math.min(1,redCells/.5f)*lungFunction; }
    public float thermalStress() { return Math.clamp((Math.abs(temperature-37)-1)/4,0,1); }
    private static float range(float v,float min,float max) {
        if (!Float.isFinite(v)) throw new IllegalArgumentException("Non-finite survival state");
        return Math.clamp(v,min,max);
    }
}
