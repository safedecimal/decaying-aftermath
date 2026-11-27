package io.github.safedecimal.decayingaftermath.medical;

/** Deterministic vitals from the synchronized body's perfusion, reserves and drug burden. */
public final class MedicalPhysiology {
    private MedicalPhysiology() { }
    public static float immunity(BodySnapshot b) {
        var s=b.survival();float infection=s.gutInfection();
        for(var p:b.parts())infection=Math.max(infection,p.infection());
        float depressionPenalty=s.depression()==DepressionType.PERSISTENT?.08f:s.depression().severe()?.06f:0;
        return Math.clamp(1-infection*.45f-(1-s.nutrition())*.25f-s.fatigue()*.1f-s.toxin()*.1f-s.radiationGy()*.08f-depressionPenalty,0,1);
    }
    public static float mood(BodySnapshot b) {
        var s=b.survival();
        // Signed mood: +100 .. -100 in the monitor. Combines physical wellbeing with accumulated mental state.
        float wellbeing=1-b.pain()*.6f-(1-b.energy())*.15f-s.fatigue()*.15f
                -(1-s.nutrition())*.1f-(1-s.hydration())*.1f-s.nausea()*.25f;
        float physicalMood=Math.clamp(wellbeing*2-1,-1,1);
        float combined=Math.min(physicalMood, physicalMood*.45f + s.moodReserve()*.55f);
        if(s.depression().severe()) combined=Math.min(combined, -.35f);
        else if(s.depression().active()) combined=Math.min(combined, -.15f);
        return Math.clamp(combined,-1,1);
    }
    public static int respiratoryRate(BodySnapshot b) {
        if(b.mode()==BodySnapshot.Mode.DEAD)return 0;
        var s=b.survival();float opiate=b.drug()/(1+s.antagonist()*4);
        return Math.round(Math.clamp(16+(1-b.oxygen())*26+(1-b.energy())*10+b.pain()*5
                +s.thermalStress()*8-Math.max(0,opiate-.7f)*16,0,60));
    }
    public static float sepsis(float infectionSum,float maxInfection) {
        return Math.clamp(Math.max((infectionSum-1)/3,Math.max(0,(maxInfection-.75f)/.25f)*.6f),0,1);
    }
    // BodyModel debits this fraction once per server tick; convert to percentage points/second.
    public static float bloodLossPerTick(float effectiveBleedFlow) {
        return effectiveBleedFlow * .00065f;
    }
    public static float bloodLossPointsPerSecond(float effectiveBleedFlow) {
        return bloodLossPerTick(effectiveBleedFlow) * 20 * 100;
    }
    private static float perfusion(BodySnapshot b) {
        if(b.cardiacArrest())return 0;
        float filling=b.racingHeart()?Math.clamp(.72f-(b.cardiacTicks()-CardiacRhythm.WARNING)*.22f/160,.5f,.72f):1;
        return Math.clamp(b.blood()/.8f,0,1)*( .8f+.2f*b.oxygen())*filling;
    }
    public static int systolic(BodySnapshot b) {
        if(b.mode()==BodySnapshot.Mode.DEAD)return 0;
        return Math.round(Math.clamp((120+b.pain()*20+b.survival().stimulant()*12)*perfusion(b),0,220));
    }
    public static int diastolic(BodySnapshot b) {
        if(b.mode()==BodySnapshot.Mode.DEAD)return 0;
        return Math.round(Math.clamp((80+b.pain()*10+b.survival().stimulant()*6)*perfusion(b),0,140));
    }
}
