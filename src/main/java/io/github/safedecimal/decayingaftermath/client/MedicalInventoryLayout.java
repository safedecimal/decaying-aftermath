package io.github.safedecimal.decayingaftermath.client;

/** Native inventory slots arranged around the T-pose, without covering its hit boxes. */
public final class MedicalInventoryLayout {
    public static final int WIDTH=260, HEIGHT=250;
    public static final int HOTBAR_X=19, HOTBAR_Y=223;
    public static final int RECIPE_X=229, RECIPE_Y=112;
    static final int BLEED_DETAIL_LINE_HEIGHT=9;
    // Unused space below the screen-left arm, above the leggings slot.
    static final MinecraftBodyLayout.Box BLEED_DETAILS=new MinecraftBodyLayout.Box(4,69,88,4*BLEED_DETAIL_LINE_HEIGHT);
    public record Position(int x,int y) {}
    private MedicalInventoryLayout() {}

    public static Position slot(int index) {
        if(index>=9&&index<36) return new Position(30+(index-9)%9*18,164+(index-9)/9*18);
        if(index>=36&&index<45) return new Position(22+(index-36)*20,226);
        return switch(index) {
            case 5 -> new Position(101,0);
            case 6 -> new Position(130,76);
            case 7 -> new Position(72,106);
            case 8 -> new Position(132,129);
            case 45 -> new Position(178,52);
            case 1 -> new Position(220,32);
            case 2 -> new Position(238,32);
            case 3 -> new Position(220,50);
            case 4 -> new Position(238,50);
            case 0 -> new Position(229,82);
            default -> throw new IllegalArgumentException("Not a player inventory slot: "+index);
        };
    }
}
