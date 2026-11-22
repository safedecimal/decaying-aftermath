package io.github.safedecimal.decayingaftermath.medical;

/** Qualitative game model, not clinical calibration. Charge is CU UI energy, NOT joules.
 * Depolarization may terminate a shockable rhythm; an unsynchronized shock to an organized
 * rhythm may instead induce VF. Excess energy adds local electrical/thermal injury.
 * The legacy negative timer represents the game's shockable pulseless rhythm, not asystole/PEA.
 */
public final class DefibShockPolicy {
    public enum Result { REJECTED, BLOCKED, NO_CONVERSION, CONVERTED, ARRHYTHMIA, ARREST }
    public record Effect(Result result,float injury,float power) {}
    private DefibShockPolicy() {}

    public static Effect assess(int rhythm,boolean automatic,float charge,float roll,float vulnerability) {
        if(!Float.isFinite(roll)||roll<0||roll>=1||!Float.isFinite(vulnerability))
            return new Effect(Result.REJECTED,0,0);
        boolean shockable=CardiacRhythm.shockable(rhythm);
        // AED is an interlocked device, not an unrestricted manual shock button.
        if(automatic)return new Effect(shockable?Result.CONVERTED:Result.BLOCKED,0,0);
        if(!Float.isFinite(charge)||charge<10||charge>200)return new Effect(Result.REJECTED,0,0);
        float power=charge/200;
        if(!shockable) {
            float risk=Math.clamp(.08f+.52f*power+.20f*Math.clamp(vulnerability,0,1),0,.9f);
            return new Effect(roll<risk?Result.ARREST:Result.ARRHYTHMIA,.10f*power,power);
        }
        float required=DefibGame.fibrillation(rhythm)*2;
        float injury=Math.clamp((charge-required-40)/160,0,1)*.18f;
        float conversion=Math.clamp(1-Math.abs(required-charge)/80,0,1);
        return new Effect(roll<conversion?Result.CONVERTED:Result.NO_CONVERSION,injury,power);
    }
}
