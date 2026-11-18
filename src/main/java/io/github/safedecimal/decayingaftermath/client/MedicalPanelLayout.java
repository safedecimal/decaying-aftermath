package io.github.safedecimal.decayingaftermath.client;

/** The CU monitor uses Minecraft GUI units and shrinks to leave room for the inventory. */
public final class MedicalPanelLayout {
    public static final int SOURCE_WIDTH = 133;
    public static final int SOURCE_HEIGHT = 196;
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

    public static Box forScreen(int screenWidth, int screenHeight) {
        // Both panels use GUI units. On small windows they share the inventory's fit scale.
        float scale = Math.min(1f, Math.min(
                Math.max(1, screenWidth - 3 * GAP) / (float) (SOURCE_WIDTH + MedicalInventoryLayout.WIDTH),
                Math.max(1, screenHeight - 2 * GAP) / (float) MedicalInventoryLayout.HEIGHT));
        return new Box(GAP, GAP, Math.max(1, (int)Math.ceil(SOURCE_WIDTH * scale)),
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
