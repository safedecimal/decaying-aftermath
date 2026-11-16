package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;

/** Fixed front-view Minecraft proportions: head 8x8, body 8x12, limbs 4x12 (3-wide slim arms). */
public final class MinecraftBodyLayout {
    public record Box(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    public record Face(int u, int v, int width, int height, int outerU, int outerV) {}
    private MinecraftBodyLayout() {}

    public static Box box(BodyPart part, boolean slim) {
        return switch (part) {
            case HEAD -> new Box(94, 20, 32, 32);
            case TORSO -> new Box(94, 52, 32, 48);
            case LEFT_ARM -> new Box(126, 52, slim ? 12 : 16, 48);
            case RIGHT_ARM -> new Box(slim ? 82 : 78, 52, slim ? 12 : 16, 48);
            case LEFT_LEG -> new Box(110, 100, 16, 48);
            case RIGHT_LEG -> new Box(94, 100, 16, 48);
        };
    }
    public static BodyPart partAt(double x, double y, boolean slim) {
        for (BodyPart part : BodyPart.values()) if (box(part, slim).contains(x, y)) return part;
        return null;
    }
    public static Face face(BodyPart part, boolean slim) {
        return switch (part) {
            case HEAD -> new Face(8, 8, 8, 8, 40, 8);
            case TORSO -> new Face(20, 20, 8, 12, 20, 36);
            case LEFT_ARM -> new Face(36, 52, slim ? 3 : 4, 12, 52, 52);
            case RIGHT_ARM -> new Face(44, 20, slim ? 3 : 4, 12, 44, 36);
            case LEFT_LEG -> new Face(20, 52, 4, 12, 4, 52);
            case RIGHT_LEG -> new Face(4, 20, 4, 12, 4, 36);
        };
    }
}
