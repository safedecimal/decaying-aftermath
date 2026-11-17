package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.MedicalSupplyPolicy;

/** Both labels fit inside one native 16-pixel slot; a nonempty item never rounds down to 0%. */
public final class SupplyOverlayLayout {
    private SupplyOverlayLayout() {}
    public static String percent(float fill) {
        float value=MedicalSupplyPolicy.fill(fill);
        return (value<=0?0:Math.max(1,(int)Math.ceil(value*100-.00001f)))+"%";
    }
    public static float scale(int percentWidth, int countWidth) {
        return Math.min(.45f, 15f / Math.max(1, percentWidth + countWidth + 2));
    }
    public static float countX(int countWidth, float scale) { return 16 - countWidth * scale; }
}
