package io.github.safedecimal.decayingaftermath.client;

/** Screen-space anchor, completely independent of the inventory and recipe book. */
public final class MedicalPanelLayout {
    public static final int WIDTH = 133;
    public static final int HEIGHT = 196;
    public static final int GAP = 4;
    public static final int COMPACT_HEIGHT = 23;

    private MedicalPanelLayout() {
    }

    public static int inventoryLeft(int screenWidth, int inventoryWidth, int vanillaLeft, boolean recipesVisible) {
        return vanillaLeft;
    }

    public static Box locate(int screenWidth, int screenHeight, int left, int top,
            int inventoryWidth, int inventoryHeight, boolean recipesVisible) {
        return topLeft();
    }

    public static Box topLeft() {
        return new Box(0, 0, WIDTH, HEIGHT, false);
    }

    public record Box(int x, int y, int width, int height, boolean compact) {
    }
}
