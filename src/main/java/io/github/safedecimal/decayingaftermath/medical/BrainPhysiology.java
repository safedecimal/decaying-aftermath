package io.github.safedecimal.decayingaftermath.medical;

/** Functional neural reserve, separate from consciousness and skin/head integrity.
 * Viable tissue can recover function after brief hypoxia; zero reserve never regrows here.
 * Rates are accelerated game balance, not clinical prognosis or neuron counts. */
public final class BrainPhysiology {
    private BrainPhysiology() {}
    public static float advance(float health,float delivery,float food,float water,float stress,boolean resting) {
        if(health<=0)return 0;
        if(delivery<.55f)return Math.max(0,health-.0004f*(1-Math.clamp(delivery/.55f,0,1)));
        if(delivery<=.7f||food<=.25f||water<=.2f||stress>=.5f)return health;
        float support=Math.min(Math.clamp(food/.5f,0,1),Math.clamp(water/.5f,0,1))*(1-stress);
        return Math.min(1,health+.000015f*support*(resting?1.5f:1));
    }
}
