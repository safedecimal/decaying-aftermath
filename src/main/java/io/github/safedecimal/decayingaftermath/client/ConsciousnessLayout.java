package io.github.safedecimal.decayingaftermath.client;

/** level1/Consciousness + CanvasScaler: height-matched 1920x1080 canvas, no fixed 78px shrink. */
public record ConsciousnessLayout(int x, int y, int diameter, int ecgX, int ecgY,
        int ecgWidth, int ecgHeight, int textX, int textY) {
    public static final int REFERENCE_HEIGHT = 1080;
    public static final int SOURCE_DIAMETER = 447;
    public static final int ECG_WIDTH = 139;
    public static final int ECG_HEIGHT = 23;

    public static ConsciousnessLayout at(int width, int height) {
        int diameter = Math.max(1, Math.min(Math.max(1, width - 16),
                Math.round(Math.max(1, height) * SOURCE_DIAMETER / (float) REFERENCE_HEIGHT)));
        // Source RawImage is 417x69, exactly 3x its own 139x23 point-filtered texture.
        int ecgWidth = Math.max(1, Math.round(diameter * 417f / SOURCE_DIAMETER));
        int ecgHeight = Math.max(1, Math.round(diameter * 69f / SOURCE_DIAMETER));
        int cx = width / 2, cy = height / 2;
        return new ConsciousnessLayout(cx - diameter / 2, cy - diameter / 2, diameter,
                cx - ecgWidth / 2, cy - ecgHeight / 2, ecgWidth, ecgHeight,
                cx, cy + ecgHeight / 2 + 4);
    }
}
