package io.github.safedecimal.decayingaftermath.client;

/** MinigameBase.UpdateHandPhysics: 60 Hz, velocity lerp, drag and authored 1/4 UI units. */
public final class CuHandMotion {
    public static final double STEP=1.0/60, UI_SCALE=.25;
    private double x,y,vx,vy,previousX,previousY;
    private long steps;
    public double x() { return 128+x*UI_SCALE; }
    public double y() { return 128+y*UI_SCALE; }
    public double velocityX() { return vx; }
    public void beginTick() {previousX=x;previousY=y;}
    public void step(double targetX,double targetY,boolean wrapping) {
        step(targetX,targetY,wrapping,0);
    }
    public void step(double targetX,double targetY,boolean wrapping,float pain) {
        // CU GetMousePos applies pain-scaled coherent noise. Minecraft has no CU RES/STR stat;
        // bounded deterministic harmonics adapt the existing pain field, not random cursor jumps.
        double t=steps++*STEP,a=Float.isFinite(pain)?Math.clamp(pain,0,1)*20:0;
        double jitterX=a*(Math.sin(t*2*Math.PI*2.9)+.5*Math.sin(t*2*Math.PI*1.37))/1.5;
        double jitterY=a*(Math.sin(t*2*Math.PI*2.2+.7)+.5*Math.sin(t*2*Math.PI*1.61))/1.5;
        double tx=Math.clamp((targetX-128)/UI_SCALE+jitterX,-524,524),ty=Math.clamp((targetY-128)/UI_SCALE+jitterY,-524,524);
        double dx=(tx-x)*.25,dy=(ty-y)*.25,length=Math.hypot(dx,dy);
        if(length>75) {dx*=75/length;dy*=75/length;}
        vx+=(dx-vx)*(4*STEP);vy+=(dy-vy)*(4*STEP);
        vx*=1-1.5*STEP;vy*=1-1.5*STEP;
        x+=vx*STEP*120;y+=vy*STEP*120;
        if(wrapping) {vx*=1-34*STEP;vy*=1-34*STEP;}
    }
    public void outsideRadius(double radius) {
        double length=Math.hypot(x,y);
        if(length<radius&&length>0) {x*=radius/length;y*=radius/length;}
    }
    public float rotation(boolean wrapping) { return (float)Math.toRadians(vx*.5+(wrapping?x*.02:0)); }
    public double visualX(float partialTick) { return 128+(previousX+(x-previousX)*Math.clamp(partialTick,0,1))*UI_SCALE; }
    public double visualY(float partialTick) { return 128+(previousY+(y-previousY)*Math.clamp(partialTick,0,1))*UI_SCALE; }
}
