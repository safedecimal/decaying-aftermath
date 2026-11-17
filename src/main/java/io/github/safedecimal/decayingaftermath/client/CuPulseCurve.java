package io.github.safedecimal.decayingaftermath.client;

/** Original Body.heartCurveNormal, resources.assets/11982 (same keys in all three Body prefabs).
 * Unweighted Unity AnimationCurve segments are cubic Hermite, not hand-drawn Gaussian spikes.
 * The mod has no fibrillationProgress field, so it does not invent arrhythmia from generic HP.
 */
public final class CuPulseCurve {
    private static final double[][] KEYS = {
        {-0.09999999403953552, 0.0, 0.0, 1.2353696823120117},
        {-0.005905520170927048, 0.116241455078125, -0.015826893970370293, -0.37817734479904175},
        {0.16402554512023926, -0.23161381483078003, -0.057394083589315414, 28.833396911621094},
        {0.28535962104797363, 0.9987809062004089, -0.36848679184913635, -0.14406661689281464},
        {0.3720250725746155, -0.6998956203460693, 0.31912845373153687, -0.2657984495162964},
        {0.44125795364379883, -0.29149407148361206, 7.3256731033325195, 2.5289340019226074},
        {0.5196843147277832, -0.2318335473537445, 0.715009868144989, 1.1036510467529297},
        {0.5627187490463257, 0.08824406564235687, 13.44367504119873, 7.385239124298096},
        {0.6198915839195251, 0.26990145444869995, 0.98519366979599, 0.35433366894721985},
        {0.7045726776123047, 0.14889034628868103, -2.369715690612793, -1.8848083019256592},
        {0.8602069020271301, -0.040676917880773544, -0.19007037580013275, -1.555308699607849e-05},
        {1.0, 0.0, 0.29097944498062134, 0.0}
    };
    private CuPulseCurve() {}
    public static double normal(double phase) {
        if (!Double.isFinite(phase)) return 0;
        if (phase <= KEYS[0][0]) return KEYS[0][1];
        for (int index = 1; index < KEYS.length; index++) {
            double[] right = KEYS[index], left = KEYS[index - 1];
            if (phase > right[0]) continue;
            double span = right[0] - left[0], t = (phase - left[0]) / span;
            double t2 = t * t, t3 = t2 * t;
            return (2 * t3 - 3 * t2 + 1) * left[1] + (t3 - 2 * t2 + t) * span * left[3]
                    + (-2 * t3 + 3 * t2) * right[1] + (t3 - t2) * span * right[2];
        }
        return KEYS[KEYS.length - 1][1];
    }
}
