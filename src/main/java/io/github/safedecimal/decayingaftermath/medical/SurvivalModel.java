package io.github.safedecimal.decayingaftermath.medical;

import java.util.Objects;

/** Deterministic 20 Hz adaptation of CU metabolism, fluid balance and liquid pharmacology. */
public final class SurvivalModel {
    private float hydration=1,nutrition=1,temperature=37,fatigue,toxin,plasma=1,redCells=1;
    private float antibiotic,stimulant,antagonist,coagulant,encumbrance,pendingFood,pendingWater;
    private float gutInfection,pendingGerms;
    private double bodyMassKg=SurvivalSnapshot.DEFAULT_BODY_MASS_KG, radiationGy, bloodLostLiters;
    private int incubationTicks;

    public SurvivalModel() { }
    public SurvivalModel(SurvivalSnapshot s) {
        Objects.requireNonNull(s);
        hydration=s.hydration(); nutrition=s.nutrition(); temperature=s.temperature(); fatigue=s.fatigue();
        toxin=s.toxin(); plasma=s.plasma(); redCells=s.redCells(); antibiotic=s.antibiotic();
        stimulant=s.stimulant(); antagonist=s.antagonist(); coagulant=s.coagulant(); encumbrance=s.encumbrance();
        gutInfection=s.gutInfection();
        bodyMassKg=s.bodyMassKg(); radiationGy=s.radiationGy(); bloodLostLiters=s.bloodLostLiters();
    }
    public SurvivalSnapshot snapshot(float internalBleeding,float lungFunction) {
        return new SurvivalSnapshot(hydration,nutrition,temperature,fatigue,toxin,plasma,redCells,
                antibiotic,stimulant,antagonist,coagulant,internalBleeding,lungFunction,encumbrance,gutInfection,(float)bodyMassKg,(float)radiationGy,(float)bloodLostLiters);
    }
    public float bloodVolume() { return plasma*.55f+redCells*.45f; }
    public float effectiveOpiate(float burden) { return burden/(1+antagonist*4); }
    public float clottingFactor() { return Math.clamp(1+coagulant*.65f-toxin*.3f, .25f,2); }
    public float antibioticAction() { return Math.clamp(antibiotic,0,1); }
    public float stimulantAction() { return Math.min(1,stimulant); }
    public float hydration() { return hydration; }
    public float nutrition() { return nutrition; }
    public float fatigue() { return fatigue; }
    public float temperature(){return temperature;}
    public float thermalStress() { return Math.clamp((Math.abs(temperature-37)-1)/4,0,1); }
    public float systemicStress() {
        return Math.max(Math.min(1,(float)radiationGy*.1f),Math.max(gutInfection*.6f,Math.max(thermalStress(),Math.max(Math.clamp((.2f-hydration)*5,0,1),Math.min(1,toxin*.45f)))));
    }
    public float oxygenDelivery(float lungFunction) {
        return Math.min(1,bloodVolume()/.5f)*Math.min(1,redCells/.5f)*lungFunction;
    }
    public void tick(BodyEnvironment e,float lostBlood,float sepsis) {
        float volume=bloodVolume();
        float oldHydration=hydration;
        if (volume>0 && lostBlood>0) {
            // Lose both components in the same proportion; never substitute plasma for red cells.
            float fraction=Math.min(1,lostBlood/volume);
            bloodLostLiters=Math.min(10000,bloodLostLiters+volume*fraction*bodyMassKg*.07);
            plasma=unit(plasma*(1-fraction)); redCells=unit(redCells*(1-fraction));
        }
        encumbrance=e.encumbrance();
        float food=Math.min(pendingFood,.0004f),water=Math.min(pendingWater,.001f);
        float surplusFood=Math.max(0,nutrition+food-1);
        nutrition=unit(nutrition+food); hydration=unit(hydration+water);
        bodyMassKg+=surplusFood*.5;
        pendingFood=Math.max(0,pendingFood-food); pendingWater=Math.max(0,pendingWater-water);
        if (e.metabolismEnabled()) {
            float exertion=e.moving() ? e.sprinting()?2.5f:1 : 0;
            hydration=unit(hydration-.000004f*(1+exertion+Math.max(0,temperature-37)*.8f+sepsis+gutInfection*2));
            nutrition=unit(nutrition-.000003f*(1+exertion*.6f+thermalStress()));
            fatigue=unit(fatigue+(e.sleeping()?-.0012f:e.moving()?.000020f:.000010f));
            if(nutrition<.2f)bodyMassKg-=(.2-nutrition)*.00005*(1+exertion*.6);
        }
        if(incubationTicks>0) incubationTicks--;
        else if(pendingGerms>0) {
            float germ=Math.min(pendingGerms,.0002f); gutInfection=unit(gutInfection+germ); pendingGerms-=germ;
        }
        gutInfection=unit(gutInfection-.000003f-.000035f*antibioticAction());
        // Dehydration reduces plasma, not red-cell mass (hemoconcentration).
        if(hydration<.15f) plasma=unit(plasma-(.15f-hydration)*.00006f);
        if (e.temperatureEnabled()) {
            // Heat transfer depends on the gradient, exposure and wetness, not an instant biome reset.
            float transfer=(e.ambientTemperature()-temperature)*.000020f*(1-e.insulation()*.8f)*(e.wet()?2:1);
            float thermoregulation=(37-temperature)*.00055f*Math.min(1,nutrition*2)*Math.min(1,hydration*2);
            float fever=sepsis*.00018f;
            temperature=Math.clamp(temperature+transfer+thermoregulation+fever+(e.sprinting()?.00004f:0),25,45);
        }
        // Plasma replenishes faster than erythrocytes, only when nutrition/water actually support it.
        if (lostBlood<=.000001f && hydration>.3f && nutrition>.25f && sepsis<.25f) {
            float newPlasma=Math.min(1-plasma,.000008f*hydration);
            plasma+=newPlasma; hydration=unit(hydration-newPlasma*.15f);
            redCells=unit(redCells+.0000015f*nutrition);
        }
        float excess=Math.max(0,stimulant-1)+Math.max(0,antibiotic-1.5f)+Math.max(0,coagulant-1);
        toxin=Math.clamp(toxin+excess*.00004f-.00001f*Math.min(1,hydration*2),0,3);
        antibiotic=Math.max(0,antibiotic-.00006f); stimulant=Math.max(0,stimulant-.00012f);
        antagonist=Math.max(0,antagonist-.00020f); coagulant=Math.max(0,coagulant-.00008f);
        bodyMassKg=Math.clamp(bodyMassKg+(hydration-oldHydration)*2,35,250);
    }
    public void feed(float food,float water) {
        if (!Float.isFinite(food)||!Float.isFinite(water)||food<0||water<0) return;
        pendingFood=Math.min(1,pendingFood+food); pendingWater=Math.min(1,pendingWater+water);
    }
    public void irradiate(float doseGy) {
        if(Float.isFinite(doseGy)&&doseGy>0)radiationGy=Math.min(100,radiationGy+doseGy);
    }
    public void poison(float amount) { if (Float.isFinite(amount)&&amount>0) toxin=Math.clamp(toxin+amount,0,3); }
    public void drinkContaminated(float pathogens,float chemicals) {
        if(Float.isFinite(pathogens)&&pathogens>0) {
            if(pendingGerms<=0)incubationTicks=1200; // Later sips cannot postpone the first exposure forever.
            pendingGerms=Math.min(1,pendingGerms+pathogens);
        }
        poison(chemicals);
    }
    public void caffeinate(float amount) { if(Float.isFinite(amount)&&amount>0)stimulant=Math.clamp(stimulant+amount*.25f,0,3); }
    public void starvation(float amount) { if (Float.isFinite(amount)&&amount>0) nutrition=unit(nutrition-amount*.02f); }
    public void wakeFromNight() { fatigue=Math.max(0,fatigue-.65f); }
    public void cool(float amount) { if(temperature>37.2f)temperature=Math.max(37.2f,temperature-.04f*Math.clamp(amount,0,1)); }
    /** Sole explicit exception: existing supernatural Last Standing reserves remain unchanged. */
    public void restoreLastStandingBlood(float blood) { plasma=redCells=unit(blood); }
    public boolean canTreat(TreatmentKind kind) { Objects.requireNonNull(kind);return true; }
    public void treat(TreatmentKind kind,float amount) {
        amount=unit(amount);
        switch(kind) {
            case ANTIBIOTICS -> antibiotic=Math.clamp(antibiotic+amount*.9f,0,3);
            case COMBAT_STIMULANT -> stimulant=Math.clamp(stimulant+amount*.9f,0,3);
            case NALOXONE -> antagonist=Math.clamp(antagonist+amount,0,3);
            case COAGULANT -> coagulant=Math.clamp(coagulant+amount*.8f,0,3);
            case BLOOD_BAG -> { plasma=unit(plasma+amount*.3f); redCells=unit(redCells+amount*.3f); }
            case SALINE -> plasma=unit(plasma+amount*.5f);
            default -> { }
        }
    }
    public record SavedState(SurvivalSnapshot snapshot,float pendingFood,float pendingWater,float pendingGerms,int incubationTicks) {
        public SavedState(SurvivalSnapshot snapshot,float pendingFood,float pendingWater){this(snapshot,pendingFood,pendingWater,0,0);}
        public SavedState {
            Objects.requireNonNull(snapshot);
            if (!Float.isFinite(pendingFood)||!Float.isFinite(pendingWater)) throw new IllegalArgumentException("Invalid absorption");
            pendingFood=unit(pendingFood); pendingWater=unit(pendingWater);
            if(!Float.isFinite(pendingGerms))throw new IllegalArgumentException("Invalid pathogen reserve");
            pendingGerms=unit(pendingGerms); incubationTicks=Math.clamp(incubationTicks,0,12000);
        }
    }
    public SavedState save(float internalBleeding,float lungFunction) {
        return new SavedState(snapshot(internalBleeding,lungFunction),pendingFood,pendingWater,pendingGerms,incubationTicks);
    }
    public static SurvivalModel restore(SavedState state) {
        SurvivalModel s=new SurvivalModel(state.snapshot()); s.pendingFood=state.pendingFood(); s.pendingWater=state.pendingWater();
        s.pendingGerms=state.pendingGerms();s.incubationTicks=state.incubationTicks();return s;
    }
    private static float unit(float value) { return Math.clamp(value,0,1); }
}
