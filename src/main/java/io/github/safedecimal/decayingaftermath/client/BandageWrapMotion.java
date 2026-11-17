package io.github.safedecimal.decayingaftermath.client;

/** CU's 20-degree OnUse increments; full coverage is not a stop condition or an item refill. */
public final class BandageWrapMotion {
    private double angle,previous,highWater;
    private int paidSteps;
    public void grab(double x,double y) { previous=polar(x,y); }
    public double advance(double x,double y) {
        double next=polar(x,y),delta=normalize(next-previous);previous=next;
        if(Math.abs(delta)>20) return 0;
        angle=Math.max(0,angle+delta);highWater=Math.max(highWater,angle);
        int steps=(int)Math.floor((highWater+.00001)/20),newSteps=steps-paidSteps;paidSteps=steps;
        return newSteps/18.0;
    }
    public double angle() {return angle;}
    public int turns() {return (int)(highWater/360);}
    public float coverage() {return highWater>=360?1:(float)(angle/360);}
    public float sizeMultiplier() {return Math.min(1.15f,.9f+Math.max(0,paidSteps-18)*.0015f);}
    public static double polar(double x,double y) {return (Math.toDegrees(Math.atan2(x-128,128-y))+360)%360;}
    public static double normalize(double angle) {return (angle+540)%360-180;}
}
