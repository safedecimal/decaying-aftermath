package io.github.safedecimal.decayingaftermath.client;

/** Thread, then tension each strap. Steady directional strokes, not a single 35-pixel jump. */
public final class FastenerMotion {
    private final double[] progress=new double[4],paid=new double[4];
    public record Pull(float fraction,boolean complete,boolean offRail) {}
    public Pull pull(int phase,double x,double y,double seconds) {
        int i=Math.clamp(phase-1,0,3),row=i<2?112:164;
        if(Math.abs(y-row)>16) return new Pull(0,false,true);
        boolean thread=(i%2)==0;
        double target=Math.clamp(thread?(x-96)/50:(148-x)/50,0,1);
        progress[i]=Math.min(target,progress[i]+seconds*.9);
        double delta=Math.max(0,progress[i]-paid[i]);paid[i]=Math.max(paid[i],progress[i]);
        return new Pull((float)(delta*.25),progress[i]>=.995,false);
    }
    public float progress(int phase) {return (float)progress[Math.clamp(phase-1,0,3)];}
}
