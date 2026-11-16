package io.github.safedecimal.decayingaftermath.client;

/** Keeps the monitor out of inventory slots and out of an open recipe book. */
public final class MedicalPanelLayout {
    public static final int WIDTH = 133;
    public static final int HEIGHT = 196;
    public static final int GAP = 4;
    public static final int COMPACT_HEIGHT = 23;

    private MedicalPanelLayout() {
    }

    public static int inventoryLeft(int screenWidth, int inventoryWidth, int vanillaLeft, boolean recipesVisible) {
        if (!recipesVisible && screenWidth >= WIDTH + GAP + inventoryWidth + 6) {
            // Center the medical monitor + inventory as a single group, monitor on the left.
            return (screenWidth + WIDTH + GAP - inventoryWidth) / 2;
        }
        return vanillaLeft;
    }

    public static Box locate(int screenWidth, int screenHeight, int left, int top,
            int inventoryWidth, int inventoryHeight, boolean recipesVisible) {
        if (!recipesVisible && left >= WIDTH + GAP + 3) {
            return new Box(left - WIDTH - GAP, Math.clamp(top, 4, Math.max(4, screenHeight - HEIGHT - 4)), WIDTH, HEIGHT, false);
        }
        int right = left + inventoryWidth + GAP;
        if (right + WIDTH <= screenWidth - 4) {
            return new Box(right, Math.clamp(top, 4, Math.max(4, screenHeight - HEIGHT - 4)), WIDTH, HEIGHT, false);
        }
        int width = Math.min(inventoryWidth, screenWidth - 8);
        int x = recipesVisible ? (screenWidth - width) / 2 : Math.clamp(left, 4, screenWidth - width - 4);
        int y = top >= COMPACT_HEIGHT + GAP ? top - COMPACT_HEIGHT - GAP
                : Math.min(screenHeight - COMPACT_HEIGHT - 4, top + inventoryHeight + 4);
        return new Box(x, Math.max(4, y), width, COMPACT_HEIGHT, true);
    }

    public record Box(int x, int y, int width, int height, boolean compact) {
    }
}
