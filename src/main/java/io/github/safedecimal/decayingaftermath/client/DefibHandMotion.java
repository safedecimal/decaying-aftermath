package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.DefibGame;

/** MinigameBase's native CU coordinates and 60 Hz inertia, isolated from other procedures. */
public final class DefibHandMotion {
    public static final double STEP=1.0/60;
    private double x,y,vx,vy,previousX,previousY;
    private long steps;
    public double x() {return x;}
    public double y() {return y;}
    public float rotation() {return (float)Math.toRadians(-vx*.5);}
    public void beginTick() {previousX=x;previousY=y;}
    public void step(double pointerX,double pointerY,float pain,float consciousness,boolean dial) {
        double c=Float.isFinite(consciousness)?Math.clamp(consciousness,0,1):0;
        double p=Float.isFinite(pain)?Math.clamp(pain,0,1)*100:0,t=steps++*STEP;
        // Coherent bounded tremor stands in for Unity's PerlinNoise; no invented STR/RES skills.
        double tx=Math.clamp(pointerX+p*(Math.sin(t*2*Math.PI*2.9)+.5*Math.sin(t*2*Math.PI*1.37))/3,-524,524);
        double ty=Math.clamp(pointerY+p*(Math.sin(t*2*Math.PI*2.2+.7)+.5*Math.sin(t*2*Math.PI*1.61))/3,-524,524);
        double dx=(tx-x)*.25*c,dy=(ty-y)*.25*c,length=Math.hypot(dx,dy);
        if(length>75) {dx*=75/length;dy*=75/length;}
        vx+=(dx-vx)*4*STEP*c;vy+=(dy-vy)*4*STEP*c;
        vx*=1-1.5*STEP;vy*=1-1.5*STEP;x+=vx*STEP*120;y+=vy*STEP*120;
        if(dial) {
            double xx=x-DefibGame.DIAL_X,yy=y-DefibGame.DIAL_Y,r=Math.hypot(xx,yy);
            if(r>75) {x=DefibGame.DIAL_X+xx*75/r;y=DefibGame.DIAL_Y+yy*75/r;}
        }
    }
    public double visualX(float partial,boolean dial) {return visual(partial,dial)[0];}
    public double visualY(float partial,boolean dial) {return visual(partial,dial)[1];}
    private double[] visual(float partial,boolean dial) {
        double a=Float.isFinite(partial)?Math.clamp(partial,0,1):0;
        double xx=previousX+(x-previousX)*a,yy=previousY+(y-previousY)*a;
        if(dial) {
            double dx=xx-DefibGame.DIAL_X,dy=yy-DefibGame.DIAL_Y,r=Math.hypot(dx,dy);
            if(r>75) {xx=DefibGame.DIAL_X+dx*75/r;yy=DefibGame.DIAL_Y+dy*75/r;}
        }
        return new double[]{xx,yy};
    }
}
