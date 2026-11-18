package io.github.safedecimal.decayingaftermath.client;

/** Native CU CanvasScaler: 1920x1080 reference, height match=1, authored sprite scale=3. */
public final class MedicalPanelLayout {
    public static final int SOURCE_WIDTH = 133;
    public static final int SOURCE_HEIGHT = 196;
    public static final int WIDTH = 399;
    public static final int HEIGHT = 588;
    public static final int GAP = 4;
    public static final int COMPACT_HEIGHT = 23;

    private MedicalPanelLayout() {
    }

    public static int inventoryLeft(int screenWidth, int inventoryWidth, int vanillaLeft, boolean recipesVisible) {
        return vanillaLeft;
    }

    public static Box locate(int screenWidth, int screenHeight, int left, int top,
            int inventoryWidth, int inventoryHeight, boolean recipesVisible) {
        return forScreen(screenWidth, screenHeight);
    }

    public static Box topLeft() {
        return forScreen(1920, 1080);
    }

    public static Box forScreen(int screenWidth, int screenHeight) {
        float canvasScale = Math.max(1,screenHeight) / 1080f;
        int x = Math.round(19 * canvasScale), y = Math.round(20 * canvasScale);
        // Native size is height-relative, independent of Minecraft's GUI scale setting.
        float scale = Math.min(3 * canvasScale, Math.max(1,screenWidth / 2 - GAP - x) / (float) SOURCE_WIDTH);
        return new Box(x, y, Math.max(1, (int)Math.ceil(SOURCE_WIDTH * scale)),
                Math.max(1, (int)Math.ceil(SOURCE_HEIGHT * scale)), false, scale);
    }

    public record Box(int x, int y, int width, int height, boolean compact, float scale) {
        public Box(int x,int y,int width,int height,boolean compact) {
            this(x,y,width,height,compact,Math.min(width/(float)SOURCE_WIDTH,height/(float)(compact?COMPACT_HEIGHT:SOURCE_HEIGHT)));
        }
        public float localX(double screenX) { return (float) ((screenX - x) / scale()); }
        public float localY(double screenY) { return (float) ((screenY - y) / scale()); }
    }
}
