package io.github.safedecimal.decayingaftermath.client;

/** Device-space composition with a screen-relative footprint, independent of GUI scale. */
public final class UniHealthHudLayout {
    public static final int WIDTH=304, HEIGHT=76, SLOT_SIZE=22, SLOT_STEP=28;
    public static final float MAX_WIDTH_SHARE=.60f, MAX_HEIGHT_SHARE=.18f;
    public record Box(float x,float y,float scale) { }
    private UniHealthHudLayout() { }
    public static Box at(int width,int height) {
        if(width<=0||height<=0)throw new IllegalArgumentException("GUI dimensions must be positive");
        float scale=Math.min(1,Math.min(width*MAX_WIDTH_SHARE/WIDTH,height*MAX_HEIGHT_SHARE/HEIGHT));
        float margin=Math.min(4,height*.02f);
        return new Box((width-WIDTH*scale)/2,height-HEIGHT*scale-margin,scale);
    }
    public static int slotX(int slot) {
        if(slot<0||slot>8)throw new IllegalArgumentException("Hotbar slot must be 0..8");
        return 26+slot*SLOT_STEP;
    }
}
