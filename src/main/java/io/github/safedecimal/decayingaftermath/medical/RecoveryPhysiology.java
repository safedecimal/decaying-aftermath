package io.github.safedecimal.decayingaftermath.medical;

/** Accelerated game recovery: real plasma replacement takes hours and RBC replacement weeks.
 * Nutrition is a coarse substrate proxy, not a protein/iron/B12/folate simulation. */
public final class RecoveryPhysiology {
    public static final int PLASMA_REPLACEMENT_TICKS=20*60*15;
    public static final int RED_CELL_REPLACEMENT_TICKS=20*60*90;
    private RecoveryPhysiology() {}
    public record Production(float plasma,float cells) {}
    public static Production production(float plasma,float cells,float water,float food,float sepsis,float perfusion) {
        float circulation=Math.clamp(perfusion,0,1),inflammation=1-.8f*Math.clamp(sepsis,0,1);
        float fluidSupport=Math.clamp((water-.15f)/.35f,0,1),substrate=Math.clamp((food-.10f)/.40f,0,1);
        // Marrow production continues during bleeding; loss may still exceed production.
        float fluid=Math.min(1-plasma,fluidSupport*circulation/PLASMA_REPLACEMENT_TICKS);
        float epo=1+Math.clamp(1-cells,0,1);
        float red=Math.min(1-cells,substrate*inflammation*circulation*Math.clamp(water/.2f,0,1)*epo/RED_CELL_REPLACEMENT_TICKS);
        return new Production(Math.max(0,fluid),Math.max(0,red));
    }
    public static float tissueRepair(float tissue,boolean vital,boolean perfused,boolean woundProtected,
            float bleeding,float internalBleeding,float infection,boolean foreignBody,float nutrition) {
        if(!perfused||!woundProtected||foreignBody||bleeding>.000001f||internalBleeding>.000001f
                ||infection>=.25f||tissue<=0||vital&&tissue<=.3f)return 0;
        return Math.min(1-tissue,.000008f*Math.clamp(nutrition/.5f,0,1));
    }
}
